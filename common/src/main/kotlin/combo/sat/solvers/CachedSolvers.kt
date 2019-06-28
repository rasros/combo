@file:JvmName("CachedSolvers")

package combo.sat.solvers

import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.util.*
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * A cached solver reuses previous solutions, provided that there are previous instances that match the assumptions.
 * As with all solvers, this can be used in parallel by multiple threads.
 * Using a cache with multiple threads in parallel makes the solver non-deterministic even if a specific random seed
 * is used.
 */
class CachedSolver @JvmOverloads constructor(val baseSolver: Solver, maxSize: Int = 20) : Solver {

    override val randomSeed get() = baseSolver.randomSeed
    override val timeout get() = baseSolver.timeout
    private var randomSequence = RandomSequence(nanos().toInt())

    /**
     * Chance of generating new instance regardless of whether there are any instances matching the assumptions.
     */
    var pNew: Float = 0.0f

    private val buffer = RandomCache<Instance>(maxSize)

    override fun witnessOrThrow(assumptions: IntCollection, guess: MutableInstance?): Instance {
        val rng = randomSequence.next()
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

    override fun asSequence(assumptions: IntCollection) = baseSolver.asSequence(assumptions)
}

/**
 * A cached optimizer reuses previous solutions, provided that there are previous instances that match the assumptions.
 * As with all solvers, this can be used in parallel by multiple threads.
 * Using a cache with multiple threads in parallel makes the solver non-deterministic even if a specific random seed
 * is used.
 */
class CachedOptimizer<in O : ObjectiveFunction> @JvmOverloads constructor(
        val baseOptimizer: Optimizer<O>, maxSize: Int = 20) : Optimizer<O> {

    override val randomSeed get() = baseOptimizer.randomSeed
    override val timeout get() = baseOptimizer.timeout
    private var randomSequence = RandomSequence(nanos().toInt())

    /**
     * Chance of generating new instance regardless of whether there are any instances matching the assumptions.
     */
    var pNew: Float = 0.0f

    /**
     * Chance of generating new instance regardless of whether there are any instances matching the assumptions, using
     * a guess initial solution randomly selected from a matching instance.
     */
    var pNewWithGuess: Float = 0.1f

    private val buffer = RandomCache<Instance>(maxSize)

    override fun optimizeOrThrow(function: O, assumptions: IntCollection, guess: MutableInstance?): Instance {
        val c: Constraint = if (assumptions.isEmpty()) Tautology else Conjunction(assumptions)
        val rng = randomSequence.next()
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

        if (minV > function.lowerBound() && best != null && rng.nextFloat() < pNewWithGuess) {
            try {
                val guessed = baseOptimizer.optimizeOrThrow(function, assumptions, guess
                        ?: best!!.copy() as MutableInstance?)
                val v = function.value(guessed)
                if (v != minV)
                    buffer.add(rng, guessed)
                if (v < minV)
                    best = guessed
            } catch (e: ValidationException) {
                failure = e
            }
        }
        return if (best == null && failure == null)
            baseOptimizer.optimizeOrThrow(function, assumptions, guess).also { buffer.add(rng, it) }
        else best ?: throw UnsupportedOperationException("Failed to find matching fallback instance.", failure)
    }
}
