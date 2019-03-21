package combo.math

import combo.util.assert
import kotlin.random.Random

/**
 * Using format-preserving encryption
 */
class IntPermutation(val size: Int = Int.MAX_VALUE, rng: Random) : Iterable<Int> {

    private val mask: Int // bit mask for block
    // 0 < size <= mask+1  and mask+1 is a power of 2
    private val rish: Int                 // right shift count
    private val rk1 = rng.nextInt()
    private val rk2 = rng.nextInt()
    private val rk3 = rng.nextInt()

    init {
        var i = 8
        var j = 3
        while (j < 31 && i < size) {
            i += i
            j++
        }
        this.mask = i - 1
        this.rish = j * 3 / 7
    }

    fun encode(value: Int): Int {
        assert(value in 0 until size)
        var x = value
        do {
            x = (x * 0x7f4a7c15 + rk1) and mask
            x = x xor (x ushr rish)
            x = (x * 0x7f4a7c15 + rk2) and mask
            x = x xor (x ushr rish)
            x = (x * 0x7f4a7c15 + rk3) and mask
            x = x xor (x ushr rish)
        } while (x >= this.size)
        return x
    }

    override fun iterator(): IntIterator {
        var i = 0
        return object : IntIterator() {
            override fun hasNext() = i < size
            override fun nextInt() = encode(i++)
        }
    }
}

class LongPermutation(val size: Long = Long.MAX_VALUE, rng: Random) : Iterable<Long> {

    private val mask: Long // bit mask for block
    // 0 < size <= mask+1  and mask+1 is a power of 2
    private val rish: Int                 // right shift count
    private val rk1 = rng.nextLong()
    private val rk2 = rng.nextLong()
    private val rk3 = rng.nextLong()
    private val rk4 = rng.nextLong()
    private val rk5 = rng.nextLong()

    init {
        var i = 8L
        var j = 3
        while (j < 63 && i < size) {
            i += i
            ++j
        }
        this.mask = i - 1L
        this.rish = j * 3 / 7
    }

    fun encode(value: Long): Long {
        assert(value in 0 until size)
        var x = value
        do {
            x = (x * 0x3a8f05c5 + rk1) and mask
            x = x xor (x ushr rish)
            x = (x * 0x3a8f05c5 + rk2) and mask
            x = x xor (x ushr rish)
            x = (x * 0x3a8f05c5 + rk3) and mask
            x = x xor (x ushr rish)
            x = (x * 0x3a8f05c5 + rk4) and mask
            x = x xor (x ushr rish)
            x = (x * 0x3a8f05c5 + rk5) and mask
            x = x xor (x ushr rish)
        } while (x >= this.size)
        return x
    }

    override fun iterator(): LongIterator {
        var i = 0L
        return object : LongIterator() {
            override fun hasNext() = i < size
            override fun nextLong() = encode(i++)
        }
    }
}