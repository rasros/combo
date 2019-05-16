package combo.sat.solvers

import combo.math.RandomSequence
import combo.math.toIntArray
import combo.sat.*
import combo.sat.Constraint
import combo.sat.constraints.*
import combo.sat.constraints.Linear
import combo.util.mapArray
import combo.util.millis
import combo.util.nanos
import org.jacop.constraints.*
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.constraints.XeqP
import org.jacop.floats.core.FloatVar
import org.jacop.satwrapper.SatTranslation
import org.jacop.search.*
import java.util.*
import kotlin.math.ceil
import kotlin.random.Random

/**
 * [Solver] and [Optimizer] of [LinearObjective] using the JaCoP constraint satisfactory problem (CSP) library.
 */
class JacopSolver @JvmOverloads constructor(
        problem: Problem,
        constraintHandler: (Constraint, Store, Array<BooleanVar>) -> Unit = { _, _, _ ->
            throw UnsupportedOperationException("Register custom constraint handler in order to handle extra constraints.")
        }) : Solver, Optimizer<LinearObjective> {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
    override var timeout: Long = -1L

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [SparseBitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceFactory: InstanceBuilder = BitArrayBuilder

    private val lock = Object()

    private var randomSequence = RandomSequence(nanos())
    private val store = Store()
    private val vars = Array(problem.nbrVariables) { i ->
        BooleanVar(store, "x$i")
    }
    private val optimizeVars = Array(problem.nbrVariables) { i ->
        FloatVar(store, "xf$i", 0.0, 1.0)
    }

    init {
        for (i in vars.indices) store.imposeWithConsistency(XeqP(vars[i], optimizeVars[i]))
        val translation = SatTranslation(store)
        for (c in problem.constraints) {
            when (c) {
                is Disjunction -> {
                    val pos = c.literals.asSequence().filter { it.toBoolean() }
                            .map { vars[it.toIx()] }.toList().toTypedArray()
                    val neg = c.literals.asSequence().filter { !it.toBoolean() }
                            .map { vars[it.toIx()] }.toList().toTypedArray()
                    translation.generate_clause(pos, neg)
                }
                is Conjunction ->
                    for (l in c.literals) {
                        val value = if (l.toBoolean()) 1 else 0
                        store.imposeWithConsistency(XeqC(vars[l.toIx()], value))
                    }
                else -> {
                    val converted = convertConstraint(c)
                    if (converted == null) constraintHandler.invoke(c, store, vars)
                    else store.impose(converted)
                }
            }
        }
        translation.impose()
    }

    private fun convertConstraint(c: Constraint): PrimitiveConstraint? {
        return when (c) {
            is Disjunction -> {
                Or(c.literals.asSequence().map { XeqC(vars[it.toIx()], if (it.toBoolean()) 1 else 0) }.toList())
            }
            is Conjunction ->
                And(c.literals.asSequence().map { XeqC(vars[it.toIx()], if (it.toBoolean()) 1 else 0) }.toList())
            is Cardinality -> {
                val pos = c.literals.asSequence().filter { it.toBoolean() }.map { vars[it.toIx()] }.toList().toTypedArray()
                val neg = c.literals.asSequence().filter { !it.toBoolean() }.map { vars[it.toIx()] }.toList().toTypedArray()
                val degree = IntVar(store, c.degree, c.degree)
                if (neg.isEmpty()) {
                    SumBool(pos, c.relation.operator, degree)
                } else {
                    val posSum = IntVar(store, 0, pos.size)
                    val negSum = IntVar(store, 0, neg.size)
                    store.impose(SumBool(pos, "=", posSum))
                    store.impose(SumBool(neg, "=", negSum))
                    LinearInt(arrayOf(posSum, negSum), intArrayOf(1, -1), c.relation.operator, c.degree - neg.size)
                }
            }
            is Linear -> {
                val pos = c.literals.asSequence().filter { it.toBoolean() }.map { vars[it.toIx()] }.toList().toTypedArray()
                val neg = c.literals.asSequence().filter { !it.toBoolean() }.map { vars[it.toIx()] }.toList().toTypedArray()
                val degree = IntVar(store, c.degree, c.degree)
                if (neg.isEmpty()) {
                    var k = 0
                    val weights = IntArray(pos.size)
                    for (lit in c.literals.iterator())
                        weights[k++] = c.weights[lit.toIx()]
                    LinearInt(pos, weights, c.relation.operator, degree)
                } else {
                    val posSum = IntVar(store, Int.MIN_VALUE, Int.MAX_VALUE)
                    val negSum = IntVar(store, Int.MIN_VALUE, Int.MAX_VALUE)
                    val posWeights = IntArray(pos.size)
                    val negWeights = IntArray(neg.size)
                    var kPos = 0
                    var kNeg = 0
                    for (lit in c.literals.iterator()) {
                        if (lit.toBoolean()) posWeights[kPos++] = c.weights[lit.toIx()]
                        else negWeights[kNeg++] = c.weights[lit.toIx()]
                    }
                    store.impose(LinearInt(pos, posWeights, "=", posSum))
                    store.impose(LinearInt(neg, negWeights, "=", negSum))
                    LinearInt(arrayOf(posSum, negSum), intArrayOf(1, -1), c.relation.operator, c.degree - negWeights.sum())
                }
            }
            is ReifiedImplies -> {
                IfThen(XeqC(vars[c.literal.toIx()], if (c.literal.toBoolean()) 1 else 0),
                        convertConstraint(c.constraint) ?: throw UnsupportedOperationException(
                                "Only built in constraints can be used with ReifiedImplies."))
            }
            is ReifiedEquivalent -> {
                val wrapped = (convertConstraint(c.constraint) ?: throw UnsupportedOperationException(
                        "Only built in constraints can be used with ReifiedEquivalent."))
                if (c.literal.toBoolean()) Reified(wrapped, vars[c.literal.toIx()])
                else Eq(XeqC(vars[c.literal.toIx()], 0), wrapped)
            }
            is NumericConstraint -> {
                throw UnsupportedOperationException(
                        "Numeric constraints not supported.")
            }
            else -> null
        }
    }

    override fun witnessOrThrow(assumptions: Literals): Instance {
        synchronized(lock) {
            try {
                store.setLevel(store.level + 1)
                if (vars.isEmpty()) return instanceFactory.create(0)
                if (assumptions.isNotEmpty()) {
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))
                }
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }
                val rng = randomSequence.next()
                val result = search.labeling(store, SimpleSelect(vars, MostConstrainedDynamic(), BinaryIndomainRandom(rng)))
                if (!result) {
                    if (search.timeOutOccured) throw TimeoutException(timeout)
                    else throw UnsatisfiableException()
                }
                return toInstance(instanceFactory, rng)
            } finally {
                store.removeLevel(store.level)
                store.setLevel(store.level - 1)
            }
        }
    }

    /**
     * This method is not lazy due to limitations in jacop. Use another solver or [forEachInstance] instead.
     * If timeout is not set it will only terminate once all solutions are exhausted, which is probably never.
     */
    override fun asSequence(assumptions: Literals): kotlin.sequences.Sequence<Instance> {
        val list = ArrayList<Instance>()
        forEachInstance(Integer.MAX_VALUE, assumptions) { list.add(it) }
        return list.asSequence()
    }

    /**
     * This method is the preferred way to iterate through solutions using Jacop.
     */
    fun forEachInstance(limit: Int, assumptions: Literals, instanceConsumer: (Instance) -> Unit) {
        synchronized(lock) {
            try {
                store.setLevel(store.level + 1)
                if (assumptions.isNotEmpty())
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))

                val rng = randomSequence.next()
                val select = SimpleSelect(vars, MostConstrainedStatic(), BinaryIndomainRandom(rng))
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }
                search.setSolutionListener(object : SimpleSolutionListener<BooleanVar>() {
                    override fun recordSolution() {
                        super.recordSolution()
                        instanceConsumer.invoke(toInstance(instanceFactory, rng))
                    }
                })
                search.getSolutionListener().setSolutionLimit(limit)
                search.getSolutionListener().searchAll(true)
                search.labeling(store, select)
            } finally {
                store.removeLevel(store.level)
                store.setLevel(store.level - 1)
            }
        }
    }

    override fun optimizeOrThrow(function: LinearObjective, assumptions: Literals): Instance {
        synchronized(lock) {
            try {
                store.setLevel(store.level + 1)
                if (optimizeVars.isEmpty()) return instanceFactory.create(0)
                if (assumptions.isNotEmpty())
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))

                val cost = IntVar(store, Int.MIN_VALUE, Int.MAX_VALUE)
                val lin = LinearInt(vars, function.weights.toIntArray(0.1f).mapArray {
                    if (function.maximize) -it else it
                }, "=", cost)
                store.impose(lin)
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }

                val result = search.labeling(store, SimpleSelect<BooleanVar>(
                        vars, MostConstrainedDynamic<BooleanVar>(),
                        WeightIndomain(function, vars)), cost)

                if (!result) {
                    if (search.timeOutOccured) throw TimeoutException(timeout)
                    else throw UnsatisfiableException()
                }

                return toInstance(instanceFactory, randomSequence.next())
            } finally {
                store.removeLevel(store.level)
                store.setLevel(store.level - 1)
            }
        }
    }

    private fun toInstance(factory: InstanceBuilder, rng: Random): Instance {
        val instance = factory.create(vars.size)
        vars.forEachIndexed { i, v ->
            if ((v.dom().singleton() && v.value() == 1) || (!v.dom().singleton() && rng.nextBoolean()))
                instance[i] = true
        }
        return instance
    }

    private fun setTimeout(search: DepthFirstSearch<*>) {
        if (timeout >= 0L) {
            search.setTimeOut(ceil(timeout / 1000.0).toLong())
            try {
                val cls = Class.forName("org.jacop.search.DepthFirstSearch")
                val fld = cls.getDeclaredField("timeOut")
                fld.isAccessible = true
                fld.set(search, millis() + timeout)
            } catch (e: Exception) {
            }
        }
    }

    private class BinaryIndomainRandom(private val rng: Random) : Indomain<BooleanVar> {
        override fun indomain(v: BooleanVar) = rng.nextInt(2)
    }

    private class WeightIndomain(val function: LinearObjective, vars: Array<BooleanVar>) : Indomain<BooleanVar> {
        private val cache = HashMap<BooleanVar, Int>()

        init {
            vars.forEachIndexed { i, bv -> cache[bv] = i }
        }

        override fun indomain(bVar: BooleanVar): Int {
            return if (function.maximize) {
                if (function.weights[cache[bVar]!!] >= 0) 1 else 0
            } else {
                if (function.weights[cache[bVar]!!] < 0) 1 else 0
            }
        }
    }
}
