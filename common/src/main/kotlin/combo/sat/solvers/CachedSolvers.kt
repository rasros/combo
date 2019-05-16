@file:JvmName("CachedSolvers")

package combo.sat.solvers

import combo.math.RandomSequence
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.util.RandomConcurrentBuffer
import combo.util.collectionOf
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * A cached solver reuses previous solutions, provided that there are previous instances that match the assumptions.
 * As with all solvers, this can be used in parallel by multiple threads.
 */
class CachedSolver @JvmOverloads constructor(val baseSolver: Solver, maxSize: Int = 50) : Solver {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed

    override var timeout: Long
        get() = baseSolver.timeout
        set(value) {
            baseSolver.timeout = value
        }

    /**
     * Chance of generating new instance regardless of whether there are any instances matching the assumptions.
     */
    var pNew: Float = 0.0f

    private val buffer = RandomConcurrentBuffer<Instance>(maxSize)
    private var randomSequence = RandomSequence(nanos())

    override fun witnessOrThrow(assumptions: Literals): Instance {
        val rng = randomSequence.next()
        var failure: ValidationException? = null
        try {
            if (rng.nextFloat() < pNew)
                return baseSolver.witnessOrThrow(assumptions).also { buffer.add(rng, it) }
        } catch (e: ValidationException) {
            failure = e
        }
        val c: Constraint = if (assumptions.isEmpty()) Tautology else Conjunction(collectionOf(*assumptions))
        val l: Instance? = buffer.find { c.satisfies(it) }
        return if (l == null && failure == null)
            baseSolver.witnessOrThrow(assumptions).also { buffer.add(rng, it) }
        else l ?: throw UnsatisfiableException("Failed to find matching fallback instance.", failure)
    }
}

/**
 * A cached optimizer reuses previous solutions, provided that there are previous instances that match the assumptions.
 * As with all solvers, this can be used in parallel by multiple threads.
 */
class CachedOptimizer<in O : ObjectiveFunction> @JvmOverloads constructor(
        val baseOptimizer: Optimizer<O>, maxSize: Int = 50) : Optimizer<O> {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed

    override var timeout: Long
        get() = baseOptimizer.timeout
        set(value) {
            baseOptimizer.timeout = value
        }

    /**
     * Chance of generating new instance regardless of whether there are any instances matching the assumptions.
     */
    var pNew: Float = 0.0f

    private val buffer = RandomConcurrentBuffer<Instance>(maxSize)
    private var randomSequence = RandomSequence(nanos())

    override fun optimizeOrThrow(function: O, assumptions: Literals): Instance {
        val rng = randomSequence.next()

        val c: Constraint = if (assumptions.isEmpty()) Tautology else Conjunction(collectionOf(*assumptions))
        var minV = Float.MAX_VALUE
        var best: Instance? = null
        var failure: ValidationException? = null

        if (rng.nextFloat() < pNew) {
            try {
                best = baseOptimizer.optimizeOrThrow(function, assumptions)
                buffer.add(rng, best)
                minV = function.value(best)
            } catch (e: ValidationException) {
                failure = e
            }
        }

        buffer.forEach {
            if (c.satisfies(it)) {
                val v = function.value(it)
                if (v < minV) {
                    minV = v
                    best = it
                }
            }
        }
        return if (best == null && failure == null)
            baseOptimizer.optimizeOrThrow(function, assumptions).also { buffer.add(rng, it) }
        else best ?: throw UnsupportedOperationException("Failed to find matching fallback instance.", failure)
    }
}
