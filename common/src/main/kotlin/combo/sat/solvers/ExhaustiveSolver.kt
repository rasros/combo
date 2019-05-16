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
     * If true then perform unit propagation before solving when assumptions are used.
     */
    var propagateAssumptions: Boolean = true

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [SparseBitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceFactory: InstanceBuilder = BitArrayBuilder

    private var randomSequence = RandomSequence(nanos())

    override fun witnessOrThrow(assumptions: Literals): Instance {
        val propAssumptions = propAssumptions(assumptions)
        val remap = createRemap(propAssumptions)
        val nbrVariables = problem.nbrVariables - propAssumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return InstancePermutation(nbrVariables, instanceFactory, randomSequence.next())
                .asSequence()
                .takeWhile { millis() <= end }
                .map { remapInstance(propAssumptions, it, remap) }
                .filter { problem.satisfies(it) }
                .firstOrNull() ?: throw if (millis() > end) TimeoutException(timeout) else UnsatisfiableException()
    }

    override fun asSequence(assumptions: Literals): Sequence<Instance> {
        val propAssumptions = try {
            propAssumptions(assumptions)
        } catch (e: UnsatisfiableException) {
            return emptySequence()
        }
        val remap = createRemap(propAssumptions)
        val nbrVariables = problem.nbrVariables - propAssumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return InstancePermutation(nbrVariables, instanceFactory, randomSequence.next())
                .asSequence()
                .takeWhile { millis() <= end }
                .map { remapInstance(propAssumptions, it, remap) }
                .filter { problem.satisfies(it) }
    }

    override fun optimizeOrThrow(function: ObjectiveFunction, assumptions: Literals) = asSequence(assumptions).minBy {
        function.value(it)
    } ?: throw UnsatisfiableException()

    private fun propAssumptions(assumptions: Literals): Literals {
        return if (propagateAssumptions && assumptions.isNotEmpty()) {
            val units = IntHashSet()
            units.addAll(assumptions)
            problem.unitPropagation(units)
            units.toArray()
        } else {
            assumptions
        }
    }

    private fun createRemap(assumptions: Literals): IntArray {
        if (assumptions.isEmpty()) return EMPTY_INT_ARRAY
        val nbrVariables = problem.nbrVariables - assumptions.size
        val themap = IntArray(nbrVariables)
        var ix = 0
        val taken = IntHashSet(assumptions.size * 2, nullValue = -1)
        assumptions.forEach { taken.add(it.toIx()) }
        for (i in 0 until nbrVariables) {
            while (taken.contains(ix)) ix++
            themap[i] = ix++
        }
        return themap
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

