package combo.sat.solvers

import combo.math.RandomSequence
import combo.sat.*
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntSet
import combo.util.millis
import combo.util.nanos
import kotlin.jvm.JvmOverloads

class ExhaustiveSolver @JvmOverloads constructor(
        val problem: Problem,
        val timeout: Long = -1L,
        val randomSeed: Long = nanos(),
        val labelingFactory: LabelingFactory = BitFieldLabelingFactory) : Solver, Optimizer<ObjectiveFunction> {

    var totalSatisfied: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set
    val solutionDensity get() = totalSatisfied / totalEvaluated.toDouble()
    private val randomSequence = RandomSequence(randomSeed)

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        val remap = createRemap(assumptions)
        val nbrVariables = problem.nbrVariables - assumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return LabelingPermutation.sequence(nbrVariables, labelingFactory, randomSequence.next())
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
        return LabelingPermutation.sequence(nbrVariables, labelingFactory, randomSequence.next())
                .takeWhile { millis() <= end }
                .map { remapLabeling(assumptions, it, remap) }
                .filter {
                    val satisfied = problem.satisfies(it)
                    if (satisfied) totalSatisfied++
                    totalEvaluated++
                    satisfied
                }
    }

    override fun optimizeOrThrow(function: ObjectiveFunction, assumptions: Literals) = sequence(assumptions).minBy {
        function.value(it)
    } ?: throw UnsatisfiableException()

    private fun createRemap(assumptions: Literals): IntArray {
        val nbrVariables = problem.nbrVariables - assumptions.size
        return if (assumptions.isNotEmpty()) {
            val themap = IntArray(nbrVariables)
            var ix = 0
            val taken = IntSet(assumptions.size * 2)
            assumptions.forEach { taken.add(it.toIx()) }
            for (i in 0 until nbrVariables) {
                while (taken.contains(ix)) ix++
                themap[i] = ix++
            }
            themap
        } else EMPTY_INT_ARRAY
    }

    private fun remapLabeling(assumptions: Literals, labeling: Labeling, remap: IntArray): Labeling {
        return if (assumptions.isNotEmpty()) {
            val result = this.labelingFactory.create(problem.nbrVariables)
            result.setAll(assumptions)
            for (i in labeling.indices) {
                result[remap[i]] = labeling[i]
            }
            result
        } else labeling
    }
}


