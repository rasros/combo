@file:JvmName("Sat4J")

package combo.sat

import combo.math.Rng
import combo.math.Vector
import combo.model.TimeoutException
import combo.model.UnsatisfiableException
import combo.util.applyTransform
import combo.util.millis
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
import org.sat4j.minisat.core.Solver as Sat4J
import org.sat4j.pb.SolverFactory as PBSolverFactory
import org.sat4j.specs.TimeoutException as Sat4JTimeoutException

// TODO stats
// TODO debug
class Sat4JSolver(val problem: Problem,
                  override val config: SolverConfig = SolverConfig(),
                  val timeout: Long = -1L,
                  val maxConflicsts: Int = Int.MAX_VALUE,
                  val forget: Boolean = true,
                  private val solver: Sat4J<*> = SolverFactory.newMiniLearningHeap()) : Solver {

    private val timeoutOrder = TimeoutOrder(solver.order)
    private val literalSelection = RandomLiteralSelectionStrategySeeded(config.nextRng())

    init {
        this.solver.order = timeoutOrder
        this.solver.order.phaseSelectionStrategy = RandomLiteralSelectionStrategySeeded(config.nextRng())
        this.solver.setup(problem)
    }

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        literalSelection.rng = config.nextRng()
        if (timeout > 0L) timeoutOrder.setTimeout(timeout)
        solver.setTimeoutOnConflicts(maxConflicsts)
        val assumption = contextLiterals.toDimacs()
        if (solver.isSatisfiable(assumption)) {
            val l = solver.model().toLabeling(config.labelingBuilder)
            if (forget) solver.clearLearntClauses()
            return l
        } else {
            throw UnsatisfiableException()
        }
    }

    override fun sequence(contextLiterals: Literals): Sequence<Labeling> {
        val base = SolverFactory.newMiniLearning(
                MixedDataStructureDanielWL(),
                VarOrderHeap(RandomLiteralSelectionStrategySeeded(config.nextRng())))
        base.setup(problem)
        val solver = ModelIterator(base)
        val iterator = ModelIterator(solver)
        val assumption = contextLiterals.toDimacs()
        val timeout = timeout
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        return generateSequence {
            if (millis() >= end) null
            else if (!iterator.isSatisfiable(assumption)) null
            else iterator.model().toLabeling(config.labelingBuilder)
        }
    }


    // Modified to allow selection of random seed
    private class RandomLiteralSelectionStrategySeeded(var rng: Rng) : IPhaseSelectionStrategy {
        override fun assignLiteral(p: Int) {}
        override fun init(nlength: Int) {}
        override fun init(v: Int, p: Int) {}
        override fun updateVar(p: Int) {}
        override fun updateVarAtDecisionLevel(q: Int) {}
        override fun select(v: Int) = if (rng.boolean()) posLit(v) else negLit(v)
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

private fun Sat4J<*>.setup(problem: Problem) {
    // This is the only way to disable the broken timeout handling in sat4j
    // (otherwise it spawns lots of threads)
    setTimeoutOnConflicts(Int.MAX_VALUE)
    newVar(problem.nbrVariables)
    try {
        for (c in problem.sentences)
            when (c) {
                is Cardinality -> {
                    val cardLits = c.literals.toDimacs()
                    when (c.operator) {
                        Cardinality.Operator.AT_LEAST -> addAtLeast(cardLits, c.degree)
                        Cardinality.Operator.AT_MOST -> addAtMost(cardLits, c.degree)
                        Cardinality.Operator.EXACTLY -> addExactly(cardLits, c.degree)
                    }
                }
                else -> c.toCnf().forEach { addClause(it.literals.toDimacs()) }
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
        newClause[i] = this[i].asDimacs()
    return VecInt(newClause)
}

private fun IntArray.toLabeling(builder: LabelingBuilder<*>): Labeling {
    val nbrPos = count { it > 0 }
    val lits = IntArray(nbrPos)
    var k = 0
    forEachIndexed { i, dl -> if (dl > 0) lits[k++] = i.asLiteral(true) }
    return builder.build(size, lits)
}

class Sat4JLinearOptimizer(val problem: Problem,
                           override val config: SolverConfig = SolverConfig(),
                           val timeout: Long = -1L,
                           val maxConflicts: Int = Int.MAX_VALUE,
                           private var optimizerCreator: () -> PBSolver = { PBSolverFactory.newLight() as PBSolver })
    : LinearOptimizer {

    override fun optimizeOrThrow(weights: Vector, contextLiterals: Literals): Labeling {
        val pbSolver = optimizerCreator()
        pbSolver.setTimeoutOnConflicts(maxConflicts)
        if (timeout >= 0L)
            pbSolver.order = TimeoutOrder(pbSolver.order).apply { setTimeout(timeout) }
        val optimizer = OptToPBSATAdapter(PseudoOptDecorator(WeightedMaxSatDecorator(pbSolver)))
        pbSolver.setup(problem)
        val intWeights = weights.toIntArray()
        if (config.maximize) intWeights.applyTransform { -it }
        optimizer.objectiveFunction = ObjectiveFunction(
                VecInt((1..intWeights.size).toList().toIntArray()),
                Vec(intWeights.map { valueOf(it.toLong()) }.toTypedArray()))
        if (!optimizer.isSatisfiable(contextLiterals.toDimacs())) {
            throw UnsatisfiableException()
        }
        return optimizer.model().toLabeling(config.labelingBuilder)
    }
}