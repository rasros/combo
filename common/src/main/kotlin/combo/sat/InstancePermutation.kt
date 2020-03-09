package combo.sat

import combo.math.CyclingHashIntPermutation
import combo.util.AtomicInt
import kotlin.math.max
import kotlin.random.Random

/**
 * This class iterates over an instance in a random order without repetitions.
 */
class InstancePermutation constructor(
        private val nbrVariables: Int, val factory: InstanceFactory, rng: Random) : Iterator<Instance> {

    private val permutation: Array<CyclingHashIntPermutation>
    private var count: AtomicInt = AtomicInt(0)
    private val lastBits = nbrVariables % 31

    init {
        val size = (nbrVariables + 30) / 31
        val last = 1 shl lastBits
        this.permutation = Array(size) { i ->
            if (i == size - 1 && last != 1) CyclingHashIntPermutation(last, rng) else CyclingHashIntPermutation(Int.MAX_VALUE, rng)
        }
    }

    private val limit = if (nbrVariables > 30) Int.MAX_VALUE else max(1, 2 shl nbrVariables - 1)

    override fun hasNext() = count.get() < limit

    override fun next(): Instance {
        val instance = factory.create(nbrVariables)
        val c = count.getAndIncrement()
        var ix = 0
        for (perm in permutation) {
            val value = perm.encode(c and perm.size - 1)
            if (perm.size == Int.MAX_VALUE) instance.setBits(ix, 31, value)
            else instance.setBits(ix, lastBits, value)
            ix += 31
        }
        return instance
    }
}
