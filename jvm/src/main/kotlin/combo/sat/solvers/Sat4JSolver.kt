package combo.sat.solvers

import combo.math.toIntArray
import combo.sat.*
import combo.sat.constraints.*
import combo.sat.constraints.Relation.*
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.millis
import combo.util.nanos
import org.sat4j.core.LiteralsUtils.negLit
import org.sat4j.core.LiteralsUtils.posLit
import org.sat4j.core.Vec
import org.sat4j.core.VecInt
import org.sat4j.minisat.SolverFactory
import org.sat4j.minisat.core.IPhaseSelectionStrategy
import org.sat4j.pb.OptToPBSATAdapter
import org.sat4j.pb.PseudoOptDecorator
import org.sat4j.pb.core.PBSolver
import org.sat4j.specs.ContradictionException
import org.sat4j.specs.ISolver
import org.sat4j.specs.ISolverService
import org.sat4j.tools.ModelIterator
import org.sat4j.tools.SearchListenerAdapter
import java.math.BigInteger
import java.math.BigInteger.valueOf
import kotlin.random.Random
import org.sat4j.minisat.core.Solver as Sat4J
import org.sat4j.pb.ObjectiveFunction as Sat4JObjectiveFunction
import org.sat4j.pb.SolverFactory as PBSolverFactory

/**
 * [Solver] and [Optimizer] of [LinearObjective] using the Sat4J SAT library. Using this requires an extra optional
 * dependency, like so in gradle: compile "org.ow2.sat4j:org.ow2.sat4j.maxsat:2.3.5"
 * The internal solver is re-used for [witness] and [asSequence] and kept as [ThreadLocal]. For [optimize] a new one
 * is created for each call due to limitations in Sat4J.
 */
