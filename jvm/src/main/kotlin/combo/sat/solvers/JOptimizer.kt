package combo.sat.solvers

import cern.colt.matrix.tint.IntMatrix1D
import cern.colt.matrix.tint.IntMatrix2D
import cern.colt.matrix.tint.impl.DenseIntMatrix1D
import cern.colt.matrix.tint.impl.SparseIntMatrix2D
import com.joptimizer.exception.InfeasibleProblemException
import com.joptimizer.exception.IterationsLimitException
import com.joptimizer.optimizers.BIPLokbaTableMethod
import com.joptimizer.optimizers.BIPOptimizationRequest
import combo.math.toIntArray
import combo.sat.*
import combo.sat.constraints.Cardinality
import combo.sat.constraints.Conjunction
import combo.sat.constraints.Disjunction
import combo.sat.constraints.ReifiedEquivalent
import combo.sat.constraints.Relation.*
import combo.util.*
import org.apache.commons.logging.impl.NoOpLog
import kotlin.random.Random

/**
 * TODO support linear, cardinality with negated literals, reifiedeq, reifiedimp
 * [Optimizer] of [LinearObjective] using the binary integer programming (BIP) solver of the JOptimizer library.
 * Assumptions during solving are added using the A=b matrices, allowing reuse of the constant G<=h matrices.
 * Using this class requires an extra optional dependency, like so in gradle: compile "com.joptimizer:joptimizer:4.0.0"
 */
class JOptimizer @JvmOverloads constructor(
        val problem: Problem,
        constraintHandler: (Constraint, row: Int, G: IntMatrix2D, h: IntMatrix1D) -> Int = { _, _, _, _ ->
            throw UnsupportedOperationException("Register custom constraint handler in order to handle extra constraints.")
        },
        constraintHandlerRowCounter: (Constraint) -> Int = { _ ->
            throw UnsupportedOperationException("Register custom constraint handler in order to handle extra constraints.")
        }) : Optimizer<LinearObjective> {

    override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            field = value
        }
    private var rng = Random(randomSeed)

    /**
     * Timeout is not supported by JOptimizer, use maxIterations for pre-mature cancellation..
     */
    override var timeout: Long = -1L

    /**
     * Maximum number of iteration in the search algorithm.
     */
    var maxIterations: Int = Int.MAX_VALUE

    /**
     * Sensitivity of conversion of float objective function to int objective function.
     */
    var delta: Float = 1e-4f

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [SparseBitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceBuilder: InstanceBuilder = BitArrayBuilder

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
            } else if (c is ReifiedEquivalent) {
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
            else if (c is ReifiedEquivalent)
                for (d in c.toCnf()) addDisjunction(d.literals)
            else {
                row += constraintHandler.invoke(c, row, G, h)
            }
    }

    /**
     * It converts double weight vector into int-vector by dividing by delta.
     * Does not support timeout.
     *
     * @throws UnsatisfiableException
     * @throws IterationsReachedException by maxIterations
     */
    override fun optimizeOrThrow(function: LinearObjective, assumptions: IntCollection, guess: MutableInstance?): Instance {
        val request = BIPOptimizationRequest().apply {
            val mult = if (function.maximize) -1 else 1
            setC(function.weights.toIntArray(delta)
                    .apply { if (function.maximize) this.transformArray { it * mult } })
            setG(this@JOptimizer.G)
            setH(this@JOptimizer.h)
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
            disableLogging(this, "com.joptimizer.optimizers.BIPLokbaTableMethod", "log")
            disableLogging(null, "com.joptimizer.optimizers.BIPBfMethod", "log")
            bipOptimizationRequest = request
            try {
                optimize()
            } catch (e: InfeasibleProblemException) {
                throw UnsatisfiableException(cause = e)
            } catch (e: IterationsLimitException) {
                throw IterationsReachedException(maxIterations)
            }
        }
        return opt.bipOptimizationResponse.solution.toInstance(instanceBuilder)
    }

    private fun disableLogging(obj: Any?, name: String, field: String) {
        try {
            val cls = Class.forName(name)
            val fld = cls.getDeclaredField(field)
            fld.isAccessible = true
            fld.set(obj, NoOpLog())
        } catch (e: Exception) {
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

    private fun IntArray.toInstance(builder: InstanceBuilder): Instance {
        val nbrPos = count { it > 0 }
        val lits = IntArray(nbrPos)
        var k = 0
        forEachIndexed { i, dl -> if (dl == 1) lits[k++] = i.toLiteral(true) }
        return builder.create(size).apply { setAll(lits) }
    }
}
