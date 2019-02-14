package combo.sat.solvers

import combo.math.RandomSequence
import combo.math.toIntArray
import combo.sat.*
import combo.sat.Relation.*
import combo.util.millis
import combo.util.nanos
import combo.util.transformArray
import org.sat4j.core.LiteralsUtils.negLit
import org.sat4j.core.LiteralsUtils.posLit
import org.sat4j.core.Vec
import org.sat4j.core.VecInt
import org.sat4j.maxsat.WeightedMaxSatDecorator
import org.sat4j.minisat.SolverFactory
import org.sat4j.minisat.constraints.MixedDataStructureDanielWL
import org.sat4j.minisat.core.IOrder
import org.sat4j.minisat.core.IPhaseSelectionStrategy
import org.sat4j.minisat.orders.VarOrderHeap
import org.sat4j.pb.ObjectiveFunction
import org.sat4j.pb.OptToPBSATAdapter
import org.sat4j.pb.PseudoOptDecorator
import org.sat4j.pb.core.PBSolver
import org.sat4j.specs.ContradictionException
import org.sat4j.tools.ModelIterator
import java.math.BigInteger.valueOf
import kotlin.random.Random
import org.sat4j.minisat.core.Solver as Sat4J
import org.sat4j.pb.SolverFactory as PBSolverFactory
import org.sat4j.specs.TimeoutException as Sat4JTimeoutException

/**
 * [Solver] and [Optimizer] of [LinearObjective] using the Sat4J SAT library.
 */
class Sat4JSolver @JvmOverloads constructor(
        val problem: Problem,
        val solverCreator: () -> Sat4J<*> = { SolverFactory.newMiniLearningHeap() },
        var optimizerCreator: () -> PBSolver = { PBSolverFactory.newLight() as PBSolver },
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
     * [IntSetInstanceFactory] otherwise [BitFieldInstanceFactory].
     */
    var instanceFactory: InstanceFactory = BitFieldInstanceFactory

    /**
     * Solver aborts after this number of conflicts are reached.
     */
    var maxConflicts: Int = Int.MAX_VALUE

    /**
     * Solver forgets all learned clauses after each [witness]. Setting to false might improve solving speed but
     * introduces bias in the generated instances.
     */
    var forgetLearnedClauses: Boolean = true

    private val solverLock = Object()
    private var randomSequence = RandomSequence(nanos())
    private val literalSelection = RandomLiteralSelectionStrategySeeded(randomSequence.next())
    private val solver: Sat4J<*> = solverCreator.invoke()
    private val timeoutOrder = TimeoutOrder(solver.order)

    init {
        this.solver.order = timeoutOrder
        this.solver.order.phaseSelectionStrategy = RandomLiteralSelectionStrategySeeded(randomSequence.next())
        this.solver.setup(problem, constraintHandler)
    }

    override fun witnessOrThrow(assumptions: Literals): Instance {
        synchronized(solverLock) {
            literalSelection.rng = randomSequence.next()
            if (timeout > 0L) timeoutOrder.setTimeout(timeout)
            solver.setTimeoutOnConflicts(maxConflicts)
            val assumption = assumptions.toDimacs()
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

    override fun sequence(assumptions: Literals): Sequence<Instance> {
        val base = SolverFactory.newMiniLearning(
                MixedDataStructureDanielWL(),
                VarOrderHeap(RandomLiteralSelectionStrategySeeded(randomSequence.next())))
        base.setup(problem, constraintHandler)
        base.setTimeoutOnConflicts(maxConflicts)
        val solver = ModelIterator(base)
        val iterator = ModelIterator(solver)
        val assumption = assumptions.toDimacs()
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
        val pbSolver = optimizerCreator()
        pbSolver.setTimeoutOnConflicts(maxConflicts)
        if (timeout >= 0L)
            pbSolver.order = TimeoutOrder(pbSolver.order).apply { setTimeout(timeout) }
        val optimizer = OptToPBSATAdapter(PseudoOptDecorator(WeightedMaxSatDecorator(pbSolver)))
        pbSolver.setup(problem, constraintHandler)
        val intWeights = function.weights.toIntArray()
        if (function.maximize) intWeights.transformArray { -it }
        optimizer.setTimeoutOnConflicts(maxConflicts)
        optimizer.objectiveFunction = ObjectiveFunction(
                VecInt((1..intWeights.size).toList().toIntArray()),
                Vec(intWeights.map { valueOf(it.toLong()) }.toTypedArray()))
        try {
            if (!optimizer.isSatisfiable(assumptions.toDimacs()))
                throw UnsatisfiableException()
            return optimizer.model().toInstance(instanceFactory)
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

    // Modified to allow selection of random seed
    private class RandomLiteralSelectionStrategySeeded(var rng: Random) : IPhaseSelectionStrategy {
        override fun assignLiteral(p: Int) {}
        override fun init(nlength: Int) {}
        override fun init(v: Int, p: Int) {}
        override fun updateVar(p: Int) {}
        override fun updateVarAtDecisionLevel(q: Int) {}
        override fun select(v: Int) = if (rng.nextBoolean()) posLit(v) else negLit(v)
    }

    private fun Sat4J<*>.setup(problem: Problem, constraintHandler: (Constraint, org.sat4j.minisat.core.Solver<*>) -> Nothing) {
        setTimeoutOnConflicts(Int.MAX_VALUE)
        // This was the only way to disable the broken timeout handling in sat4j (otherwise it spawns lots of threads)
        newVar(problem.nbrVariables)
        try {
            for (c in problem.constraints)
                when (c) {
                    is Disjunction -> addClause(c.literals.toArray().apply { sort() }.toDimacs())
                    is Conjunction -> c.literals.forEach {
                        addClause(intArrayOf(it).toDimacs())
                    }
                    is Cardinality -> {
                        val cardLits = c.literals.toArray().apply { sort() }.toDimacs()
                        when (c.relation) {
                            GE -> addAtLeast(cardLits, c.degree)
                            LE -> addAtMost(cardLits, c.degree)
                            EQ -> addExactly(cardLits, c.degree)
                            GT -> addAtLeast(cardLits, c.degree + 1)
                            LT -> addAtMost(cardLits, c.degree - 1)
                            NE ->
                                throw UnsupportedOperationException("Relation != cannot be expressed as a linear inequality.")
                        }
                    }
                    is Reified -> c.toCnf().forEach { addClause(it.literals.toArray().apply { sort() }.toDimacs()) }
                    is Tautology -> Unit
                    else -> constraintHandler.invoke(c, this)
                }
        } catch (e: ContradictionException) {
            throw UnsatisfiableException("Failed to construct Sat4J problem.", cause = e)
        }
        for (i in 1..problem.nbrVariables)
            registerLiteral(i)
    }

    private fun Literals.toDimacs(): VecInt {
        val newClause = IntArray(size)
        for (i in indices)
            newClause[i] = this[i].toDimacs()
        return VecInt(newClause)
    }

    private fun IntArray.toInstance(factory: InstanceFactory): Instance {
        val nbrPos = count { it > 0 }
        val lits = IntArray(nbrPos)
        var k = 0
        forEachIndexed { i, dl -> if (dl > 0) lits[k++] = i.toLiteral(true) }
        return factory.create(size).apply { setAll(lits) }
    }
}
