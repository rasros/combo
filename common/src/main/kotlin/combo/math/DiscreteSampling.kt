package combo.math

import combo.util.IntArrayList
import combo.util.assert
import kotlin.random.Random

interface DiscreteSampler {
    fun sample(rng: Random): Int
}

/**
 * Implementation of Vose's Alias Method for sampling from a discrete PMF in constant time.
 */
class AliasMethodSampler(probabilities: FloatArray) : DiscreteSampler {
    private val U = FloatArray(probabilities.size) // Probability table
    private val K = IntArray(probabilities.size) { it } // Alias table

    init {
        assert(probabilities.isNotEmpty())
        val n = probabilities.size

        val underfull = IntArrayList()
        val overfull = IntArrayList()
        for ((i, prob) in probabilities.withIndex()) {
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
 * [probabilities] are required to be sorted in ascending order
 */
class BinarySearchSampler(private val probabilities: DoubleArray) : DiscreteSampler {
    override fun sample(rng: Random): Int {
        val n = probabilities.size
        val x = rng.nextDouble()
        if (probabilities.isEmpty() || x < probabilities[0]) return 0
        var low = 0
        var high = n - 1
        while (high - low > 1) {
            val mid = (low + high) ushr 1
            if (x > probabilities[mid]) low = mid
            else high = mid
        }
        return high
    }
}
