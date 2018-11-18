package combo.util

import combo.sat.Ix
import combo.util.IntSet.Companion.LOAD_FACTOR
import combo.util.IntSet.Companion.hash
import combo.util.IntSet.Companion.tableSizeFor
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

interface IntCollection : Iterable<Ix> {

    val size: Int
    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0
    fun copy(): IntCollection
    fun clear()
    fun contains(ix: Ix): Boolean
    fun toArray(): IntArray

    override fun iterator(): IntIterator

    fun random(rng: Random = Random.Default): Int
    fun add(ix: Ix): Boolean
    fun addAll(ixs: IntArray) = ixs.fold(true) { all, it -> this.add(it) && all }
    fun addAll(ixs: Iterable<Ix>) = ixs.fold(true) { all, it -> this.add(it) && all }
    fun remove(ix: Ix): Boolean
}

/**
 * Specialized open addressing hash table for storing index variables. Storing only positive (>=0) integers.
 * Also supports getting a random value from the index. It uses linear probing (remove operation works only with linear
 * probing).
 */
class IntSet private constructor(private var table: IntArray, private var _size: Int) : IntCollection {

    constructor(initialSize: Int = 4) : this(IntArray(tableSizeFor(initialSize)) { -1 }, 0)

    internal companion object {
        const val LOAD_FACTOR = 0.55

        @JvmStatic
        fun hash(i: Int): Int {
            var x = i
            x = ((x shr 16) xor x) * 0x45d9f3b
            x = ((x ushr 16) xor x) * 0x45d9f3b
            x = (x ushr 16) xor x
            return x
        }

        fun tableSizeFor(size: Int): Int {
            return max(2, msb((size.toDouble() / LOAD_FACTOR).toInt()) * 2)
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

    override val size: Int
        get() = _size

    override fun copy() = IntSet(table.copyOf(), size)

    override fun clear() {
        table.forEachIndexed { i, _ -> table[i] = -1 }
        _size = 0
    }

    override operator fun contains(ix: Int) = table[linearProbe(ix)] >= 0

    override fun toArray(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i] >= 0) result[ix++] = table[i]
        return result
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextInt(): Int {
                while (table[ptr] < 0)
                    ptr++
                seen++
                return table[ptr++]
            }
        }
    }

    override fun random(rng: Random): Int {
        require(size > 0)
        var k = rng.nextInt(table.size)
        while (table[k] < 0)
            k = (k + 1) % table.size
        return table[k]
    }

    override fun add(ix: Ix): Boolean {
        require(ix >= 0)
        if (table[linearProbe(ix)] >= 0)
            return false
        if (tableSizeFor(size + 1) > table.size)
            rehash()
        _size++
        table[linearProbe(ix)] = ix
        return true
    }

    private fun rehash() {
        val old = table
        table = IntArray(tableSizeFor((_size.toDouble() / LOAD_FACTOR).toInt()))
        table.forEachIndexed { i, _ -> table[i] = -1 }
        _size = 0
        for (i in old.indices)
            if (old[i] >= 0) add(old[i])
    }

    override fun remove(ix: Ix): Boolean {
        var i = linearProbe(ix)
        if (table[i] < 0) return false
        _size--

        var j = i
        table[i] = -1
        while (true) {
            j = (j + 1) % table.size
            if (table[j] < 0) break
            val k = abs(hash(table[j])) % table.size
            if ((j > i && (k <= i || k > j)) || (j < i && (k <= i && k > j))) {
                table[i] = table[j]
                i = j
            }
            table[i] = -1
        }
        return true
    }

    private fun linearProbe(ix: Int): Int {
        var j = abs(hash(ix)) % table.size
        while ((table[j] >= 0 && table[j] != ix))
            j = (j + 1) % table.size
        return j
    }
}

class IntList private constructor(private var array: IntArray, private var _size: Int) : IntCollection {

    constructor(initialSize: Int) : this(IntArray(initialSize), 0)

