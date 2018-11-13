package combo.sat.solvers

import cern.colt.matrix.tint.IntMatrix1D
import cern.colt.matrix.tint.IntMatrix2D
import cern.colt.matrix.tint.impl.DenseIntMatrix1D
import cern.colt.matrix.tint.impl.SparseIntMatrix2D
import com.joptimizer.exception.InfeasibleProblemException
import com.joptimizer.exception.IterationsLimitException
import com.joptimizer.optimizers.BIPLokbaTableMethod
import com.joptimizer.optimizers.BIPOptimizationRequest
import combo.math.Vector
import combo.model.IterationsReachedException
import combo.model.UnsatisfiableException
import combo.sat.*
import combo.util.HashIntSet
import org.apache.commons.logging.impl.NoOpLog
import kotlin.math.roundToInt

class JOptimizer(val problem: Problem,
                 override val config: SolverConfig = SolverConfig(),
                 val maxItr: Int = Int.MAX_VALUE,
                 val delta: Double = 1e-3) : LinearOptimizer {

    /**
     * It converts double weight vector into int-vector by dividing by delta.
     * Does not support timeout.
     *
     * @throws UnsatisfiableException
     * @throws IterationsReachedException by config.maxIterations
     */
    override fun optimizeOrThrow(weights: Vector, contextLiterals: Literals): Labeling {
        val p = if (contextLiterals.isNotEmpty())
            problem.simplify(HashIntSet().apply { addAll(contextLiterals) }, true)
        else problem
        val (G, h) = setupProblem(p)

        val request = BIPOptimizationRequest().apply {
            val mult = if (config.maximize) -1 else 1
            setC(IntArray(weights.size) { i ->
                (weights[i] / delta).roundToInt() * mult
            })
            setG(G)
            setH(h)
            isPresolvingDisabled = true
            maxIteration = maxItr
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
                throw IterationsReachedException(maxItr)
            }
        }
        return opt.bipOptimizationResponse.solution.toLabeling(config.labelingBuilder).also {
            if (!p.satisfies(it)) throw UnsatisfiableException()
        }
    }

    companion object {
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

    private data class JOptimizerLP(val G: IntMatrix2D, val h: IntMatrix1D)

    private fun setupProblem(problem: Problem): JOptimizerLP {
        var nbrConstraints = 0
        for (c in problem.sentences)
            if (c is Disjunction)
                nbrConstraints++
            else if (c is Cardinality) {
                nbrConstraints++
                if (c.operator === Cardinality.Operator.EXACTLY) nbrConstraints++
            } else nbrConstraints += c.toCnf().count()

        val G = SparseIntMatrix2D(nbrConstraints, problem.nbrVariables)
        val h = DenseIntMatrix1D(nbrConstraints)

        var row = 0
        for (c in problem.sentences)
            if (c is Cardinality) {
                if (c.operator == Cardinality.Operator.EXACTLY || c.operator === Cardinality.Operator.AT_LEAST) {
                    for (l in c.literals)
                        G.set(row, l.asIx(), -1)
                    h.set(row, -1)
                    row++
                }
                if (c.operator == Cardinality.Operator.EXACTLY || c.operator === Cardinality.Operator.AT_MOST) {
                    for (l in c.literals)
                        G.set(row, l.asIx(), 1)
                    h.set(row, 1)
                    row++
                }
            } else {
                for (d in c.toCnf()) {
                    var nbrNegative = 0
                    for (l in d.literals) {
                        G.set(row, l.asIx(), if (l.asBoolean()) -1 else 1)
                        nbrNegative += if (l.asBoolean()) 0 else 1
                    }
                    h.set(row, -1 + nbrNegative)
                    row++
                }
            }
        return JOptimizerLP(G, h)
    }

    private fun IntArray.toLabeling(builder: LabelingBuilder<*>): Labeling {
        val nbrPos = count { it > 0 }
        val lits = IntArray(nbrPos)
        var k = 0
        forEachIndexed { i, dl -> if (dl == 1) lits[k++] = i.asLiteral(true) }
        return builder.build(size, lits)
    }
}
