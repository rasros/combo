package combo.sat

import combo.model.TimeoutException
import combo.model.UnsatisfiableException
import combo.util.EMPTY_INT_ARRAY
import combo.util.IndexSet
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

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        val remap = createRemap(contextLiterals)
        val nbrVariables = problem.nbrVariables - contextLiterals.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return LabelingPermutation.sequence(nbrVariables, config.labelingBuilder, config.nextRng())
                .map { if (millis() <= end) it else throw TimeoutException(timeout) }
                .map { remapLabeling(contextLiterals, it, remap) }
                .firstOrNull {
                    val satisfied = problem.satisfies(it)
                    if (satisfied) totalSatisfied++
                    totalEvaluated++
                    satisfied
                } ?: throw UnsatisfiableException()
    }

    override fun sequence(contextLiterals: Literals): Sequence<Labeling> {
        val remap = createRemap(contextLiterals)
        val nbrVariables = problem.nbrVariables - contextLiterals.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return LabelingPermutation.sequence(nbrVariables, config.labelingBuilder, config.nextRng())
                .takeWhile { millis() <= end }
                .map { remapLabeling(contextLiterals, it, remap) }
                .filter {
                    val satisfied = problem.satisfies(it)
                    if (satisfied) totalSatisfied++
                    totalEvaluated++
                    satisfied
                }
    }

    private fun createRemap(contextLiterals: Literals): IntArray {
        val nbrVariables = problem.nbrVariables - contextLiterals.size
        return if (contextLiterals.isNotEmpty()) {
            val themap = IntArray(nbrVariables)
            var ix = 0
            val taken = IndexSet(contextLiterals.size * 2)
            contextLiterals.forEach { taken.add(it.asIx()) }
            for (i in 0 until nbrVariables) {
                while (taken.contains(ix)) ix++
                themap[i] = ix++
            }
            themap
        } else EMPTY_INT_ARRAY
    }

    private fun remapLabeling(contextLiterals: Literals, labeling: Labeling, remap: IntArray): Labeling {
        return if (contextLiterals.isNotEmpty()) {
            val result = config.labelingBuilder.build(problem.nbrVariables, contextLiterals)
            for (i in labeling.indices) {
                result[remap[i]] = labeling[i]
            }
            result
        } else labeling
    }
}