    override val size: Int
        get () = _size

    override fun copy() = IntList(array.copyOf(), size)

    override fun clear() {
        _size = 0
    }

    operator override fun contains(ix: Ix) = indexOf(ix) >= 0

    operator fun get(index: Int) = array[index]

    fun indexOf(ix: Int): Int {
        for (i in 0 until _size) {
            if (array[i] == ix) return i
        }
        return -1
    }

    override fun toArray() = array.copyOfRange(0, _size)

    override fun iterator() = object : IntIterator() {
        private var ptr = 0
        override fun hasNext() = ptr < size
        override fun nextInt() = array[ptr++]
    }

    override fun random(rng: Random) = array[rng.nextInt(_size)]

    override fun add(ix: Int): Boolean {
        if (array.size == _size)
            array = array.copyOf(array.size * 2)
        array[_size++] = ix
        return true
    }

    override fun remove(ix: Int): Boolean {
        val indexOf = indexOf(ix)
        if (indexOf >= 0) {
            _size--
            for (i in indexOf until _size)
                array[i] = array[i + 1]
            return true
        }
        return false
    }
}

typealias IntEntry = Long

fun IntEntry.key() = (this ushr (Int.SIZE_BITS)).toInt()
fun IntEntry.value() = (this shl (Int.SIZE_BITS)).toInt()

/**
 * Specialized open addressing hash table for storing index variables. Storing only positive (>=0) integers.
 * Also supports getting a random value from the index. It uses linear probing (remove operation works only with linear
 * probing).
 */
class IntMap private constructor(private var table: LongArray, private var _size: Int) : Iterable<IntEntry> {

    constructor(initialSize: Int = 4) : this(LongArray(tableSizeFor(initialSize)), 0)

    val size: Int
        get() = _size

    fun copy() = IntMap(table.copyOf(), size)

    fun clear() {
        table.forEachIndexed { i, _ -> table[i] = -1 }
        _size = 0
    }

    operator fun contains(ix: Int) = table[linearProbe(ix)] >= 0

    fun keys(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i] >= 0) result[ix++] = table[i].key()
        return result
    }

    fun values(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i] >= 0) result[ix++] = table[i].value()
        return result
    }

    override fun iterator(): LongIterator {
        return object : LongIterator() {
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextLong(): IntEntry {
                while (table[ptr] < 0)
                    ptr++
                seen++
                return table[ptr++]
            }
        }
    }

    fun add(entry: IntEntry) = set(entry.key(), entry.value())

    operator fun set(key: Int, value: Int): Boolean {
        require(key >= 0)
        if (table[linearProbe(key)] >= 0)
            return false
        if (tableSizeFor(size + 1) > table.size)
            rehash()
        _size++
        table[linearProbe(key)] = key.toLong() shl Int.SIZE_BITS or value.toLong()
        return true
    }

    private fun rehash() {
        val old = table
        table = LongArray(tableSizeFor((_size.toDouble() / LOAD_FACTOR).toInt()))
        table.forEachIndexed { i, _ -> table[i] = -1 }
        _size = 0
        for (i in old.indices)
            if (old[i] >= 0) add(old[i])
    }

    fun remove(ix: Ix): Boolean {
        var i = linearProbe(ix)
        if (table[i] < 0) return false
        _size--

        var j = i
        table[i] = -1
        while (true) {
            j = (j + 1) % table.size
            if (table[j] < 0) break
            val k = abs(hash(table[j].key())) % table.size
            if ((j > i && (k <= i || k > j)) || (j < i && (k <= i && k > j))) {
                table[i] = table[j]
                i = j
            }
            table[i] = -1
        }
        return true
    }

    private fun linearProbe(ix: Int): Int {
        var j = abs(hash(ix)) % table.size
        while ((table[j].key() >= 0 && table[j].key() != ix))
            j = (j + 1) % table.size
        return j
    }
}
