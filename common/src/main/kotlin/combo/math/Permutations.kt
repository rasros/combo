package combo.math

import kotlin.random.Random

private const val INT_ROUNDS = 4
private const val LONG_ROUNDS = 7

class IntPermutation(val size: Int = Int.MAX_VALUE, rng: Random = Random.Default) : Iterable<Int> {

    private val mask: Int // bit mask for block
    // 0 < size <= mask+1  and mask+1 is a power of 2
    private val rish: Int                 // right shift count
    private val rk = IntArray(INT_ROUNDS)    // rk for each round

    init {
        require(size > 0)
        var i = 8
        var j = 3
        while (j < 31 && i < size) {
            i += i
            ++j
        }
        this.mask = i - 1
        this.rish = j * 3 / 7

        var r = INT_ROUNDS
        do {
            rk[--r] = rng.nextInt()
        } while (r != 0)
    }

    fun encode(value: Int): Int {
        require(value >= 0)
        require(value < size)
        var x = value
        do {
            var r = INT_ROUNDS
            do {
                x = (x * 0xADB + this.rk[--r]) and this.mask;
                x = x xor x.ushr(this.rish)
            } while (r != 0)
        } while (x >= this.size)
        return x
    }

    override fun iterator(): IntIterator {
        val itr = IntRange(0, size - 1).iterator()
        return object : IntIterator() {
            override fun hasNext() = itr.hasNext()
            override fun nextInt() = encode(itr.nextInt())
        }
    }
}

class LongPermutation(val size: Long = Long.MAX_VALUE, rng: Random = Random.Default) : Iterable<Long> {
    private val mask: Long // bit mask for block
    // 0 < size <= mask+1  and mask+1 is a power of 2
    private val rish: Int                 // right shift count
    private val rk = LongArray(LONG_ROUNDS)    // rk for each round

    init {
        require(size > 0)
        var i = 8L
        var j = 3
        while (j < 63 && i < size) {
            i += i
            ++j
        }
        this.mask = i - 1L
        this.rish = j * 3 / 7

        var r = LONG_ROUNDS
        do {
            rk[--r] = rng.nextLong()
        } while (r != 0)
    }

    fun encode(value: Long): Long {
        var x = value
        do {
            var r = LONG_ROUNDS
            do {
                //x = (x * 0xADB + this.rk[--r]) & this.mask;
                x = x * 0x3A8F05C5 + this.rk[--r] and this.mask
                x = x xor x.ushr(this.rish)
            } while (r != 0)
        } while (x >= this.size)
        return x
    }

    override fun iterator(): LongIterator {
        val itr = LongRange(0, size - 1).iterator()
        return object : LongIterator() {
            override fun hasNext() = itr.hasNext()
            override fun nextLong() = encode(itr.nextLong())
        }
    }
}