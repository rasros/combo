package combo.sat.solvers

import combo.math.RandomSequence
import combo.sat.*
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntHashSet
import combo.util.millis
import combo.util.nanos

/**
 * This [Solver] and [Optimizer] uses brute force. It can only solve small and easy problems.
 * @param problem the problem contains the [Constraint]s and the number of variables.
 */
class ExhaustiveSolver(val problem: Problem) : Solver, Optimizer<ObjectiveFunction> {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
    override var timeout: Long = -1L

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [IntSetInstanceFactory] otherwise [BitArrayFactory].
     */
    var instanceFactory: InstanceFactory = BitArrayFactory

    private var randomSequence = RandomSequence(nanos())

    override fun witnessOrThrow(assumptions: Literals): Instance {
        val remap = createRemap(assumptions)
        val nbrVariables = problem.nbrVariables - assumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return InstancePermutation(nbrVariables, instanceFactory, randomSequence.next())
                .asSequence()
                .map { if (millis() <= end) it else throw TimeoutException(timeout) }
                .map { remapInstance(assumptions, it, remap) }
                .firstOrNull { problem.satisfies(it) } ?: throw UnsatisfiableException()
    }

    override fun sequence(assumptions: Literals): Sequence<Instance> {
        val remap = createRemap(assumptions)
        val nbrVariables = problem.nbrVariables - assumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return InstancePermutation(nbrVariables, instanceFactory, randomSequence.next())
                .asSequence()
                .takeWhile { millis() <= end }
                .map { remapInstance(assumptions, it, remap) }
                .filter { problem.satisfies(it) }
    }

    override fun optimizeOrThrow(function: ObjectiveFunction, assumptions: Literals) = sequence(assumptions).minBy {
        function.value(it)
    } ?: throw UnsatisfiableException()

    private fun createRemap(assumptions: Literals): IntArray {
        val nbrVariables = problem.nbrVariables - assumptions.size
        return if (assumptions.isNotEmpty()) {
            val themap = IntArray(nbrVariables)
            var ix = 0
            val taken = IntHashSet(assumptions.size * 2)
            assumptions.forEach { taken.add(it.toIx()) }
            for (i in 0 until nbrVariables) {
                while (taken.contains(ix)) ix++
                themap[i] = ix++
            }
            themap
        } else EMPTY_INT_ARRAY
    }

    private fun remapInstance(assumptions: Literals, instance: Instance, remap: IntArray): Instance {
        return if (assumptions.isNotEmpty()) {
            val result = this.instanceFactory.create(problem.nbrVariables)
            result.setAll(assumptions)
            for (i in instance.indices) {
                result[remap[i]] = instance[i]
            }
            result
        } else instance
    }
}

