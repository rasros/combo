@file:JvmName("Instances")

package combo.sat

import combo.math.Vector
import combo.util.*
import kotlin.jvm.JvmName


/**
 * An instance is used by the solvers to find a valid truth assignment. There are two basic implementations:
 * 1) [BitArray] will suit most applications, and [SparseBitArray] will work better for [Problem]s with
 * sparse solutions. The [InstanceFactory] class is used to create the [Instance]s in a generic way by eg. an
 * [combo.sat.solvers.Optimizer].
 *
 * Equals and hashCode are defined through actual assignment values.
 */
interface Instance : Iterable<Int> {

    /**
     * Number of variables declared.
     */
    val size: Int

    val indices: IntRange
        get() = 0 until size

    fun copy(): MutableInstance

    operator fun get(ix: Int): Boolean

    fun getBits(ix: Int, nbrBits: Int): Int {
        assert(nbrBits in 1..32 && ix + nbrBits <= size)
        var bits = 0
        for (i in 0 until nbrBits)
            if (get(ix + i)) bits = bits xor (1 shl i)
        return bits
    }

    /**
     * Iterates over all set values returning indices.
     */
    override fun iterator() = object : IntIterator() {
        var i = 0

        init {
            while (i < size && !this@Instance[i]) i++
        }

        override fun hasNext() = i < size
        override fun nextInt() = i++.also {
            if (i >= size) throw NoSuchElementException()
            while (i < size && !this@Instance[i]) i++
        }
    }

    val sparse: Boolean
}

interface MutableInstance : Instance {

    fun flip(ix: Int) = set(ix, !get(ix))
    operator fun set(ix: Int, value: Boolean)

    fun setBits(ix: Int, nbrBits: Int, value: Int) {
        assert(nbrBits in 1..32 && ix + nbrBits <= size)
        var k = value
        for (i in 0 until nbrBits) {
            set(ix + i, k and 1 == 1)
            k = k ushr 1
            //set(ix + i, value ushr i and 1 == 1)
        }
        assert(k == 0)
    }
}

/**
 * Gets an integer that is downcasted to variable number of bits.
 */
fun Instance.getSignedInt(ix: Int, nbrBits: Int): Int {
    val raw = getBits(ix, nbrBits)
    return if (raw and (1 shl nbrBits - 1) != 0) raw or -65536
    else raw
}

fun MutableInstance.setSignedBits(ix: Int, nbrBits: Int, value: Int) {
    val mask = (-1 ushr Int.SIZE_BITS - nbrBits + 1)
    if (value >= 0) setBits(ix, nbrBits, value and mask)
    else setBits(ix, nbrBits, (1 shl nbrBits - 1) or (value and mask))
}

fun MutableInstance.setFloat(ix: Int, value: Float) = setBits(ix, 32, value.toRawBits())
fun Instance.getFloat(ix: Int) = Float.fromBits(getBits(ix, 32))

fun Instance.deepEquals(other: Instance): Boolean {
    if (this === other) return true
    if (size != other.size) return false
    for (i in 0 until size) if (this[i] != other[i]) return false
    return true
}

fun Instance.deepHashCode(): Int {
    var result = size
    val itr = iterator()
    while (itr.hasNext())
        result = 31 * result + itr.nextInt()
    return result
}

fun Instance.deepToString() = toIntArray().joinToString(",", "[", "]")

infix fun Instance.dot(v: Vector): Double {
    var sum = 0.0
    val itr = iterator()
    while (itr.hasNext()) sum += v[itr.nextInt().toIx()]
    return sum
}

fun Instance.literal(ix: Int) = ix.toLiteral(this[ix])

fun Instance.toLiterals(): Literals {
    val list = IntList()
    val itr = iterator()
    while (itr.hasNext()) list.add(itr.nextInt())
    list.toArray()
    return list.toArray()
}

fun MutableInstance.set(literal: Literal) = set(literal.toIx(), literal.toBoolean())
fun MutableInstance.setAll(literals: Literals) = literals.forEach { set(it) }
fun MutableInstance.setAll(literals: Iterable<Literal>) = literals.forEach { set(it) }

fun Instance.toIntArray() = IntArray(size) { if (this[it]) 1 else 0 }
fun Instance.toDoubleArray() = DoubleArray(size) { if (this[it]) 1.0 else 0.0 }

