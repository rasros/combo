package combo.sat.optimizers

import combo.math.toIntArray
import combo.sat.*
import combo.sat.Constraint
import combo.sat.constraints.*
import combo.sat.constraints.Linear
import combo.util.*
import org.jacop.constraints.*
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.core.FloatVar
import org.jacop.satwrapper.SatTranslation
import org.jacop.search.*
import kotlin.math.ceil
import kotlin.random.Random

/**
 * [Optimizer] of [LinearObjective] using the JaCoP constraint satisfactory problem (CSP) library.
 * Using this requires an extra optional dependency. It can be used in parallel from multiple threads, the solver is
 * protected internally with thread local.
 *
 * @param problem the problem contains the [Constraint]s and the number of variables.
 * @param randomSeed Set the random seed to a specific value to have a reproducible algorithm.
 * @param timeout The solver will abort after timeout in milliseconds have been reached, without a real-time guarantee.
 * @param instanceFactory Determines the [Instance] that will be created for solving.
 * @param delta Precision with which to convert objective function into integer constraints.
 * @param gcdSimplify Simplify weights with greatest common divisor before optimizing.
 */
class JacopSolver @JvmOverloads constructor(
        val problem: Problem,
        override val randomSeed: Int = nanos().toInt(),
        override val timeout: Long = -1L,
        val instanceFactory: InstanceFactory = BitArrayFactory,
        val delta: Float = 0.01f,
        val gcdSimplify: Boolean = true,
        val constraintHandler: (Constraint, Store, Array<BooleanVar>) -> Unit = { _, _, _ ->
            throw UnsupportedOperationException("Register custom constraint handler in order to handle extra constraints.")
        }) : Optimizer<LinearObjective> {

    private val randomSequence = RandomSequence(randomSeed)

    private inner class ConstraintEncoder {
        val store = Store()

        val vars = Array(problem.nbrValues) { i ->
            BooleanVar(store, "x$i")
        }

        val varIndex = HashMap<BooleanVar, Int>().apply {
            for (i in vars.indices)
                this[vars[i]] = i
        }

        init {
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
                    when {
                        neg.isEmpty() -> SumBool(pos, c.relation.operator, IntVar(store, c.degree, c.degree))
                        pos.isEmpty() -> SumBool(neg, c.relation.not().operator, IntVar(store, -c.degree, -c.degree))
                        else -> {
                            val posSum = IntVar(store, 0, pos.size)
                            val negSum = IntVar(store, 0, neg.size)
                            store.impose(SumBool(pos, "=", posSum))
                            store.impose(SumBool(neg, "=", negSum))
                            LinearInt(arrayOf(posSum, negSum), intArrayOf(1, -1), c.relation.operator, c.degree - neg.size)
                        }
                    }
                }
                is CardinalityVar -> {
                    TODO("need IntBounds to be calculated in initial of two passes over problem.constraints")
                }
                is Linear -> {
                    val pos = c.literals.asSequence().filter { it.toBoolean() }.map { vars[it.toIx()] }.toList().toTypedArray()
                    val neg = c.literals.asSequence().filter { !it.toBoolean() }.map { vars[it.toIx()] }.toList().toTypedArray()
                    when {
                        neg.isEmpty() -> LinearInt(pos, c.literals.asSequence().map {
                            c.weights[c.literals[it]]
                        }.toList().toIntArray(), c.relation.operator, IntVar(store, c.degree, c.degree))
                        pos.isEmpty() -> LinearInt(neg, c.literals.asSequence().map {
                            c.weights[c.literals[it]]
                        }.toList().toIntArray(), c.relation.not().operator, IntVar(store, -c.degree, -c.degree))
                        else -> {
                            val posSum = IntVar(store, Int.MIN_VALUE, Int.MAX_VALUE)
                            val negSum = IntVar(store, Int.MIN_VALUE, Int.MAX_VALUE)
                            val posWeights = IntArray(pos.size)
                            val negWeights = IntArray(neg.size)
                            var kPos = 0
                            var kNeg = 0
                            for (lit in c.literals.iterator()) {
                                if (lit.toBoolean()) posWeights[kPos++] = c.weights[c.literals[lit]]
                                else negWeights[kNeg++] = c.weights[c.literals[lit]]
                            }
                            store.impose(LinearInt(pos, posWeights, "=", posSum))
                            store.impose(LinearInt(neg, negWeights, "=", negSum))
                            LinearInt(arrayOf(posSum, negSum), intArrayOf(1, -1), c.relation.operator, c.degree - negWeights.sum())
                        }
                    }
                }
                is ReifiedImplies -> {
                    val wrapped = convertConstraint(c.constraint) ?: throw UnsupportedOperationException(
                            "Only built in constraints can be used with ReifiedImplies.")
                    IfThen(XeqC(vars[c.literal.toIx()], if (c.literal.toBoolean()) 1 else 0), wrapped)
                }
                is ReifiedEquivalent -> {
                    val wrapped = (convertConstraint(c.constraint) ?: throw UnsupportedOperationException(
                            "Only built in constraints can be used with ReifiedEquivalent."))
                    if (c.literal.toBoolean()) Reified(wrapped, vars[c.literal.toIx()])
                    else Eq(XeqC(vars[c.literal.toIx()], 0), wrapped)
                }
                is IntBounds -> {
                    val xs = vars.copyOfRange(c.literals.min.toIx(), c.literals.max.toIx() + 1)
                    val y = IntVar(store, c.min, c.max)
                    BinaryXeqY(xs, y)
                }
                is FloatBounds -> {
                    val xs = vars.copyOfRange(c.literals.min.toIx(), c.literals.max.toIx() + 1)
                    val y = FloatVar(store, c.min.toDouble(), c.max.toDouble())
                    BinaryXeqP(xs, y)
                }
                else -> null
            }
        }
    }

    override val complete get() = true

    private val encoderTL = ThreadLocal.withInitial { ConstraintEncoder() }

    override fun witnessOrThrow(assumptions: IntCollection, guess: Instance?): Instance {
        with(encoderTL.get()) {
            try {
                store.setLevel(store.level + 1)
                if (vars.isEmpty()) return instanceFactory.create(0)
                val rng = randomSequence.next()
                if (assumptions.isNotEmpty()) {
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))
                }
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }
                val indomain = if (guess == null) BinaryIndomainRandom(rng) else InitialGuessIndomain(this, guess)
                val result = search.labeling(store, SimpleSelect(vars, MostConstrainedDynamic(), indomain))
                if (!result) {
                    if (search.timeOutOccured) throw TimeoutException(timeout)
                    else throw UnsatisfiableException()
                }
                return toInstance(this, rng)
            } finally {
                store.removeLevel(store.level)
                store.setLevel(store.level - 1)
            }
        }
    }

    override fun asSequence(assumptions: IntCollection): kotlin.sequences.Sequence<Instance> {
        with(ConstraintEncoder()) {
            val rng = randomSequence.next()

            val select = SimpleSelect(vars, MostConstrainedStatic(), BinaryIndomainRandom(rng))
            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
                setTimeout(this)
            }

            return generateSequence {
                val instance: Instance?
                try {
                    store.setLevel(store.level + 1)
                    for (l in assumptions)
                        store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))
                    val result = search.labeling(store, select)
                    instance = if (!result) null
                    else toInstance(this@with, rng)
                } finally {
                    store.removeLevel(store.level)
                    store.setLevel(store.level - 1)
                }

                if (instance != null) {
                    store.impose(Or(Array(instance.size) {
                        XeqC(vars[it], if (instance.isSet(it)) 0 else 1)
                    }))
                }
                instance
            }
        }
    }

    override fun optimizeOrThrow(function: LinearObjective, assumptions: IntCollection, guess: Instance?): Instance {
        with(encoderTL.get()) {
            try {
                store.setLevel(store.level + 1)
                if (vars.isEmpty()) return instanceFactory.create(0)
                if (assumptions.isNotEmpty())
                    for (l in assumptions) store.impose(XeqC(vars[l.toIx()], if (l.toBoolean()) 1 else 0))

                val cost = IntVar(store, Int.MIN_VALUE, Int.MAX_VALUE)
                val lin = LinearInt(vars, function.weights.toIntArray(delta, gcdSimplify).apply {
                    if (function.maximize) transformArray { -it }
                }, "=", cost)
                store.impose(lin)
                val search = DepthFirstSearch<BooleanVar>().apply {
                    setPrintInfo(false)
                    setTimeout(this)
                }

                val indomain = if (guess == null) WeightIndomain(function, vars)
                else InitialGuessIndomain(this, guess)
                val result = search.labeling(store, SimpleSelect<BooleanVar>(
                        vars, MostConstrainedDynamic<BooleanVar>(), indomain), cost)

                if (!result) {
                    if (search.timeOutOccured) throw TimeoutException(timeout)
                    else throw UnsatisfiableException()
                }

                return toInstance(this, randomSequence.next())
            } finally {
                store.removeLevel(store.level)
                store.setLevel(store.level - 1)
            }
        }
    }

    private fun toInstance(encoder: ConstraintEncoder, rng: Random): Instance {
        val instance = instanceFactory.create(encoder.vars.size)
        encoder.vars.forEachIndexed { i, v ->
            if ((v.singleton() && v.value() == 1) || (!v.singleton() && rng.nextBoolean()))
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

    private class BinaryIndomainRandom(val rng: Random) : Indomain<BooleanVar> {
        override fun indomain(v: BooleanVar) = rng.nextInt(2)
    }

    private class InitialGuessIndomain(val encoder: ConstraintEncoder, val guess: Instance) : Indomain<BooleanVar> {
        override fun indomain(v: BooleanVar) = if (guess.isSet(encoder.varIndex[v]!!)) 1 else 0
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
