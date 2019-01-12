package combo.sat.solvers

import cern.colt.matrix.tint.IntMatrix1D
import cern.colt.matrix.tint.IntMatrix2D
import cern.colt.matrix.tint.impl.DenseIntMatrix1D
import cern.colt.matrix.tint.impl.SparseIntMatrix2D
import com.joptimizer.exception.InfeasibleProblemException
import com.joptimizer.exception.IterationsLimitException
import com.joptimizer.optimizers.BIPLokbaTableMethod
import com.joptimizer.optimizers.BIPOptimizationRequest
import combo.math.RandomSequence
import combo.sat.*
import combo.sat.Relation.*
import combo.util.IntCollection
import combo.util.IntList
import combo.util.nanos
import org.apache.commons.logging.impl.NoOpLog
import kotlin.math.roundToInt

/**
 * [Optimizer] of [LinearObjective] using the binary integer programming (BIP) solver of the JOptimizer library.
 * Assumptions during solving are added using the A=b matrices, allowing reuse of the constant G<=h matrices.
 */
class JOptimizerSolver(val problem: Problem,
                       val labelingFactory: LabelingFactory = BitFieldLabelingFactory,
                       val randomSeed: Long = nanos(),
                       val maxIterations: Int = Int.MAX_VALUE,
                       val delta: Double = 1e-3,
                       constraintHandler: (Constraint, row: Int, G: IntMatrix2D, h: IntMatrix1D) -> Int = { _, _, _, _ ->
                     throw UnsupportedOperationException("Register custom constraint handler in order to handle extra constraints.")
                 },
                       constraintHandlerRowCounter: (Constraint) -> Int = { _ ->
                     throw UnsupportedOperationException("Register custom constraint handler in order to handle extra constraints.")
                 }) : Optimizer<LinearObjective>, Solver {

    var totalSuccesses: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set

    private val randomSequence = RandomSequence(randomSeed)
    private val G: IntMatrix2D
    private val h: IntMatrix1D

    init {
        var nbrConstraints = 0
        for (c in problem.constraints) {
            if (c is Conjunction)
                nbrConstraints += c.literals.size
            else if (c is Disjunction)
                nbrConstraints++
            else if (c is Cardinality) {
                nbrConstraints++
                if (c.relation === EQ) nbrConstraints++
                if (c.relation === NE)
                    throw UnsupportedOperationException("Relation != cannot be expressed as a linear program.")
            } else if (c is Reified) {
                nbrConstraints += 1 + c.literals.size
            } else nbrConstraints += constraintHandlerRowCounter.invoke(c)
        }

        G = SparseIntMatrix2D(nbrConstraints, problem.nbrVariables)
        h = DenseIntMatrix1D(nbrConstraints)

        var row = 0

        fun addDisjunction(literals: IntCollection) {
            var nbrNegative = 0
            for (l in literals) {
                G.set(row, l.toIx(), if (l.toBoolean()) -1 else 1)
                nbrNegative += if (l.toBoolean()) 0 else 1
            }
            h.set(row, -1 + nbrNegative)
            row++
        }
        for (c in problem.constraints)
            if (c is Cardinality) {
                if (c.relation in arrayOf(GE, GT, EQ)) {
                    for (l in c.literals)
                        G.set(row, l.toIx(), -1)
                    h.set(row, -c.degree - if (c.relation == GT) 1 else 0)
                    row++
                }
                if (c.relation in arrayOf(LE, LT, EQ)) {
                    for (l in c.literals)
                        G.set(row, l.toIx(), 1)
                    h.set(row, c.degree - if (c.relation == LT) 1 else 0)
                    row++
                }
            } else if (c is Conjunction)
                for (lit in c.literals) addDisjunction(IntList(intArrayOf(lit)))
            else if (c is Disjunction)
                addDisjunction(c.literals)
            else if (c is Reified)
                for (d in c.toCnf()) addDisjunction(d.literals)
            else {
                row += constraintHandler.invoke(c, row, G, h)
            }
    }

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        val rng = randomSequence.next()
        return optimizeOrThrow(
                LinearObjective(false, DoubleArray(problem.nbrVariables) { rng.nextDouble() - 0.5 }), assumptions
        )
    }

    /**
     * It converts double weight vector into int-vector by dividing by delta.
     * Does not support timeout.
     *
     * @throws UnsatisfiableException
     * @throws IterationsReachedException by config.maxIterations
     */
    override fun optimizeOrThrow(function: LinearObjective, assumptions: Literals): Labeling {

        totalEvaluated++

        val request = BIPOptimizationRequest().apply {
            val mult = if (function.maximize) -1 else 1
            setC(IntArray(function.weights.size) { i ->
                (function.weights[i] / delta).roundToInt() * mult
            })
            setG(this@JOptimizerSolver.G)
            setH(this@JOptimizerSolver.h)
            if (assumptions.isNotEmpty()) {
                val A = SparseIntMatrix2D(assumptions.size, problem.nbrVariables)
                val B = DenseIntMatrix1D(assumptions.size)
                for ((i, lit) in assumptions.withIndex()) {
                    A.set(i, lit.toIx(), 1)
                    B.set(i, if (lit.toBoolean()) 1 else 0)
                }
                setA(A)
                setB(B)
            }
            maxIteration = maxIterations
        }
        val opt = BIPLokbaTableMethod().apply {
            try {
                val cls = Class.forName("com.joptimizer.optimizers.BIPLokbaTableMethod")
                val fld = cls.getDeclaredField("log")
                fld.isAccessible = true
                fld.set(this, NoOpLog())
            } catch (e: Exception) {
            }
            bipOptimizationRequest = request
            try {
                optimize()
            } catch (e: InfeasibleProblemException) {
                throw UnsatisfiableException(cause = e)
            } catch (e: IterationsLimitException) {
                throw IterationsReachedException(maxIterations)
            }
        }
        return opt.bipOptimizationResponse.solution.toLabeling(labelingFactory).also {
            totalSuccesses++
        }
    }

    private companion object {
        init {
            try {
                val cls = Class.forName("com.joptimizer.optimizers.BIPPresolver")
                val fld = cls.getDeclaredField("log")
                fld.isAccessible = true
                fld.set(null, NoOpLog())
            } catch (e: Exception) {
            }
        }
    }

    private fun IntArray.toLabeling(factory: LabelingFactory): Labeling {
        val nbrPos = count { it > 0 }
        val lits = IntArray(nbrPos)
        var k = 0
        forEachIndexed { i, dl -> if (dl == 1) lits[k++] = i.toLiteral(true) }
        return factory.create(size).apply { setAll(lits) }
    }
}