interface InstanceFactory {
    fun create(size: Int): MutableInstance
}

object SparseBitArrayFactory : InstanceFactory {
    override fun create(size: Int) = SparseBitArray(size)
}

class SparseBitArray(override val size: Int, val map: IntIntHashMap = IntIntHashMap(1, -1)) : MutableInstance {

    override operator fun get(ix: Int) = (map[ix shr 5] ushr ix and 0x1F) and 1 == 1

    override fun copy() = SparseBitArray(size, map.copy())

    override fun set(ix: Int, value: Boolean) {
        val i = ix shr 5
        val rem = ix and 0x1F
        setOrRemove(i, if (value) map[i] or (1 shl rem)
        else map[i] and (1 shl rem).inv())
    }

    private fun setOrRemove(i: Int, value: Int) {
        if (value == 0) map.remove(i)
        else map[i] = value
    }

    override fun getBits(ix: Int, nbrBits: Int): Int {
        assert(nbrBits in 1..32 && ix + nbrBits <= size)
        val i1 = ix shr 5
        val i2 = (ix + nbrBits - 1) shr 5
        val rem = ix and 0x1F
        return if (i1 != i2) {
            val v1 = (map[i1] ushr Int.SIZE_BITS + rem)
            val v2 = map[i2] shl Int.SIZE_BITS - rem
            val mask = -1 ushr Int.SIZE_BITS - nbrBits
            (v1 or v2) and mask
        } else {
            val value = map[i1] ushr rem
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
            val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem)
            val mask2 = (-1 shl rem)

            var v1 = map[i1] and mask1
            v1 = v1 or (value shl rem and mask1.inv())
            setOrRemove(i1, v1)

            var v2 = map[i2] and mask2
            v2 = v2 or ((value ushr (32 - rem)) and mask2.inv())
            setOrRemove(i2, v2)
        } else {
            val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem).inv()
            val mask2 = (-1 shl rem).inv()
            var v = map[i1] and (mask1 or mask2) // zero out old value
            v = v or (value shl rem) // set value
            setOrRemove(i1, v)
        }
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            var base = map.iterator()
            var currentKey: Int = 0
            var currentValue: Int = 0
            var i = 0
            override fun hasNext() = base.hasNext() || currentValue != 0
            override fun nextInt(): Int {
                if (currentValue == 0) {
                    val l = base.nextLong()
                    currentKey = l.key()
                    currentValue = l.value()
                    i = 0
                }
                // (map[ix / Int.SIZE_BITS] ushr ix.rem(Int.SIZE_BITS)) and 1 == 1
                while (currentValue and 1 == 0) {
                    i++
                    currentValue = currentValue ushr 1
                }
                currentValue = currentValue ushr 1
                if (i >= 32) throw NoSuchElementException()
                return (currentKey shl Int.SIZE_BYTES + 1) + i++
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is SparseBitArray) {
            if (size != other.size) false
            else {
                val itr1 = map.iterator()
                val itr2 = other.map.iterator()
                while (itr1.hasNext() && itr2.hasNext())
                    if (itr1.nextLong() != itr2.nextLong()) return false
                if (itr1.hasNext() || itr2.hasNext()) return false
                return true
            }
        } else if (other is Instance) deepEquals(other)
        else false
    }

    override fun hashCode(): Int {
        var result = size
        val itr = map.iterator()
        while (itr.hasNext()) {
            val l = itr.nextLong()
            result = 31 * result + l.key()
            result = 31 * result + l.value()
        }
        return result
    }

    override fun toString() = deepToString()
    override val sparse: Boolean get() = true
}

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
            val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem)
            val mask2 = (-1 shl rem)
            field[i1] = field[i1] and mask1
            field[i1] = field[i1] or (value shl rem and mask1.inv())
            field[i2] = field[i2] and mask2
            field[i2] = field[i2] or ((value ushr (32 - rem)) and mask2.inv())
        } else {
            val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem).inv()
            val mask2 = (-1 shl rem).inv()
            field[i1] = field[i1] and (mask1 or mask2) // zero out old value
            field[i1] = field[i1] or (value shl rem) // set value
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
