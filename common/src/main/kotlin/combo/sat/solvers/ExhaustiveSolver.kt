package combo.sat.solvers

import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.util.*

/**
 * This [Solver] and [Optimizer] uses brute force. It can only solve small and easy problems.
 * @param problem the problem contains the [Constraint]s and the number of variables.
 */
class ExhaustiveSolver(val problem: Problem) : Solver, Optimizer<ObjectiveFunction> {

    override var randomSeed: Int
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.randomSeed
    private var randomSequence = RandomSequence(nanos().toInt())

    override var timeout: Long = -1L

    /**
     * If true then perform unit propagation before solving when assumptions are used.
     * This will sometimes drastically reduce the number of variables and make it possible to solve using brute force.
     */
    var propagateAssumptions: Boolean = true

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [SparseBitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceBuilder: InstanceBuilder = BitArrayBuilder

    /**
     * The [guess] is used only if it satisfies all constraints.
     */
    override fun witnessOrThrow(assumptions: IntCollection, guess: MutableInstance?): Instance {
        val propAssumptions = propAssumptions(assumptions)
        if (guess != null && (propAssumptions.isEmpty() || Conjunction(propAssumptions).satisfies(guess)) && problem.satisfies(guess))
            return guess
        val remap = createRemap(propAssumptions)
        val nbrVariables = problem.nbrVariables - propAssumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return InstancePermutation(nbrVariables, instanceBuilder, randomSequence.next())
                .asSequence()
                .takeWhile { millis() <= end }
                .map { remapInstance(propAssumptions, it, remap) }
                .filter { problem.satisfies(it) }
                .firstOrNull() ?: throw if (millis() > end) TimeoutException(timeout) else UnsatisfiableException()
    }

    override fun asSequence(assumptions: IntCollection): Sequence<Instance> {
        val propAssumptions = try {
            propAssumptions(assumptions)
        } catch (e: UnsatisfiableException) {
            return emptySequence()
        }
        val remap = createRemap(propAssumptions)
        val nbrVariables = problem.nbrVariables - propAssumptions.size
        val end = if (timeout > 0) millis() + timeout else Long.MAX_VALUE
        return InstancePermutation(nbrVariables, instanceBuilder, randomSequence.next())
                .asSequence()
                .takeWhile { millis() <= end }
                .map { remapInstance(propAssumptions, it, remap) }
                .filter { problem.satisfies(it) }
    }

    override fun optimizeOrThrow(function: ObjectiveFunction, assumptions: IntCollection, guess: MutableInstance?) = asSequence(assumptions).minBy {
        function.value(it)
    } ?: throw UnsatisfiableException()

    private fun propAssumptions(assumptions: IntCollection): IntCollection {
        return if (propagateAssumptions && assumptions.isNotEmpty()) {
            val units = IntHashSet()
            units.addAll(assumptions)
            problem.unitPropagation(units)
            units
        } else {
            assumptions
        }
    }

    override fun isComplete() = true

    private fun createRemap(assumptions: IntCollection): IntArray {
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

    private fun remapInstance(assumptions: IntCollection, instance: Instance, remap: IntArray): Instance {
        return if (assumptions.isNotEmpty()) {
            val result = this.instanceBuilder.create(problem.nbrVariables)
            result.setAll(assumptions)
            for (i in instance.indices) {
                result[remap[i]] = instance[i]
            }
            result
        } else instance
    }
}

