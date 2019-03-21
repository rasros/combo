package combo.sat

import combo.math.LongPermutation
import combo.util.AtomicLong
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * This class iterates over an instance in a random order without repetitions.
 */
class InstancePermutation constructor(
        private val nbrVariables: Int, val factory: InstanceFactory, rng: Random) : Iterator<MutableInstance> {

    private val permutation: Array<LongPermutation>
    private var count: AtomicLong = AtomicLong(0)
    private val lastBits = nbrVariables % 63

    init {
        val size = (nbrVariables + 62) / 63
        val last = 1L shl lastBits
        this.permutation = Array(size) { i ->
            if (i == size - 1 && last != 1L) LongPermutation(last, rng) else LongPermutation(Long.MAX_VALUE, rng)
        }
    }

    private val limit = max(1, 2L shl nbrVariables - 1)

    override fun hasNext() = count.get() < limit

    override fun next(): MutableInstance {
        val instance = factory.create(nbrVariables)
        val c = count.inc()
        var ix = 0
        for (perm in permutation) {
            val value = perm.encode(c)
            val lower = value.toInt()
            val upper = (value ushr 32).toInt()
            if (perm.size == Long.MAX_VALUE) {
                instance.setBits(ix, 32, lower)
                instance.setBits(ix + 32, 31, upper)
            } else {
                instance.setBits(ix, min(32, lastBits), lower)
                if (upper != 0) instance.setBits(ix + 32, lastBits - 32, upper)
            }
            ix += 63
        }
        return instance
    }
}
