package combo.sat.solvers

import combo.math.RandomSequence
import combo.math.toIntArray
import combo.sat.*
import combo.sat.constraints.*
import combo.sat.constraints.Relation.*
import combo.util.millis
import combo.util.nanos
import combo.util.transformArray
import org.sat4j.core.LiteralsUtils.negLit
import org.sat4j.core.LiteralsUtils.posLit
import org.sat4j.core.Vec
import org.sat4j.core.VecInt
import org.sat4j.minisat.SolverFactory
import org.sat4j.minisat.core.IOrder
import org.sat4j.minisat.core.IPhaseSelectionStrategy
import org.sat4j.pb.ObjectiveFunction
import org.sat4j.pb.core.PBSolver
import org.sat4j.specs.ContradictionException
import org.sat4j.tools.ModelIterator
import java.math.BigInteger
import java.math.BigInteger.valueOf
import kotlin.random.Random
import org.sat4j.minisat.core.Solver as Sat4J
import org.sat4j.pb.SolverFactory as PBSolverFactory
import org.sat4j.specs.TimeoutException as Sat4JTimeoutException

/**
 * [Solver] and [Optimizer] of [LinearObjective] using the Sat4J SAT library. Using this requires an extra optional
 * dependency, like so in gradle: compile "org.ow2.sat4j:org.ow2.sat4j.maxsat:2.3.5"
 */
