package combo.sat

import combo.util.entry

object BitArrayFactory : InstanceFactory {
    override fun create(size: Int) = BitArray(size)
}

/**
 * This uses a dense int array as backing for [Instance]. 32 bit ints are used instead of 64 bits due to JavaScript
 * interoperability. The advantage of bitarray is low size but also fast iteration for semi-sprase data because ints
 * with 0-value can be skipped entirely.
 */
class BitArray constructor(override val size: Int, val field: IntArray) : Instance {

    // Note this code uses a lot of bit shifts. The most common being masking by 0x1F and shifting right by 5.
    //  - shifting by 5 is equivalent to dividing by 32 which gives the int-field to access
    //  - ix and 0x1F is equivalent to modulus by 32 which gives the bit in a specific int
    // Hence these two operations is used in get/set

    constructor(size: Int) : this(size, IntArray((size shr 5) + if (size and 0x1F > 0) 1 else 0))

    override val wordSize: Int get() = this.field.size

    override fun copy(): BitArray = BitArray(size, field.copyOf())

    override fun isSet(ix: Int) = (field[ix shr 5] ushr (ix and 0x1F)) and 1 == 1

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

    override fun getWord(wordIx: Int) = field[wordIx]

    override fun setWord(wordIx: Int, value: Int) {
        field[wordIx] = value
    }

    override fun clear() {
        for (i in field.indices)
            field[i] = 0
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            var fieldI: Int = 0
            var fieldValue: Int = if (field.isEmpty()) 0 else field[fieldI]
            var valueI = 0

            private fun advance() {
                if (fieldI + 1 < field.size && fieldValue == 0) {
                    fieldI++
                    while (fieldI + 1 < field.size && field[fieldI] == 0)
                        fieldI++
                    valueI = 0
                    fieldValue = field[fieldI]
                }
                while (fieldValue != 0 && fieldValue and 1 == 0) {
                    valueI++
                    fieldValue = fieldValue ushr 1
                }
            }

            init {
                advance()
            }

            override fun hasNext() = fieldI + 1 < field.size || fieldValue != 0
            override fun nextInt(): Int {
                if (valueI >= 32) throw NoSuchElementException()
                val ret = (fieldI shl 5) + valueI
                valueI++
                fieldValue = fieldValue ushr 1
                advance()
                return ret
            }
        }
    }

    override fun wordIterator() = object : LongIterator() {
        var i = 0
        override fun hasNext() = i < field.size
        override fun nextLong() = entry(i, field[i++])
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