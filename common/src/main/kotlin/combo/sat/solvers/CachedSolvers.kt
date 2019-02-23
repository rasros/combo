@file:JvmName("CachedSolvers")

package combo.sat.solvers

import combo.math.RandomSequence
import combo.sat.*
import combo.util.ConcurrentCache
import combo.util.collectionOf
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * A cached solver reuses previous solutions, provided that there are previous instances that match the assumptions.
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
    var pNew: Double = 0.0

    private val cache = ConcurrentCache<Instance>(maxSize)
    private var randomSequence = RandomSequence(nanos())

    override fun witnessOrThrow(assumptions: Literals): Instance {
        val rng = randomSequence.next()
        if (rng.nextDouble() < pNew)
            return baseSolver.witnessOrThrow(assumptions).also { cache.add(rng, it) }
        val c = Conjunction(collectionOf(assumptions))
        return cache.get(rng, { c.satisfies(it) }, { baseSolver.witnessOrThrow(assumptions) })
    }
}

/**
 * A cached optimizer reuses previous solutions, provided that there are previous instances that match the assumptions.
 * @param problem
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
    var pNew: Double = 0.0

    private val cache = ConcurrentCache<Instance>(maxSize)
    private var randomSequence = RandomSequence(nanos())

    var size = 0
        private set

    override fun optimizeOrThrow(function: O, assumptions: Literals): Instance {
        val rng = randomSequence.next()
        if (rng.nextDouble() < pNew)
            return baseOptimizer.optimizeOrThrow(function, assumptions).also { cache.add(rng, it) }

        val c = Conjunction(collectionOf(assumptions))
        var minV = Double.MAX_VALUE
        var best: Instance? = null
        cache.forEach {
            if (c.satisfies(it)) {
                val v = function.value(it)
                if (v < minV) {
                    minV = v
                    best = it
                }
            }
        }
        return best ?: baseOptimizer.optimizeOrThrow(function, assumptions).also { cache.add(rng, it) }
    }
}

/**
 * This uses a fallback in case solving fails.
 */
class FallbackSolver @JvmOverloads constructor(val baseSolver: Solver, maxSize: Int = 10) : Solver {

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

    private var randomSequence = RandomSequence(nanos())
    private val cache: ConcurrentCache<Instance> = ConcurrentCache(maxSize)

    override fun witnessOrThrow(assumptions: Literals): Instance {
        return try {
            baseSolver.witnessOrThrow(assumptions).also {
                cache.add(randomSequence.next(), it)
            }
        } catch (e: ValidationException) {
            val c = Conjunction(collectionOf(assumptions))
            var l: Instance? = null
            cache.forEach {
                if (c.satisfies(it)) l = it
            }
            return l ?: throw UnsatisfiableException("Failed to find matching fallback instance", e)
        }
    }
}

/**
 * This uses a fallback in case optimizing fails.
 */
class FallbackOptimizer<in O : ObjectiveFunction> @JvmOverloads constructor(
        val baseOptimizer: Optimizer<O>, maxSize: Int = 10) : Optimizer<O> {

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

    private var randomSequence = RandomSequence(nanos())
    private val cache: ConcurrentCache<Instance> = ConcurrentCache(maxSize)

    override fun optimizeOrThrow(function: O, assumptions: Literals): Instance {
        val c = Conjunction(collectionOf(assumptions))
        val cached: Instance? = let {
            var best: Instance? = null
            var minV = Double.MAX_VALUE
            cache.forEach {
                if (c.satisfies(it)) {
                    val v = function.value(it)
                    if (v < minV) {
                        minV = v
                        best = it
                    }
                }
            }
            best
        }
        return try {
            val instance = baseOptimizer.optimizeOrThrow(function, assumptions)
            if (cached == null) return instance
            val s1 = function.value(instance)
            val s2 = function.value(cached)
            if (s1 < s2) instance.also { cache.add(randomSequence.next(), it) }
            else cached
        } catch (e: ValidationException) {
            cached ?: throw UnsatisfiableException("Failed to find matching fallback instance", e)
        }
    }
}
