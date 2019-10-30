@file:JvmName("Instances")

package combo.sat

import combo.math.VectorView
import combo.math.vectors
import combo.util.*
import kotlin.jvm.JvmName
import kotlin.math.min
import kotlin.math.sqrt


/**
 * An instance is used by the solvers to find a valid truth assignment. There are two basic implementations:
 * 1) [BitArray] will suit most applications, and [SparseBitArray] will work better for [Problem]s with
 * sparse solutions. The [InstanceFactory] class is used to create the [Instance]s in a generic way by eg. an
 * [combo.sat.optimizers.Optimizer].
 *
 * Equals and hashCode are defined through actual assignment values.
 */
interface Instance : VectorView {

    /**
     * Number of variables declared.
     */
    override val size: Int

    val wordSize: Int get() = (size shr 5) + (if (size and 0x1F > 0) 1 else 0)

    val indices: IntRange
        get() = 0 until size

    override fun copy(): Instance
    override fun vectorCopy() = vectors.vector(toFloatArray())

    fun isSet(ix: Int): Boolean

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

    fun flip(ix: Int) = set(ix, !isSet(ix))
    operator fun set(ix: Int, value: Boolean)
    fun setWord(wordIx: Int, value: Int)
    fun clear()

    override fun get(i: Int) = if (isSet(i)) 1f else 0f

    override fun dot(v: VectorView) = fold(0f) { sum, i -> sum + v[i] }
    override fun norm2() = sqrt(sum())
    override fun sum() = cardinality().toFloat()

    override fun toFloatArray() = FloatArray(size) { if (isSet(it)) 1.0f else 0.0f }
    override fun toIntArray() = IntArray(size) { if (isSet(it)) 1 else 0 }
}

fun Instance.and(inst: Instance) {
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

fun Instance.andNot(inst: Instance) {
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

fun Instance.or(inst: Instance) {
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

fun Instance.setBits(ix: Int, nbrBits: Int, value: Int) {
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

fun Instance.setSignedInt(ix: Int, nbrBits: Int, value: Int) {
    val mask = (-1 ushr Int.SIZE_BITS - nbrBits + 1)
    if (value >= 0) setBits(ix, nbrBits, value and mask)
    else setBits(ix, nbrBits, (1 shl nbrBits - 1) or (value and mask))
}

fun Instance.setFloat(ix: Int, value: Float) = setBits(ix, 32, value.toRawBits())
fun Instance.getFloat(ix: Int) = Float.fromBits(getBits(ix, 32))

/**
 * Get first set bit (0-indexed) between index [from] to [until] (exclusive). Returns -1 on failure.
 */
fun Instance.getFirst(from: Int, until: Int): Int {
    var i = from
    var v: Int
    var nbrBits: Int
    do {
        nbrBits = min(32, until - i)
        v = getBits(i, nbrBits)
        i += nbrBits
    } while (v == 0 && i < until)
    return if (v == 0) -1
    else i - nbrBits - from + Int.bsf(v)
}

/**
 * Get last set bit (0-indexed) between index [from] to [until] (exclusive). Returns -1 on failure.
 */
fun Instance.getLast(from: Int, until: Int): Int {
    var i = until
    var v: Int
    do {
        val nbrBits = min(32, i - from)
        i -= nbrBits
        v = getBits(i, nbrBits)
    } while (v == 0 && i > from)
    return if (v == 0) -1
    else i - from + Int.bsr(v)
}

fun Instance.deepEquals(other: Instance): Boolean {
    if (this === other) return true
    if (size != other.size) return false
    for (i in 0 until wordSize) if (getWord(i) != other.getWord(i)) return false
    return true
}

fun Instance.deepToString() = when {
    wordSize <= 10 -> wordIterator().asSequence().joinToString(", ", "[", "]") { "${it.key()}=${it.value()}" }
    else -> "[<$size>]"
}

interface InstanceFactory {
    fun create(size: Int): Instance
}

fun Instance.cardinality(): Int {
    var sum = 0
    for (e in wordIterator()) sum += Int.bitCount(e.value())
    return sum
}
