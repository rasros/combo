package combo.math

import combo.util.IntList
import combo.util.assert
import kotlin.random.Random

interface DiscretePdfSampler {
    fun sample(rng: Random): Int
}

/**
 * Implementation of Vose's Alias Method for sampling from a discrete PDF in constant time.
 */
class AliasMethodSampler(probs: FloatArray) : DiscretePdfSampler {
    private val U = FloatArray(probs.size) // Probability table
    private val K = IntArray(probs.size) // Alias table

    init {
        assert(probs.isNotEmpty())
        val n = probs.size

        val underfull = IntList()
        val overfull = IntList()
        for ((i, prob) in probs.withIndex()) {
            U[i] = n * prob
            if (U[i] < 1.0f) underfull.add(i)
            else overfull.add(i)
        }

        while (underfull.size > 0 && overfull.size > 0) {
            val under = underfull.removeAt(underfull.size - 1)
            val over = overfull.removeAt(overfull.size - 1)
            K[under] = over
            U[over] = (U[over] + U[under]) - 1.0f
            if (U[over] < 1.0f) underfull.add(over)
            else overfull.add(over)
        }
        // There may be some left over elements in either list due to rounding errors, they are safely ignored
    }

    override fun sample(rng: Random): Int {
        val i = rng.nextInt(K.size)
        return if (rng.nextDouble() < U[i]) i
        else K[i]
    }
}

/**
 * [probs] are required to be sorted in ascending order
 */
class BinarySearchSampler(private val probs: DoubleArray) : DiscretePdfSampler {
    override fun sample(rng: Random): Int {
        val n = probs.size
        val x = rng.nextDouble()
        if (probs.isEmpty() || x < probs[0]) return 0
        var low = 0
        var high = n - 1
        while (high - low > 1) {
            val mid = (low + high) ushr 1
            if (x > probs[mid]) low = mid
            else high = mid
        }
        return high
    }
}
