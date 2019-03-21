package combo.util

import combo.math.IntPermutation
import kotlin.random.Random

/**
 * Only map is implemented in [MutableIntCollection] and it only works for linear functions.
 */
class IntRangeSet(val min: Int, val max: Int) : IntCollection {

    init {
        assert(min <= max)
    }

    override var size: Int = kotlin.math.max(0, 1 + (max - min))
        private set

    override fun contains(ix: Int): Boolean = ix in min..max

    override fun toArray(): IntArray {
        val array = IntArray(size)
        var k = 0
        for (i in this)
            array[k++] = i
        return array
    }

    override fun map(transform: (Int) -> Int): IntRangeSet {
        val min1 = transform.invoke(min)
        val max1 = transform.invoke(max)
        return if (min1 < max1) IntRangeSet(min1, max1)
        else IntRangeSet(max1, min1)
    }

    override fun iterator() = (min..max).iterator()

    override fun permutation(rng: Random): IntIterator {
        var i = 0
        val perm = IntPermutation(size, rng)
        return object : IntIterator() {
            override fun hasNext() = i < size
            override fun nextInt(): Int {
                if (i >= size) throw NoSuchElementException()
                return min + perm.encode(i++)
            }
        }
    }

    override fun random(rng: Random): Int {
        if (isEmpty()) throw NoSuchElementException()
        return min + rng.nextInt(size)
    }

    override fun copy() = IntRangeSet(min, max)

    override fun toString() = "IntRangeSet($min..$max)"
}