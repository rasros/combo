@file:JvmName("Labelings")

package combo.sat

import combo.math.Vector
import combo.util.IntList
import combo.util.IntSet
import kotlin.experimental.and
import kotlin.experimental.xor
import kotlin.jvm.JvmName
import kotlin.math.max

/**
 * A labeling is used by the solvers to find a valid truth assignment. There are three implementations:
 * 1) [BitFieldLabeling] will suit most applications, 2) [ByteArrayLabeling] trades better CPU performance for worse
 * memory consumption, and finally 3) [IntSetLabeling] will work better for [Problem]s with very sparse solutions.
 * The [LabelingFactory] class is used to create the [Labeling]s in a generic way by eg. an
 * [combo.sat.solvers.Optimizer].
 *
 * Equals and hashCode are defined through actual assignment values.
 */
interface Labeling : Iterable<Int> {
    val size: Int
    val indices: IntRange
        get() = 0 until size

    fun copy(): MutableLabeling
    fun literal(ix: Ix): Literal = ix.toLiteral(this[ix])
    operator fun get(ix: Ix): Boolean

    /**
     * Iterates over all values, returning both true and false literals.
     */
    override fun iterator() = object : IntIterator() {
        var i = 0
        override fun hasNext() = i < size
        override fun nextInt() = this@Labeling.literal(i++)
    }

    /**
     * Iterates over all values, returning true literals only. This method has an efficient implementation for
     * [IntSetLabeling] for sparse [Labeling]s.
     */
    fun truthIterator(): IntIterator = object : IntIterator() {
        var i = 0

        init {
            while (i < size && !this@Labeling[i]) i++
        }

        override fun hasNext() = i < size
        override fun nextInt() = this@Labeling.literal(i).also {
            i++
            while (i < size && !this@Labeling[i]) i++
        }
    }

    fun toLiterals(trueValuesOnly: Boolean = true) = if (trueValuesOnly) {
        val list = IntList()
        val itr = truthIterator()
        while (itr.hasNext()) list.add(itr.nextInt())
        list.toArray()
    } else IntArray(size) { literal(it) }
}

interface MutableLabeling : Labeling {
    fun flip(ix: Ix) = set(ix, !get(ix))
    fun set(literal: Literal) = set(literal.toIx(), literal.toBoolean())
    fun setAll(literals: Literals) = literals.forEach { set(it) }
    override fun copy(): MutableLabeling
    operator fun set(ix: Ix, value: Boolean)
}

internal fun Labeling.deepEquals(other: Labeling): Boolean {
    if (this === other) return true
    if (size != other.size) return false
    for (i in 0 until size) if (this[i] != other[i]) return false
    return true
}

internal fun Labeling.deepHashCode(): Int {
    var result = size
    val itr = truthIterator()
    while (itr.hasNext())
        result = 31 * result + itr.nextInt()
    return result
}

internal fun Labeling.deepToString() = toIntArray().joinToString(",", "[", "]")

infix fun Labeling.dot(v: Vector): Double {
    var sum = 0.0
    val itr = truthIterator()
    while (itr.hasNext()) sum += v[itr.nextInt().toIx()]
    return sum
}

fun Labeling.toIntArray() = IntArray(size) { if (this[it]) 1 else 0 }
fun Labeling.toDoubleArray() = DoubleArray(size) { if (this[it]) 1.0 else 0.0 }

interface LabelingFactory {
    fun create(size: Int): MutableLabeling
}

object IntSetLabelingFactory : LabelingFactory {
    override fun create(size: Int) = IntSetLabeling(size)
}

class IntSetLabeling(override val size: Int, val intSet: IntSet = IntSet(max(2, (size * 0.2).toInt()))) : MutableLabeling {
    override fun get(ix: Ix) = ix.toLiteral(true) in intSet
    override fun copy() = IntSetLabeling(size, intSet.copy())
    override fun set(ix: Ix, value: Boolean) {
        if (value) intSet.add(ix.toLiteral(true))
        else intSet.remove(ix.toLiteral(true))
    }

    override fun truthIterator() = intSet.iterator()

    override fun equals(other: Any?) = if (other is Labeling) deepEquals(other) else false
    override fun hashCode() = deepHashCode()
    override fun toString() = deepToString()
}

object ByteArrayLabelingFactory : LabelingFactory {
    override fun create(size: Int) = ByteArrayLabeling(size)
}

class ByteArrayLabeling constructor(val values: ByteArray) : MutableLabeling {

    constructor(size: Int) : this(ByteArray(size))

    private companion object {
        private const val ONE = 1.toByte()
        private const val ZERO = 0.toByte()
    }

    override fun copy(): ByteArrayLabeling = ByteArrayLabeling(values.copyOf())
    override val size: Int
        get() = values.size

    override fun flip(ix: Int) {
        values[ix] = values[ix] xor ONE
    }

    override operator fun set(ix: Ix, value: Boolean) {
        values[ix] = if (value) ONE else ZERO
    }

    override operator fun get(ix: Ix): Boolean = (values[ix] and 1) == ONE

    override fun literal(ix: Ix): Literal = ix.toLiteral(this[ix])

    override fun equals(other: Any?) = if (other is Labeling) deepEquals(other) else false
    override fun hashCode() = deepHashCode()
    override fun toString() = deepToString()
}

object BitFieldLabelingFactory : LabelingFactory {
    override fun create(size: Int) = BitFieldLabeling(size)
}

class BitFieldLabeling constructor(override val size: Int, val field: LongArray) : MutableLabeling {

    constructor(size: Int) : this(size, LongArray(size / Long.SIZE_BITS + if (size.rem(Long.SIZE_BITS) > 0) 1 else 0))

    override fun copy(): BitFieldLabeling = BitFieldLabeling(size, field.copyOf())

    override fun get(ix: Ix) = (field[ix / Long.SIZE_BITS] ushr ix.rem(Long.SIZE_BITS)) and 1L == 1L

    override fun flip(ix: Ix) {
        val i = ix / Long.SIZE_BITS
        field[i] = field[i] xor (1L shl ix.rem(Long.SIZE_BITS))
    }

    override fun set(ix: Ix, value: Boolean) {
        val i = ix / Long.SIZE_BITS
        if (value)
            field[i] = field[i] or (1L shl ix.rem(Long.SIZE_BITS))
        else
            field[i] = field[i] and (1L shl ix.rem(Long.SIZE_BITS)).inv()
    }

    override fun equals(other: Any?) = if (other is Labeling) deepEquals(other) else false
    override fun hashCode() = deepHashCode()
    override fun toString() = deepToString()
}
