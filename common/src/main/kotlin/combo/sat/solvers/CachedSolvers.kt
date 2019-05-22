@file:JvmName("CachedSolvers")

package combo.sat.solvers

import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.util.IntCollection
import combo.util.RandomConcurrentBuffer
import combo.util.isEmpty
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.random.Random

/**
 * A cached solver reuses previous solutions, provided that there are previous instances that match the assumptions.
 * As with all solvers, this can be used in parallel by multiple threads.
 * Using a cache with multiple threads in parallel makes the solver non-deterministic even if a specific random seed
 * is used.
 */
class CachedSolver @JvmOverloads constructor(val baseSolver: Solver, maxSize: Int = 50) : Solver {

    override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            baseSolver.randomSeed = value
            field = value
        }
    private var rng: Random = Random(randomSeed)

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

    override fun witnessOrThrow(assumptions: IntCollection, guess: MutableInstance?): Instance {
        var failure: ValidationException? = null
        try {
            if (rng.nextFloat() < pNew)
                return baseSolver.witnessOrThrow(assumptions, guess).also { buffer.add(rng, it) }
        } catch (e: ValidationException) {
            failure = e
        }
        val c: Constraint = if (assumptions.isEmpty()) Tautology else Conjunction(assumptions)
        val l: Instance? = buffer.find { c.satisfies(it) }
        return if (l == null && failure == null)
            baseSolver.witnessOrThrow(assumptions, guess).also { buffer.add(rng, it) }
        else l ?: throw UnsatisfiableException("Failed to find matching fallback instance.", failure)
    }
}

/**
 * A cached optimizer reuses previous solutions, provided that there are previous instances that match the assumptions.
 * As with all solvers, this can be used in parallel by multiple threads.
 * Using a cache with multiple threads in parallel makes the solver non-deterministic even if a specific random seed
 * is used.
 */
class CachedOptimizer<in O : ObjectiveFunction> @JvmOverloads constructor(
        val baseOptimizer: Optimizer<O>, maxSize: Int = 50) : Optimizer<O> {

    override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            this.baseOptimizer.randomSeed = value
            field = value
        }
    private var rng: Random = Random(randomSeed)

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

    override fun optimizeOrThrow(function: O, assumptions: IntCollection, guess: MutableInstance?): Instance {
        val c: Constraint = if (assumptions.isEmpty()) Tautology else Conjunction(assumptions)
        var minV = Float.MAX_VALUE
        var best: Instance? = null
        var failure: ValidationException? = null

        if (rng.nextFloat() < pNew) {
            try {
                best = baseOptimizer.optimizeOrThrow(function, assumptions, guess)
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
            baseOptimizer.optimizeOrThrow(function, assumptions, guess).also { buffer.add(rng, it) }
        else best ?: throw UnsupportedOperationException("Failed to find matching fallback instance.", failure)
    }
}