class Sat4JSolver @JvmOverloads constructor(
        val problem: Problem,
        val constraintHandler: (Constraint, ISolver) -> Nothing = { c, _ ->
            throw UnsupportedOperationException("Constraint $c cannot be SAT encoded. " +
                    "Register custom constraint handler in order to handle extra constraints.")
        }) : Solver, Optimizer<LinearObjective> {

    override var randomSeed: Int
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.randomSeed
    private var randomSequence = RandomSequence(nanos().toInt())

    override var timeout: Long = -1L

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [SparseBitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceBuilder: InstanceBuilder = BitArrayBuilder

    /**
     * Solver aborts after this number of conflicts are reached.
     */
    var maxConflicts: Int = 0

    /**
     * Precision with which to convert floating point constraint and objective function into integer constraints.
     * See [toIntArray]
     */
    var delta: Float = 0.01f

    /**
     * Simplify weights with GCD before optimizing.
     */
    var gcdOptimize: Boolean = true

    private val solverTL = ThreadLocal.withInitial {
        val solver: Sat4J<*> = if (problem.constraints.any { it is Linear }) PBSolverFactory.newLight() as Sat4J<*>
        else SolverFactory.newLight() as Sat4J<*>
        setupSolver(solver)
        solver
    }

    private fun setupSolver(solver: ISolver) {

        solver.setKeepSolverHot(true)
        solver.newVar(problem.binarySize)

        try {
            for (c in problem.constraints)
                when (c) {
                    is Disjunction -> solver.addClause(c.literals.toSat4JVec())
                    is Conjunction -> c.literals.forEach {
                        solver.addClause(VecInt(intArrayOf(it)))
                    }
                    is Cardinality -> {
                        val cardLits = c.literals.toSat4JVec()
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
                    is Linear -> {
                        val pbSolver = solver as PBSolver
                        val cardLits = VecInt(c.literals.toArray())
                        val weights = Vec(Array<BigInteger>(cardLits.size()) {
                            valueOf(c.weights[c.literals[cardLits[it]]].toLong())
                        })
                        when (c.relation) {
                            GE -> pbSolver.addPseudoBoolean(cardLits, weights, true, valueOf(c.degree.toLong()))
                            LE -> pbSolver.addPseudoBoolean(cardLits, weights, false, valueOf(c.degree.toLong()))
                            EQ -> {
                                pbSolver.addPseudoBoolean(cardLits, weights, true, valueOf(c.degree.toLong()))
                                pbSolver.addPseudoBoolean(cardLits, weights, false, valueOf(c.degree.toLong()))
                            }
                            GT -> pbSolver.addPseudoBoolean(cardLits, weights, true, valueOf(c.degree.toLong() + 1L))
                            LT -> pbSolver.addPseudoBoolean(cardLits, weights, false, valueOf(c.degree.toLong() - 1L))
                            NE ->
                                throw UnsupportedOperationException("Relation != cannot be expressed as a linear inequality.")
                        }

                    }
                    is ReifiedEquivalent -> c.toCnf().forEach { solver.addClause(it.literals.toSat4JVec()) }
                    is ReifiedImplies -> c.toCnf().forEach { solver.addClause(it.literals.toSat4JVec()) }
                    is Tautology -> {
                    }
                    is Empty -> throw UnsatisfiableException("Empty constraint in problem.")
                    else -> constraintHandler.invoke(c, solver)
                }
        } catch (e: ContradictionException) {
            throw UnsatisfiableException("Failed to construct Sat4J problem.", cause = e)
        }

        if (maxConflicts > 0) solver.setTimeoutOnConflicts(maxConflicts)
        else solver.setTimeoutOnConflicts(Int.MAX_VALUE)
        // This disables Sat4j millisecond timeouts which spawns a thread for each solving

        for (i in 1..problem.binarySize)
            solver.registerLiteral(i)

    }

    override fun witnessOrThrow(assumptions: IntCollection, guess: MutableInstance?): Instance {
        val solver = solverTL.get()

        if (maxConflicts > 0) solver.setTimeoutOnConflicts(maxConflicts)
        else solver.setTimeoutOnConflicts(Int.MAX_VALUE)

        if (timeout > 0L) solver.setSearchListener(TimeoutListener(timeout))
        else solver.setSearchListener(VoidListener)

        if (guess == null)
            solver.order.phaseSelectionStrategy = RandomLiteralSelectionStrategySeeded(randomSequence.next())
        else solver.order.phaseSelectionStrategy = InitialGuessSelectionStrategy(guess)

        val assumption = assumptions.toSat4JVec()
        try {
            if (solver.isSatisfiable(assumption)) return solver.model().toInstance()
            else throw UnsatisfiableException()
        } catch (e: org.sat4j.specs.TimeoutException) {
            throw IterationsReachedException(maxConflicts)
        }
    }

    override fun asSequence(assumptions: IntCollection): Sequence<Instance> {
        val solver = solverTL.get()

        if (maxConflicts > 0) solver.setTimeoutOnConflicts(maxConflicts)
        else solver.setTimeoutOnConflicts(Int.MAX_VALUE)

        if (timeout > 0L) solver.setSearchListener(TimeoutListener(timeout))
        else solver.setSearchListener(VoidListener)

        solver.order.phaseSelectionStrategy = RandomLiteralSelectionStrategySeeded(randomSequence.next())

        val iterator = ModelIterator(solver)
        val assumption = assumptions.toSat4JVec()
        val timeout = timeout
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        return generateSequence {
            try {
                if (timeout > 0L && millis() >= end) null
                else if (!iterator.isSatisfiable(assumption)) null
                else iterator.model().toInstance()
            } catch (e: org.sat4j.specs.TimeoutException) {
                null
            }
        }
    }

    override fun optimizeOrThrow(function: LinearObjective, assumptions: IntCollection, guess: MutableInstance?): Instance {
        val pbSolver = PBSolverFactory.newLight() as PBSolver
        setupSolver(pbSolver)

        if (timeout > 0L) pbSolver.setSearchListener(TimeoutListener(timeout))

        val intWeights = function.weights.toIntArray(delta, gcdOptimize)
        val bigIntWeights = Vec(Array<BigInteger>(function.weights.size) {
            val i = if (function.maximize) -intWeights[it] else intWeights[it]
            valueOf(i.toLong())
        })
        val variableLiterals = VecInt(IntArray(bigIntWeights.size()) { it + 1 })
        pbSolver.objectiveFunction = Sat4JObjectiveFunction(variableLiterals, bigIntWeights)
        if (guess != null) pbSolver.order.phaseSelectionStrategy = InitialGuessSelectionStrategy(guess)

        val assumption = assumptions.toSat4JVec()
        val pseudoOptDecorator = PseudoOptDecorator(pbSolver)
        val optimizer = OptToPBSATAdapter(pseudoOptDecorator)
        try {
            if (optimizer.isSatisfiable(assumption))
                return optimizer.model().toInstance()
            else throw UnsatisfiableException()
        } catch (e: org.sat4j.specs.TimeoutException) {
            throw IterationsReachedException(maxConflicts)
        } finally {
            pbSolver.reset()
        }
    }

    override fun isComplete() = true

    private class TimeoutListener(val timeout: Long) : SearchListenerAdapter<ISolverService>() {
        private val end = timeout + millis()
        override fun assuming(p: Int) {
            if (end < millis()) throw TimeoutException(timeout)
        }
    }

    private object VoidListener : SearchListenerAdapter<ISolverService>()

    private class RandomLiteralSelectionStrategySeeded(val rng: Random) : IPhaseSelectionStrategy {
        override fun assignLiteral(p: Int) {}
        override fun init(nlength: Int) {}
        override fun init(v: Int, p: Int) {}
        override fun updateVar(p: Int) {}
        override fun updateVarAtDecisionLevel(q: Int) {}
        override fun select(v: Int) = if (rng.nextBoolean()) posLit(v) else negLit(v)
    }

    private class InitialGuessSelectionStrategy(val guess: Instance) : IPhaseSelectionStrategy {
        override fun assignLiteral(p: Int) {}
        override fun init(nlength: Int) {}
        override fun init(v: Int, p: Int) {}
        override fun updateVarAtDecisionLevel(q: Int) {}
        override fun updateVar(p: Int) {}
        override fun select(v: Int) = if (guess[v - 1]) posLit(v) else negLit(v)
    }

    private fun IntCollection.toSat4JVec() = VecInt(this.toArray())

    private fun IntArray.toInstance(): Instance {
        val create = instanceBuilder.create(size)
        create.setAll(this)
        return create
    }
}
