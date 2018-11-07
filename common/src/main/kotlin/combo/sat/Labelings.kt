package combo.sat

import combo.math.Vector
import kotlin.experimental.and
import kotlin.experimental.xor
import kotlin.math.min
import kotlin.random.Random

interface Labeling {
    val size: Int
    val indices: IntRange
        get() = 0 until size

    fun copy(): Labeling
    fun asLiteral(ix: Ix): Literal = ix.asLiteral(this[ix])
    operator fun get(ix: Ix): Boolean

    fun asLiterals() = IntArray(size) { asLiteral(it) }
}

infix fun Labeling.dot(v: Vector) =
        v.array.foldIndexed(0.0) { i, dot, d -> dot + d * this[i].toInt() }

interface MutableLabeling : Labeling {
    fun flip(ix: Ix) = set(ix, !get(ix))
    fun set(literal: Literal) = set(literal.asIx(), literal.asBoolean())
    fun setAll(literals: Literals) {
        // TODO all implementations can use a more efficient version
        for (lit in literals) set(lit)
    }

    override fun copy(): MutableLabeling

    operator fun set(ix: Ix, value: Boolean)
    fun pack() = Unit
}

interface LabelingBuilder<out T : MutableLabeling> {
    fun build(size: Int): T
    fun build(size: Int, posLiterals: Literals) = build(size).apply {
        for (l in posLiterals) this.set(l)
    }

    fun generate(size: Int, rng: Random) = build(size).apply {
        for (i in this.indices)
            if (rng.nextBoolean())
                this.flip(i)
    }
}

class ByteArrayLabelingBuilder : LabelingBuilder<ByteArrayLabeling> {
    override fun build(size: Int) = ByteArrayLabeling(size)
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

    override fun equals(other: Any?) = if (other is ByteArrayLabeling) other.values.contentEquals(values) else false
    override fun hashCode() = values.contentHashCode()
    override fun toString() = values.joinToString(",", "[", "]")
}


class BitFieldLabelingBuilder : LabelingBuilder<BitFieldLabeling> {
    override fun build(size: Int) = BitFieldLabeling(size)
    override fun generate(size: Int, rng: Random) = build(size).apply {
        for (i in 0 until field.size) {
            field[i] = rng.nextLong()
        }
        if (field.isNotEmpty()) {
            val mask = (1L shl (size.rem(64))) - 1
            field[0] = field[0] and mask
        }
    }
}

class BitFieldLabeling constructor(override val size: Int, val field: LongArray) : MutableLabeling {
    // TODO use size of long instead of 64 constant

    constructor(size: Int) : this(size, LongArray(size / 64 + if (size.rem(64) > 0) 1 else 0))

    override fun copy(): BitFieldLabeling = BitFieldLabeling(size, field.copyOf())

    override fun get(ix: Ix) = (field[ix / 64] ushr ix.rem(64)) and 1L == 1L

    override fun flip(ix: Ix) {
        val i = ix / 64
        field[i] = field[i] xor (1L shl ix.rem(64))
    }

    override fun set(ix: Ix, value: Boolean) {
        val i = ix / 64
        if (value)
            field[i] = field[i] or (1L shl ix.rem(64))
        else
            field[i] = field[i] and (1L shl ix.rem(64)).inv()
    }

    override fun equals(other: Any?) =
            if (other is BitFieldLabeling) other.size == size && other.field.contentEquals(field) else false

    override fun hashCode() = 31 * size + field.contentHashCode()
    override fun toString() = field.joinToString(",", "[", "]") { it.toString(2) }
}

class SparseLabelingBuilder(private val initialVecSize: Int = 16) : LabelingBuilder<SparseLabeling> {
    override fun build(size: Int) = SparseLabeling(size, initialVecSize)
    override fun build(size: Int, posLiterals: Literals) = SparseLabeling(size, posLiterals)
}

class SparseLabeling constructor(override val size: Int,
                                 literals: Literals,
                                 nbrUsed: Int,
                                 private var nbrPos: Int) : MutableLabeling {

    constructor(size: Int, initialVecSize: Int = 16) : this(size, IntArray(min(size, initialVecSize)), 0, 0)
    constructor(size: Int, literals: Literals) : this(size, literals, literals.size, literals.size)

    var nbrUsed: Int = nbrUsed
        private set
    var literals: Literals = literals
        private set

    override fun copy() = SparseLabeling(size, literals.copyOf(), nbrUsed, nbrPos)

    override fun get(ix: Ix): Boolean {
        val i = binarySearch(ix)
        return i >= 0 && literals[i].asBoolean()
    }

    override fun set(ix: Ix, value: Boolean) {
        var i = binarySearch(ix)
        if (i >= 0) {
            if (literals[i].asBoolean() && !value) nbrPos--
            else if (!literals[i].asBoolean() && value) nbrPos++
            literals[i] = ix.asLiteral(value)
        } else if (value && i < 0) {
            if (nbrUsed >= literals.size) {
                expand()
                i = binarySearch(ix)
            }
            for (j in nbrUsed downTo -i)
                literals[j] = literals[j - 1]
            literals[-i - 1] = ix.asLiteral(true)
            nbrPos++
            nbrUsed++
        }
    }

    /*
    TODO implement efficient version
    override fun setAll(literals: Literals) {
        var i = 0
        var j = 0
        while (i < size && j < literals.size) {
        }
    }
    */

    override fun equals(other: Any?) =
            if (other is SparseLabeling) other.size == size && other.literals.contentEquals(literals) else false

    override fun hashCode() = 31 * size + literals.contentHashCode()
    override fun toString() = literals.joinToString(",", "[", "]")

    private fun binarySearch(ix: Ix): Int {
        if (nbrUsed <= 64) {
            for (i in 0 until nbrUsed) {
                val oid = literals[i].asIx()
                if (ix == oid) return i
                else if (oid > ix) return -(i + 1)
            }
            return -(nbrUsed + 1)
        } else {
            var low = 0
            var high = nbrUsed - 1

            while (low <= high) {
                val mid = (low + high) ushr 1
                val midVal = literals[mid].asIx()
                when {
                    midVal < ix -> low = mid + 1
                    midVal > ix -> high = mid - 1
                    else -> return mid
                }
            }
            return -(low + 1)
        }
    }

    private fun expand() {
        if (nbrPos <= msb(literals.size) * .75) {
            var k = 0
            for (i in 0 until nbrUsed) {
                val l = literals[i]
                if (l.asBoolean()) literals[k++] = literals[i]
            }
            for (i in k until literals.size) {
                literals[i] = 0
            }
            nbrUsed = k
        } else {
            val new = IntArray(msb(literals.size shl 1))
            var k = 0
            for (i in 0 until nbrUsed)
                if (literals[i].asBoolean()) new[k++] = literals[i]
            nbrUsed = k
            literals = new
        }
    }

    override fun pack() {
        if (nbrPos == nbrUsed) {
            literals = literals.copyOfRange(0, nbrPos)
        } else {
            val new = IntArray(nbrPos)
            nbrUsed = 0
            for (l in literals)
                if (l.asBoolean()) new[nbrUsed++] = l
            literals = new
        }
    }

    private fun msb(value: Int): Int {
        var x = value
        x = x or (x shr 1)
        x = x or (x shr 2)
        x = x or (x shr 4)
        x = x or (x shr 8)
        x = x or (x shr 16)
        x = x or (x shr 24)
        return x - (x shr 1)
    }
}

