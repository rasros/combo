@file:JvmName("Instances")

package combo.sat

import combo.math.Vector
import combo.util.*
import kotlin.jvm.JvmName


/**
 * An instance is used by the solvers to find a valid truth assignment. There are two basic implementations:
 * 1) [BitArray] will suit most applications, and [SparseBitArray] will work better for [Problem]s with
 * sparse solutions. The [InstanceBuilder] class is used to create the [Instance]s in a generic way by eg. an
 * [combo.sat.solvers.Optimizer].
 *
 * Equals and hashCode are defined through actual assignment values.
 */
interface Instance : Iterable<Int> {

    /**
     * Number of variables declared.
     */
    val size: Int

    val wordSize: Int get() = (size shr 5) + (if (size and 0x1F > 0) 1 else 0)

    val indices: IntRange
        get() = 0 until size

    fun copy(): MutableInstance

    operator fun get(ix: Int): Boolean

    fun getWord(wordIx: Int): Int

    /**
     * Iterates over all set values returning indices.
     */
    override fun iterator(): IntIterator

    /**
     * Iterate over the words in the bit array in arbitrary order as wrapped key/value pairs. The upper 4 bytes contain
     * the word index and the lower 4 bytes contain the word data. Returning 0 valued words is implementation specific.
     * Use [combo.util.key] and [combo.util.value] to extract key/value pairs.
     */
    fun wordIterator(): LongIterator

    val sparse: Boolean
}

interface MutableInstance : Instance {
    fun flip(ix: Int) = set(ix, !get(ix))
    operator fun set(ix: Int, value: Boolean)
    fun setWord(wordIx: Int, value: Int)
}

fun Instance.nbrBits(wordIx: Int): Int {
    return when {
        size == 0 -> 0
        wordIx == wordSize - 1 -> {
            val s = size and 0x1F
            if (s == 0) 32
            else s
        }
        else -> 32
    }
}

fun MutableInstance.and(inst: Instance) {
    for (entry in inst.wordIterator()) {
        val key = entry.key()
        val value = entry.value()
        val current = getWord(key)
        setWord(key, current and value)
    }
    if (sparse) {
        for (entry in wordIterator()) {
            val key = entry.key()
            val value = entry.value()
            val current = inst.getWord(key)
            setWord(key, current and value)
        }
    }
}

fun MutableInstance.andNot(inst: Instance) {
    for (entry in inst.wordIterator()) {
        val key = entry.key()
        val value = entry.value()
        val current = getWord(key)
        setWord(key, current and value.inv())
    }
    if (sparse) {
        for (entry in wordIterator()) {
            val key = entry.key()
            val value = entry.value()
            val current = inst.getWord(key)
            setWord(key, current and value.inv())
        }
    }
}

fun MutableInstance.or(inst: Instance) {
    for (entry in inst.wordIterator()) {
        val key = entry.key()
        val value = entry.value()
        val current = getWord(key)
        setWord(key, current or value)
    }
}

fun Instance.getBits(ix: Int, nbrBits: Int): Int {
    assert(nbrBits in 1..32 && ix + nbrBits <= size)
    val wordIx1 = ix shr 5
    val wordIx2 = (ix + nbrBits - 1) shr 5
    val rem = ix and 0x1F
    return if (wordIx1 != wordIx2) {
        val v1 = (getWord(wordIx1) ushr Int.SIZE_BITS + rem)
        val v2 = getWord(wordIx2) shl Int.SIZE_BITS - rem
        val mask = -1 ushr Int.SIZE_BITS - nbrBits
        (v1 or v2) and mask
    } else {
        val value = getWord(wordIx1) ushr rem
        val mask = -1 ushr Int.SIZE_BITS - nbrBits
        value and mask
    }
}

fun MutableInstance.setBits(ix: Int, nbrBits: Int, value: Int) {
    assert(nbrBits >= 1 && nbrBits <= 32 && ix + nbrBits <= size)
    assert(nbrBits == 32 || value and (-1 shl nbrBits) == 0)
    val i1 = ix shr 5
    val i2 = (ix + nbrBits - 1) shr 5
    val rem = ix and 0x1F
    if (i1 != i2) {
        val mask = -1 shl rem

        var v1 = getWord(i1) and mask.inv()
        v1 = v1 or (value shl rem and mask)
        setWord(i1, v1)

        var v2 = getWord(i2) and mask
        v2 = v2 or ((value ushr (32 - rem)) and mask.inv())
        setWord(i2, v2)
    } else {
        val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem).inv()
        val mask2 = (-1 shl rem).inv()
        var v = getWord(i1) and (mask1 or mask2) // zero out old value
        v = v or (value shl rem) // set value
        setWord(i1, v)
    }
}

/**
 * Gets an integer that is downcasted to variable number of bits.
 */
fun Instance.getSignedInt(ix: Int, nbrBits: Int): Int {
    val raw = getBits(ix, nbrBits)
    val signBit = raw and (1 shl nbrBits - 1) != 0
    return if (signBit && nbrBits < 32)
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

infix fun Instance.dot(v: Vector): Float {
    var sum = 0.0f
    val itr = iterator()
    while (itr.hasNext()) sum += v[itr.nextInt()]
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
fun Instance.toFloatArray() = FloatArray(size) { if (this[it]) 1.0f else 0.0f }

interface InstanceBuilder {
    fun create(size: Int): MutableInstance
}

fun Instance.cardinality(): Int = wordIterator().asSequence().sumBy { Int.bitCount(it.value()) }
