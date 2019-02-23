package combo.sat.solvers

import combo.math.RandomSequence
import combo.sat.*
import combo.util.millis
import combo.util.nanos
import org.jacop.constraints.SumBool
import org.jacop.constraints.XeqC
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.constraints.LinearFloat
import org.jacop.floats.constraints.PmulCeqR
import org.jacop.floats.constraints.XeqP
import org.jacop.floats.core.FloatVar
import org.jacop.satwrapper.SatTranslation
import org.jacop.search.*
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

/**
 * [Solver] and [Optimizer] of [LinearObjective] using the JaCoP constraint satisfactory problem (CSP) library.
 */
class JacopSolver @JvmOverloads constructor(
        problem: Problem,
        constraintHandler: (Constraint, Store, Array<BooleanVar>) -> Nothing = { _, _, _ ->
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
     * [IntSetInstanceFactory] otherwise [BitFieldInstanceFactory].
     */
    var instanceFactory: InstanceFactory = BitFieldInstanceFactory

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
        val translation = SatTranslation(store)
        for (i in vars.indices) store.impose(XeqP(vars[i], optimizeVars[i]))
        for (c in problem.constraints) {
            when (c) {
                is Disjunction -> {
                    val pos = c.literals.asSequence().filter { it.toBoolean() }
                            .map { vars[it.toIx()] }.toList().toTypedArray()
                    val neg = c.literals.asSequence().filter { !it.toBoolean() }
                            .map { vars[it.toIx()] }.toList().toTypedArray()
                    translation.generate_clause(pos, neg)
                }
                is Conjunction -> for (l in c.literals) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))
                is Cardinality -> {
                    val array = c.literals.toArray()
                    val cardVars = Array(c.literals.size) { i ->
                        vars[array[i].toIx()]
                    }
                    val degree = IntVar(store, c.degree, c.degree)
                    store.impose(SumBool(cardVars, c.relation.operator, degree))
                }
                is Reified -> {
                    val literal = if (!c.literal.toBoolean()) {
                        val negated = BooleanVar(store, "-x${c.literal.toIx()}")
                        translation.generate_not(vars[c.literal.toIx()], negated)
                        negated
                    } else vars[c.literal.toIx()]
                    val reified = c.clause
                    if (reified is Disjunction) {
                        val pos = reified.literals.asSequence().filter { it.toBoolean() }
                                .map { vars[it.toIx()] }.toList().toTypedArray()
                        val neg = reified.literals.asSequence().filter { !it.toBoolean() }
                                .map { vars[it.toIx()] }.toList().toTypedArray()
                        translation.generate_clause_reif(pos, neg, literal)
                    } else {
                        for (d in c.toCnf()) {
                            val pos = d.literals.asSequence().filter { it.toBoolean() }
                                    .map { vars[it.toIx()] }.toList().toTypedArray()
                            val neg = d.literals.asSequence().filter { !it.toBoolean() }
                                    .map { vars[it.toIx()] }.toList().toTypedArray()
                            translation.generate_clause(pos, neg)
                        }
                    }
                }
                !is Tautology -> constraintHandler.invoke(c, store, vars)
            }
        }
        translation.impose()
    }


    override fun witnessOrThrow(assumptions: Literals): Instance {
        synchronized(lock) {
            try {
                store.setLevel(store.level + 1)
                if (optimizeVars.isEmpty()) return instanceFactory.create(0)
                if (assumptions.isNotEmpty()) {
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))
                }
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }
                val result = search.labeling(store, SimpleSelect(vars, MostConstrainedDynamic(), BinaryIndomainRandom(randomSequence.next())))
                if (!result) {
                    if (search.timeOutOccured) throw TimeoutException(timeout)
                    else throw UnsatisfiableException()
                }
                return toInstance(instanceFactory)
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
    override fun sequence(assumptions: Literals): Sequence<Instance> {
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

                val select = SimpleSelect(vars, MostConstrainedStatic(), BinaryIndomainRandom(randomSequence.next()))
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }
                search.setSolutionListener(object : SimpleSolutionListener<BooleanVar>() {
                    override fun recordSolution() {
                        super.recordSolution()
                        instanceConsumer.invoke(toInstance(instanceFactory))
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
                if (assumptions.isNotEmpty()) {
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))
                }
                val max = function.weights.asSequence().map { abs(it) }.sum()
                val cost = FloatVar(store, -max, max)
                store.impose(LinearFloat(optimizeVars, function.weights, "=", cost))
                val costVar =
                        if (function.maximize) {
                            val negCost = FloatVar(store, -max, max)
                            store.impose(PmulCeqR(cost, -1.0, negCost))
                            negCost
                        } else cost
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }
                val result = search.labeling(store, SimpleSelect<BooleanVar>(
                        vars, MostConstrainedDynamic<BooleanVar>(), IndomainMax<BooleanVar>()), costVar)
                if (!result) {
                    if (search.timeOutOccured) throw TimeoutException(timeout)
                    else throw UnsatisfiableException()
                }
                return toInstance(instanceFactory)
            } finally {
                store.removeLevel(store.level)
                store.setLevel(store.level - 1)
            }
        }
    }

    private fun toInstance(factory: InstanceFactory): Instance {
        val nbrPos = vars.count { it.value() == 1 }
        val lits = IntArray(nbrPos)
        var k = 0
        vars.forEachIndexed { i, v -> if (v.value() == 1) lits[k++] = i.toLiteral(true) }
        return factory.create(vars.size).apply { setAll(lits) }
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
}