class Sat4JSolver @JvmOverloads constructor(
        val problem: Problem,
        val constraintHandler: (Constraint, Sat4J<*>) -> Nothing = { _, _ ->
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
     * [IntSetInstanceFactory] otherwise [BitArrayBuilder].
     */
    var instanceFactory: InstanceBuilder = BitArrayBuilder

    /**
     * Solver aborts after this number of conflicts are reached.
     */
    var maxConflicts: Int = 0
        set(value) {
            solver.setTimeoutOnConflicts(value)
            field = value
        }

    /**
     * Solver forgets all learned clauses after each [witness]. Setting to false might improve solving speed but
     * introduces bias in the generated instances.
     */
    var forgetLearnedClauses: Boolean = true

    /**
     * Precision with which to convert floating point constraint and objective function into integer constraints.
     * See [toIntArray]
     */
    var delta: Float = 0.1f

    private val solverLock = Object()
    private var randomSequence = RandomSequence(nanos())
    private val literalSelection = RandomLiteralSelectionStrategySeeded(randomSequence.next())
    private val timeoutOrder: TimeoutOrder
    private val solver: Sat4J<*> = createSolver(false).also {
        timeoutOrder = TimeoutOrder(it.order)
        it.order = timeoutOrder
        it.order.phaseSelectionStrategy = literalSelection
    }

    private fun createSolver(optimizer: Boolean): Sat4J<*> {
        val solver = if (optimizer || problem.constraints.any { it is Linear }) PBSolverFactory.newLight() as PBSolver
        else SolverFactory.newMiniLearningHeap()

        solver.newVar(problem.nbrVariables)

        try {
            for (c in problem.constraints)
                when (c) {
                    is Disjunction -> solver.addClause(c.literals.toArray().apply { sort() }.toSat4JVec())
                    is Conjunction -> c.literals.forEach {
                        solver.addClause(intArrayOf(it).toSat4JVec())
                    }
                    is Cardinality -> {
                        val cardLits = c.literals.toArray().apply { sort() }.toSat4JVec()
                        when (c.relation) {
                            GE -> solver.addAtLeast(cardLits, c.degree)
                            LE -> solver.addAtMost(cardLits, c.degree)
                            EQ -> solver.addExactly(cardLits, c.degree)
                            GT -> solver.addAtLeast(cardLits, c.degree + 1)
                            LT -> solver.addAtMost(cardLits, c.degree - 1)
                            NE ->
                                throw UnsupportedOperationException("Relation != cannot be expressed as a linear inequality.")
                        }
                    }
                    /*
                is Linear -> {
                            val pbSolver = this as PBSolver
                            val cardLits = c.literals.toArray().apply { sort() }.toSat4JVec()
                            val weights = c.weights.toIntArray()
                            when (c.relation) {
                                GE -> addAtLeast(cardLits, c.degree)
                                LE -> addAtMost(cardLits, c.degree)
                                EQ -> addExactly(cardLits, c.degree)
                                GT -> addAtLeast(cardLits, c.degree + 1)
                                LT -> addAtMost(cardLits, c.degree - 1)
                                NE ->
                                    throw UnsupportedOperationException("Relation != cannot be expressed as a linear inequality.")
                            }
                            pbSolver.addPseudoBoolean(cardLits, )
                    TODO("")
                }
                            */
                    is ReifiedEquivalent -> c.toCnf().forEach { solver.addClause(it.literals.toArray().apply { sort() }.toSat4JVec()) }
                    is ReifiedImplies -> c.toCnf().forEach { solver.addClause(it.literals.toArray().apply { sort() }.toSat4JVec()) }
                    is Tautology -> Unit
                    is Empty -> throw UnsatisfiableException("Empty constraint in problem.")
                    else -> constraintHandler.invoke(c, solver)
                }
        } catch (e: ContradictionException) {
            throw UnsatisfiableException("Failed to construct Sat4J problem.", cause = e)
        }

        if (maxConflicts > 0) solver.setTimeoutOnConflicts(maxConflicts)
        else solver.setTimeoutOnConflicts(Int.MAX_VALUE) // This disables Sat4j millisecond timeouts

        for (i in 1..problem.nbrVariables)
            solver.registerLiteral(i)

        return solver
    }

    override fun witnessOrThrow(assumptions: Literals): Instance {
        synchronized(solverLock) {
            literalSelection.rng = randomSequence.next()
            if (timeout > 0L) timeoutOrder.setTimeout(timeout)
            val assumption = assumptions.toSat4JVec()
            try {
                if (solver.isSatisfiable(assumption)) return solver.model().toInstance(instanceFactory)
                else throw UnsatisfiableException()
            } catch (e: org.sat4j.specs.TimeoutException) {
                throw IterationsReachedException(maxConflicts)
            } finally {
                if (forgetLearnedClauses) solver.clearLearntClauses()
            }
        }
    }

    override fun asSequence(assumptions: Literals): Sequence<Instance> {
        val solver = createSolver(false)
        solver.order = TimeoutOrder(solver.order)
        solver.order.phaseSelectionStrategy = RandomLiteralSelectionStrategySeeded(randomSequence.next())
        val iterator = ModelIterator(solver)
        val assumption = assumptions.toSat4JVec()
        val timeout = timeout
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        return generateSequence {
            try {
                if (millis() >= end) null
                else if (!iterator.isSatisfiable(assumption)) null
                else iterator.model().toInstance(instanceFactory)
            } catch (e: org.sat4j.specs.TimeoutException) {
                throw IterationsReachedException(maxConflicts)
            }
        }
    }

    override fun optimizeOrThrow(function: LinearObjective, assumptions: Literals): Instance {
        val pbSolver = createSolver(true) as PBSolver
        if (timeout > 0L)
            pbSolver.order = TimeoutOrder(pbSolver.order).apply { setTimeout(timeout) }

        val intWeights = function.weights.toIntArray(delta)
        if (function.maximize) intWeights.transformArray { -it }
        val indices = VecInt(IntArray(intWeights.size) { it + 1 })
        val bigInts = Vec(Array<BigInteger>(intWeights.size) { valueOf(intWeights[it].toLong()) })
        pbSolver.objectiveFunction = ObjectiveFunction(indices, bigInts)
        try {
            if (!pbSolver.isSatisfiable(assumptions.toSat4JVec()))
                throw UnsatisfiableException()
            return pbSolver.model().toInstance(instanceFactory)
        } catch (e: org.sat4j.specs.TimeoutException) {
            throw IterationsReachedException(maxConflicts)
        }
    }

    private class TimeoutOrder(val base: IOrder) : IOrder by base {
        var target = Long.MAX_VALUE
        var end = Long.MAX_VALUE

        fun setTimeout(timeout: Long) {
            target = timeout
            end = timeout + millis()
        }

        override fun select(): Int {
            if (end < millis()) throw TimeoutException(target)
            else return base.select()
        }
    }

    private class RandomLiteralSelectionStrategySeeded(var rng: Random) : IPhaseSelectionStrategy {
        override fun assignLiteral(p: Int) {}
        override fun init(nlength: Int) {}
        override fun init(v: Int, p: Int) {}
        override fun updateVar(p: Int) {}
        override fun updateVarAtDecisionLevel(q: Int) {}
        override fun select(v: Int) = if (rng.nextBoolean()) posLit(v) else negLit(v)
    }

    private fun Literals.toSat4JVec(): VecInt {
        return VecInt(this)
    }

    private fun IntArray.toInstance(factory: InstanceBuilder): Instance {
        val create = factory.create(size)
        create.setAll(this)
        return create
    }
}
