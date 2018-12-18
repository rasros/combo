package combo.sat.solvers

import combo.sat.*
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntSet
import combo.util.millis
import kotlin.jvm.JvmOverloads

class ExhaustiveSolver @JvmOverloads constructor(private val problem: Problem,
                                                 override val config: SolverConfig = SolverConfig(),
                                                 private val timeout: Long = -1L) : Solver {

    var totalSatisfied: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set
    val solutionDensity get() = totalSatisfied / totalEvaluated.toDouble()

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        val remap = createRemap(assumptions)
        val nbrVariables = problem.nbrVariables - assumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return LabelingPermutation.sequence(nbrVariables, config.labelingBuilder, config.nextRandom())
                .map { if (millis() <= end) it else throw TimeoutException(timeout) }
                .map { remapLabeling(assumptions, it, remap) }
                .firstOrNull {
                    val satisfied = problem.satisfies(it)
                    if (satisfied) totalSatisfied++
                    totalEvaluated++
                    satisfied
                } ?: throw UnsatisfiableException()
    }

    override fun sequence(assumptions: Literals): Sequence<Labeling> {
        val remap = createRemap(assumptions)
        val nbrVariables = problem.nbrVariables - assumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return LabelingPermutation.sequence(nbrVariables, config.labelingBuilder, config.nextRandom())
                .takeWhile { millis() <= end }
                .map { remapLabeling(assumptions, it, remap) }
                .filter {
                    val satisfied = problem.satisfies(it)
                    if (satisfied) totalSatisfied++
                    totalEvaluated++
                    satisfied
                }
    }

    private fun createRemap(assumptions: Literals): IntArray {
        val nbrVariables = problem.nbrVariables - assumptions.size
        return if (assumptions.isNotEmpty()) {
            val themap = IntArray(nbrVariables)
            var ix = 0
            val taken = IntSet(assumptions.size * 2)
            assumptions.forEach { taken.add(it.asIx()) }
            for (i in 0 until nbrVariables) {
                while (taken.contains(ix)) ix++
                themap[i] = ix++
            }
            themap
        } else EMPTY_INT_ARRAY
    }

    private fun remapLabeling(assumptions: Literals, labeling: Labeling, remap: IntArray): Labeling {
        return if (assumptions.isNotEmpty()) {
            val result = config.labelingBuilder.build(problem.nbrVariables, assumptions)
            for (i in labeling.indices) {
                result[remap[i]] = labeling[i]
            }
            result
        } else labeling
    }
}


