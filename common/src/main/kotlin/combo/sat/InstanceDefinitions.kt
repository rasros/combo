@file:JvmName("Instances")

package combo.sat

import combo.math.Vector
import combo.util.IntList
import combo.util.assert
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
            if (i > size) throw NoSuchElementException()
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
    val signBit = raw and (1 shl nbrBits - 1) != 0
    return if (signBit)
        raw or (-1 shl nbrBits)
    else raw
}

fun MutableInstance.setSignedInt(ix: Int, nbrBits: Int, value: Int) {
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

operator fun Instance.contains(literal: Literal): Boolean = literal(literal.toIx()) == literal
fun Instance.literal(ix: Int) = ix.toLiteral(this[ix])

fun Instance.toLiterals(): Literals {
    val list = IntList()
    val itr = iterator()
    while (itr.hasNext()) list.add(itr.nextInt().toLiteral(true))
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
