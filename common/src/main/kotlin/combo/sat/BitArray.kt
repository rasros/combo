package combo.sat

import combo.util.assert
import combo.util.bitCount

object BitArrayFactory : InstanceFactory {
    override fun create(size: Int) = BitArray(size)
}

/**
 * This uses a dense int array as backing for [Instance]. 32 bit ints are used instead of 64 bits due to JavaScript
 * interoperability.
 */
class BitArray constructor(override val size: Int, val field: IntArray) : MutableInstance {

    // Note this code uses a lot of bit shifts. The most common being masking by 0x1F and shifting right by 5.
    //  - shifting by 5 is equivalent to dividing by 32 which gives the int to in field to access
    //  - ix and 0x1F is equivalent to modulus by 32 which gives the bit in a specific int
    // Hence these two operations is used in get/set

    constructor(size: Int) : this(size, IntArray((size shr 5) + if (size and 0x1F > 0) 1 else 0))

    val cardinality: Int get() = this.field.sumBy { Int.bitCount(it) }

    override fun copy(): BitArray = BitArray(size, field.copyOf())

    override operator fun get(ix: Int) = (field[ix shr 5] ushr (ix and 0x1F)) and 1 == 1

    override fun flip(ix: Int) {
        val i = ix shr 5
        field[i] = field[i] xor (1 shl (ix and 0x1F))
    }

    override fun set(ix: Int, value: Boolean) {
        val i = ix shr 5
        val mask = 1 shl (ix and 0x1F)
        field[i] = if (value) field[i] or mask
        else field[i] and mask.inv()
    }

    override fun getBits(ix: Int, nbrBits: Int): Int {
        assert(nbrBits in 1..32 && ix + nbrBits <= size)
        val i1 = ix shr 5
        val i2 = (ix + nbrBits - 1) shr 5
        val rem = ix and 0x1F
        return if (i1 != i2) {
            val v1 = (field[i1] ushr Int.SIZE_BITS + rem)
            val v2 = field[i2] shl Int.SIZE_BITS - rem
            val mask = -1 ushr Int.SIZE_BITS - nbrBits
            (v1 or v2) and mask
        } else {
            val value = field[i1] ushr rem
            val mask = -1 ushr Int.SIZE_BITS - nbrBits
            value and mask
        }
    }

    override fun setBits(ix: Int, nbrBits: Int, value: Int) {
        assert(nbrBits >= 1 && nbrBits <= 32 && ix + nbrBits <= size)
        assert(nbrBits == 32 || value and (-1 shl nbrBits) == 0)
        val i1 = ix shr 5
        val i2 = (ix + nbrBits - 1) shr 5
        val rem = ix and 0x1F
        if (i1 != i2) {
            val mask = -1 shl rem
            field[i1] = field[i1] and mask.inv()
            field[i1] = field[i1] or (value shl rem and mask)
            field[i2] = field[i2] and mask
            field[i2] = field[i2] or ((value ushr (32 - rem)) and mask.inv())
        } else {
            val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem).inv()
            val mask2 = (-1 shl rem).inv()
            field[i1] = field[i1] and (mask1 or mask2) // zero out old value
            field[i1] = field[i1] or (value shl rem) // set value
        }
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            var fieldI: Int = 0
            var fieldValue: Int = field[fieldI]
            var i = 0

            private fun advance() {
                if (fieldI + 1 < field.size && fieldValue == 0) {
                    fieldI++
                    while (fieldI + 1 < field.size && field[fieldI] == 0)
                        fieldI++
                    i = 0
                    fieldValue = field[fieldI]
                }
                while (fieldValue != 0 && fieldValue and 1 == 0) {
                    i++
                    fieldValue = fieldValue ushr 1
                }
            }

            init {
                advance()
            }

            override fun hasNext() = fieldI + 1 < field.size || fieldValue != 0
            override fun nextInt(): Int {
                if (i >= 32) throw NoSuchElementException()
                val ret = (fieldI shl Int.SIZE_BYTES + 1) + i
                i++
                fieldValue = fieldValue ushr 1
                advance()
                return ret
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is BitArray) {
            if (size != other.size) false
            else {
                for (i in field.indices) if (field[i] != other.field[i]) return false
                return true
            }
        } else if (other is Instance) deepEquals(other)
        else false
    }

    override fun hashCode(): Int {
        var result = size
        for (l in field)
            result = 31 * result + l
        return result
    }

    override fun toString() = deepToString()
    override val sparse: Boolean get() = false
}