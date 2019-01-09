package combo.sat

import combo.math.Vector
import combo.util.IntSet
import kotlin.experimental.and
import kotlin.experimental.xor
import kotlin.math.max

interface Labeling : Iterable<Int> {
    val size: Int
    val indices: IntRange
        get() = 0 until size

    fun copy(): Labeling
    fun asLiteral(ix: Ix): Literal = ix.asLiteral(this[ix])
    operator fun get(ix: Ix): Boolean

    override fun iterator() = object : IntIterator() {
        var i = 0
        override fun hasNext() = i < size
        override fun nextInt() = this@Labeling.asLiteral(i++)
    }

    fun truthIterator() = object : IntIterator() {
        var i = 0

        init {
            while (i < size && !this@Labeling[i]) i++
        }

        override fun hasNext() = i < size
        override fun nextInt() = this@Labeling.asLiteral(i).also {
            i++
            while (i < size && !this@Labeling[i]) i++
        }
    }

    fun asLiterals() = IntArray(size) { asLiteral(it) }
}

interface MutableLabeling : Labeling {
    fun flip(ix: Ix) = set(ix, !get(ix))
    fun set(literal: Literal) = set(literal.asIx(), literal.asBoolean())
    fun setAll(literals: Literals) = literals.forEach { set(it) }
    override fun copy(): MutableLabeling
    operator fun set(ix: Ix, value: Boolean)
}

internal fun Labeling.deepEquals(other: Labeling): Boolean {
    if (size != other.size) return false
    for (i in 0 until size) if (this[i] != other[i]) return false
    return true
}

internal fun Labeling.deepHashCode(): Int {
    var result = size
    for (i in truthIterator())
        result = 31 * result + i
    return result
}

internal fun Labeling.deepToString() = toIntArray().joinToString(",", "[", "]")

infix fun Labeling.dot(v: Vector) =
        v.foldIndexed(0.0) { i, dot, d -> dot + d * this[i].toInt() }

fun Labeling.toIntArray() = IntArray(size) { if (this[it]) 1 else 0 }

interface LabelingFactory {
    fun create(size: Int): MutableLabeling
}

object IntSetLabelingFactory : LabelingFactory {
    override fun create(size: Int) = IntSetLabeling(size)
}

class IntSetLabeling(override val size: Int, val intSet: IntSet = IntSet(max(2, (size * 0.2).toInt()))) : MutableLabeling {
    override fun get(ix: Ix) = ix.asLiteral(true) in intSet
    override fun copy() = IntSetLabeling(size, intSet.copy())
    override fun set(ix: Ix, value: Boolean) {
        if (value) intSet.add(ix.asLiteral(true))
        else intSet.remove(ix.asLiteral(true))
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

    override fun asLiteral(ix: Ix): Literal = ix.asLiteral(this[ix])

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
