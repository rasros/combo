@file:JvmName("CachedSolvers")

package combo.sat.solvers

import combo.math.IntPermutation
import combo.math.RandomSequence
import combo.sat.*
import combo.util.collectionOf
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

class CachedSolver @JvmOverloads constructor(val baseSolver: Solver,
                                             val maxSize: Int = 50,
                                             val randomSeed: Long = nanos(),
                                             val labelingFactory: LabelingFactory = BitFieldLabelingFactory,
                                             val pNew: Double = 0.0) : Solver {

    private val cache: Array<Labeling> = Array(maxSize) { labelingFactory.create(0) }
    private val randomSequence = RandomSequence(randomSeed)

    var size = 0
        private set

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        if (size < cache.size)
            return baseSolver.witnessOrThrow(assumptions).also { cache[size++] = it }
        val rng = randomSequence.next()
        if (rng.nextDouble() < pNew)
            return baseSolver.witnessOrThrow(assumptions).also { cache[rng.nextInt(size)] = it }

        val c = Conjunction(collectionOf(assumptions))
        return IntPermutation(size, rng).firstOrNull { c.satisfies(cache[it]) }?.let { cache[it] }
                ?: baseSolver.witnessOrThrow(assumptions)
                        .also { cache[rng.nextInt(cache.size)] = it }
    }
}

class CachedOptimizer<in O : ObjectiveFunction> @JvmOverloads constructor(val baseOptimizer: Optimizer<O>,
                                                                       val maxSize: Int = 50,
                                                                       val randomSeed: Long = nanos(),
                                                                       val labelingFactory: LabelingFactory = BitFieldLabelingFactory,
                                                                       val pNew: Double = 0.0) : Optimizer<O> {

    private val cache: Array<Labeling> = Array(maxSize) { labelingFactory.create(0) }
    private val randomSequence = RandomSequence(randomSeed)

    var size = 0
        private set

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        if (size < cache.size)
            return baseOptimizer.optimizeOrThrow(function, assumptions).also { cache[size++] = it as MutableLabeling }
        val rng = randomSequence.next()
        if (rng.nextDouble() < pNew)
            return baseOptimizer.optimizeOrThrow(function, assumptions).also { cache[rng.nextInt(size)] = it as MutableLabeling }

        val c = Conjunction(collectionOf(assumptions))
        return IntPermutation(size, rng)
                .filter { c.satisfies(cache[it]) }
                .minBy { function.value(cache[it]) }
                ?.let { cache[it] }
                ?: baseOptimizer.optimizeOrThrow(function, assumptions)
                        .also { cache[rng.nextInt(cache.size)] = it as MutableLabeling }
    }
}

class FallbackSolver @JvmOverloads constructor(val baseSolver: Solver,
                                               randomSeed: Long = nanos(),
                                               val labeling: LabelingFactory = BitFieldLabelingFactory,
                                               maxSize: Int = 10) : Solver {

    private val cache: Array<Labeling> = Array(maxSize) { labeling.create(0) }
    private val randomSequence = RandomSequence(randomSeed)

    var size = 0
        private set

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        return try {
            baseSolver.witnessOrThrow(assumptions).also {
                if (size < cache.size) cache[size++] = it
                else cache[randomSequence.next().nextInt(size)] = it
            }
        } catch (e: ValidationException) {
            val c = Conjunction(collectionOf(assumptions))
            IntPermutation(size, randomSequence.next()).firstOrNull { c.satisfies(cache[it]) }?.let { cache[it] }
                    ?: throw UnsatisfiableException("Failed to find matching fallback labeling", e)
        }
    }
}

class FallbackOptimizer<in O : ObjectiveFunction> @JvmOverloads constructor(val baseOptimizer: Optimizer<O>,
                                                                         val randomSeed: Long = nanos(),
                                                                         val labelingFactory: LabelingFactory = BitFieldLabelingFactory,
                                                                         val maxSize: Int = 10) : Optimizer<O> {

    private val cache: Array<Labeling> = Array(maxSize) { labelingFactory.create(0) }
    private val randomSequence = RandomSequence(randomSeed)

    var size = 0
        private set

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        val rng = randomSequence.next()
        val c = Conjunction(collectionOf(assumptions))
        val cached = IntPermutation(size, rng)
                .filter { c.satisfies(cache[it]) }
                .minBy { function.value(cache[it]) }
                ?.let { cache[it] }
        return try {
            val labeling = baseOptimizer.optimizeOrThrow(function, assumptions)
            if (cached == null) return labeling
            val s1 = function.value(labeling as MutableLabeling)
            val s2 = function.value(cached)
            if (s1 > s2) labeling else cached
        } catch (e: ValidationException) {
            cached ?: throw UnsatisfiableException("Failed to find matching fallback labeling", e)
        }
    }
}
