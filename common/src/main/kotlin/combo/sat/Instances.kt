@file:JvmName("Instances")

package combo.sat

import combo.math.Vector
import combo.util.IntHashMap
import combo.util.IntList
import combo.util.key
import combo.util.value
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
        require(nbrBits in 1..32)
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
    override fun copy(): MutableInstance
    operator fun set(ix: Int, value: Boolean)

    fun setBits(ix: Int, nbrBits: Int, value: Int) {
        for (i in 0 until nbrBits)
            set(ix + i, value ushr i and 1 == 1)
    }
}

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

class SparseBitArray(override val size: Int, val map: IntHashMap = IntHashMap(4, -1)) : MutableInstance {

    override operator fun get(ix: Int) = (map[ix / Int.SIZE_BITS] ushr ix.rem(Int.SIZE_BITS)) and 1 == 1

    override fun getBits(ix: Int, nbrBits: Int): Int {
        return super.getBits(ix, nbrBits)
        //TODO()
    }

    override fun setBits(ix: Int, nbrBits: Int, value: Int) {
        super.setBits(ix, nbrBits, value)
        //TODO()
    }

    override fun copy() = SparseBitArray(size, map.copy())

    override fun set(ix: Int, value: Boolean) {
        val i = ix / Int.SIZE_BITS
        val updated = if (value) map[i] or (1 shl ix.rem(Int.SIZE_BITS))
        else map[i] and (1 shl ix.rem(Int.SIZE_BITS)).inv()
        if (updated == 0) map.remove(i)
        else map[i] = updated
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

class BitArray constructor(override val size: Int, val field: LongArray) : MutableInstance {

    constructor(size: Int) : this(size, LongArray(size / Long.SIZE_BITS + if (size.rem(Long.SIZE_BITS) > 0) 1 else 0))

    override fun copy(): BitArray = BitArray(size, field.copyOf())

    override operator fun get(ix: Int) = (field[ix / Long.SIZE_BITS] ushr ix.rem(Long.SIZE_BITS)) and 1L == 1L

    override fun getBits(ix: Int, nbrBits: Int): Int {
        return super.getBits(ix, nbrBits)
        TODO()
        val i1 = ix / Long.SIZE_BITS
        val i2 = (ix + nbrBits) / Long.SIZE_BITS
        if (i1 != i2) {
            val l1 = field[i1]
            // TODO
            return super.getBits(ix, nbrBits)
        } else {
            val l = field[i1] ushr (ix.rem(Long.SIZE_BITS) - nbrBits)
            return l.toInt()
        }
    }

    override fun setBits(ix: Int, nbrBits: Int, value: Int) {
        super.setBits(ix, nbrBits, value)
        //TODO
    }

    override fun flip(ix: Int) {
        val i = ix / Long.SIZE_BITS
        field[i] = field[i] xor (1L shl ix.rem(Long.SIZE_BITS))
    }

    override fun set(ix: Int, value: Boolean) {
        val i = ix / Long.SIZE_BITS
        if (value) field[i] = field[i] or (1L shl ix.rem(Long.SIZE_BITS))
        else field[i] = field[i] and (1L shl ix.rem(Long.SIZE_BITS)).inv()
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
            result = 31 * result + l.hashCode()
        return result
    }

    override fun toString() = deepToString()
    override val sparse: Boolean get() = false
}
