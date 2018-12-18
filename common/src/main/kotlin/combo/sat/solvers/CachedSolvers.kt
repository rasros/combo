@file:JvmName("CachedSolvers")

package combo.sat.solvers

import combo.math.IntPermutation
import combo.sat.*
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

class CachedSolver @JvmOverloads constructor(val baseSolver: Solver,
                                             maxSize: Int = 50,
                                             val pNew: Double = 0.0) : Solver {

    private val cache: Array<Labeling> = Array(maxSize) { config.labelingBuilder.build(0) }

    var size = 0
        private set

    override val config: SolverConfig
        get() = baseSolver.config

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        if (size < cache.size)
            return baseSolver.witnessOrThrow(assumptions).also { cache[size++] = it }
        val rng = config.nextRandom()
        if (rng.nextDouble() < pNew)
            return baseSolver.witnessOrThrow(assumptions).also { cache[rng.nextInt(size)] = it }

        val c = Conjunction(assumptions)
        return IntPermutation(size, rng).firstOrNull { c.satisfies(cache[it]) }?.let { cache[it] }
                ?: baseSolver.witnessOrThrow(assumptions)
                        .also { cache[rng.nextInt(cache.size)] = it }
    }
}

class CachedOptimizer<O : ObjectiveFunction> @JvmOverloads constructor(val baseOptimizer: Optimizer<O>,
                                                                       maxSize: Int = 50,
                                                                       val pNew: Double = 0.0) : Optimizer<O> {

    private val cache: Array<Labeling> = Array(maxSize) { config.labelingBuilder.build(0) }

    var size = 0
        private set

    override val config: SolverConfig
        get() = baseOptimizer.config

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        if (size < cache.size)
            return baseOptimizer.optimizeOrThrow(function, assumptions).also { cache[size++] = it }
        val rng = config.nextRandom()
        if (rng.nextDouble() < pNew)
            return baseOptimizer.optimizeOrThrow(function, assumptions).also { cache[rng.nextInt(size)] = it }

        val c = Conjunction(assumptions)
        return IntPermutation(size, rng)
                .filter { c.satisfies(cache[it]) }
                .maxBy { function.value(cache[it], config.maximize) }
                ?.let { cache[it] }
                ?: baseOptimizer.optimizeOrThrow(function, assumptions)
                        .also { cache[rng.nextInt(cache.size)] = it }
    }
}

class FallbackSolver @JvmOverloads constructor(val baseSolver: Solver, maxSize: Int = 10) : Solver {

    private val cache: Array<Labeling> = Array(maxSize) { config.labelingBuilder.build(0) }

    var size = 0
        private set

    override val config: SolverConfig
        get() = baseSolver.config

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        return try {
            baseSolver.witnessOrThrow(assumptions).also {
                if (size < cache.size) cache[size++] = it
                else cache[config.nextRandom().nextInt(size)] = it
            }
        } catch (e: ValidationException) {
            val c = Conjunction(assumptions)
            IntPermutation(size, config.nextRandom()).firstOrNull { c.satisfies(cache[it]) }?.let { cache[it] }
                    ?: throw UnsatisfiableException("Failed to find matching fallback labeling", e)
        }
    }
}

class FallbackOptimizer<O : ObjectiveFunction> @JvmOverloads constructor(val baseOptimizer: Optimizer<O>, maxSize: Int = 10) : Optimizer<O> {

    private val cache: Array<Labeling> = Array(maxSize) { config.labelingBuilder.build(0) }

    var size = 0
        private set

    override val config: SolverConfig
        get() = baseOptimizer.config

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        val rng = config.nextRandom()
        val c = Conjunction(assumptions)
        val cached = IntPermutation(size, rng)
                .filter { c.satisfies(cache[it]) }
                .maxBy { function.value(cache[it], config.maximize) }
                ?.let { cache[it] }
        return try {
            val labeling = baseOptimizer.optimizeOrThrow(function, assumptions)
            if (cached == null) return labeling
            val s1 = function.value(labeling, config.maximize)
            val s2 = function.value(cached, config.maximize)
            if (s1 > s2) labeling else cached
        } catch (e: ValidationException) {
            cached ?: throw UnsatisfiableException("Failed to find matching fallback labeling", e)
        }
    }
}
