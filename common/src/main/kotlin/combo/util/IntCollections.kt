@file:JvmName("IntCollections")

package combo.util

import combo.math.IntPermutation
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Immutable collection
 */
fun collectionOf(vararg array: Int): IntCollection =
        when {
            array.size > 20 -> IntHashSet().apply { addAll(array) }
            array.isEmpty() -> EmptyCollection
            else -> IntList(array)
        }

fun IntCollection.mutableCopy(): MutableIntCollection =
        when {
            this.isEmpty() -> IntList()
            this is MutableIntCollection -> this.copy()
            else -> collectionOf(*this.toArray()) as MutableIntCollection
        }


private fun hash(i: Int): Int {
    var x = i
    x = ((x shr 16) xor x) * 0x45d9f3b
    x = ((x ushr 16) xor x) * 0x45d9f3b
    x = (x ushr 16) xor x
    return x
}

const val LOAD_FACTOR = 0.55
private fun tableSizeFor(size: Int): Int {
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

interface IntCollection : Iterable<Int> {
    val size: Int
    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0
    fun copy(): IntCollection
    operator fun contains(ix: Int): Boolean
    fun toArray(): IntArray
    fun map(transform: (Int) -> Int): IntCollection

    override fun iterator(): IntIterator
    fun permutation(rng: Random): IntIterator

    fun random(rng: Random): Int
}

interface MutableIntCollection : IntCollection {
    override fun copy(): MutableIntCollection
    fun clear()
    override fun map(transform: (Int) -> Int): MutableIntCollection
    fun add(ix: Int): Boolean
    fun addAll(ixs: IntArray) = ixs.fold(false) { any, it -> this.add(it) || any }
    fun addAll(ixs: Iterable<Int>) = ixs.fold(false) { any, it -> this.add(it) || any }
    fun remove(ix: Int): Boolean
}

object EmptyCollection : IntCollection {
    override val size = 0

    override fun copy() = IntHashSet()
    override operator fun contains(ix: Int) = false
    override fun toArray() = EMPTY_INT_ARRAY
    override fun map(transform: (Int) -> Int) = this
    override fun iterator() = object : IntIterator() {
        override fun hasNext() = false
        override fun nextInt() = throw NoSuchElementException()
    }

    override fun permutation(rng: Random) = iterator()
    override fun random(rng: Random) = throw NoSuchElementException()
}

/**
 * Only map is implemented in [MutableIntCollection] and it only works for linear functions.
 */
class IntRangeSet(min: Int, max: Int) : IntCollection {

    private val range: IntRange = min..max

    override var size: Int = max(0, 1 + (max - min))
        private set

    override fun contains(ix: Int) = ix in range

    override fun toArray(): IntArray {
        val array = IntArray(size)
        var k = 0
        for (i in this)
            array[k++] = i
        return array
    }

    override fun map(transform: (Int) -> Int) =
            IntRangeSet(transform.invoke(range.first), transform.invoke(range.last))

    override fun iterator() = range.iterator()

    override fun permutation(rng: Random): IntIterator {
        var i = 0
        val perm = IntPermutation(size, rng)
        return object : IntIterator() {
            override fun hasNext() = i < size
            override fun nextInt(): Int {
                if (i >= size) throw NoSuchElementException()
                return range.first + perm.encode(i++)
            }
        }
    }

    override fun random(rng: Random): Int {
        if (isEmpty()) throw NoSuchElementException()
        return range.first + rng.nextInt(size)
    }

    override fun copy() = IntRangeSet(range.first, range.last)
}

/**
 * Specialized open addressing hash table for storing int variables.
 * It uses linear probing (remove operation works only with linear probing).
 */
class IntHashSet private constructor(private var table: IntArray, size: Int, val nullValue: Int = 0) : MutableIntCollection {

    /**
     * @param initialSize ensure the capacity of this many items
     * @param nullValue use this to represent null values. This value cannot be added to the set.
     */
    constructor(initialSize: Int = 4, nullValue: Int = 0) : this(IntArray(tableSizeFor(initialSize)) { nullValue }, 0, nullValue)

    override var size: Int = size
        private set

    override fun copy() = IntHashSet(table.copyOf(), size, nullValue)

    override fun clear() {
        table = IntArray(tableSizeFor(4))
        if (nullValue != 0)
            table.forEachIndexed { i, _ -> table[i] = nullValue }
        size = 0
    }

    override operator fun contains(ix: Int) = table[linearProbe(ix)] != nullValue

    override fun toArray(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i] != nullValue) result[ix++] = table[i]
        return result
    }

    override fun map(transform: (Int) -> Int): IntHashSet {
        val mapped = IntHashSet(size)
        for (i in table) {
            if (i != nullValue) mapped.add(transform(i))
        }
        return mapped
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextInt(): Int {
                if (seen >= size) throw NoSuchElementException()
                seen++
                while (table[ptr] == nullValue)
                    ptr = (ptr + 1) % table.size
                return table[ptr].also {
                    ptr = (ptr + 1) % table.size
                }
            }
        }
    }

    override fun permutation(rng: Random): IntIterator {
        return object : IntIterator() {
            private var perm = IntPermutation(table.size, rng)
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextInt(): Int {
                if (seen >= size) throw NoSuchElementException()
                seen++
                while (table[perm.encode(ptr)] == nullValue)
                    ptr = (ptr + 1) % table.size
                return table[perm.encode(ptr)].also {
                    ptr = (ptr + 1) % table.size
                }
            }
        }
    }

    override fun random(rng: Random): Int {
        if (isEmpty()) throw NoSuchElementException()
        while (true) {
            val k = rng.nextInt(table.size)
            if (table[k] != nullValue) return table[k]
        }
    }

    override fun add(ix: Int): Boolean {
        require(ix != nullValue)
        if (table[linearProbe(ix)] != nullValue)
            return false

        if (tableSizeFor(size + 1) > table.size) {
            val old = table
            table = IntArray(tableSizeFor(size + 1))
            if (nullValue != 0)
                table.forEachIndexed { i, _ -> table[i] = nullValue }
            size = 0
            for (i in old.indices)
                if (old[i] != nullValue) add(old[i])
        }
        size++
        table[linearProbe(ix)] = ix
        return true
    }

    override fun remove(ix: Int): Boolean {
        var i = linearProbe(ix)
        if (table[i] == nullValue) return false
        size--

        var j = i
        table[i] = nullValue
        while (true) {
            j = (j + 1) % table.size
            if (table[j] == nullValue) break
            val k = abs(hash(table[j])) % table.size
            if ((j > i && (k <= i || k > j)) || (j < i && (k <= i && k > j))) {
                table[i] = table[j]
                i = j
            }
            table[i] = nullValue
        }
        return true
    }

    private fun linearProbe(ix: Int): Int {
        var j = abs(hash(ix)) % table.size
        while ((table[j] != nullValue && table[j] != ix))
            j = (j + 1) % table.size
        return j
    }
}

class IntList private constructor(private var array: IntArray, size: Int) : MutableIntCollection {

    constructor(initialSize: Int = 4) : this(IntArray(initialSize), 0)
    constructor(array: IntArray) : this(array, array.size)

    override var size: Int = size
        private set

    override fun copy() = IntList(array.copyOf(), size)

    override fun clear() {
        size = 0
    }

    override operator fun contains(ix: Int) = indexOf(ix) >= 0

    operator fun get(index: Int) = array[index]

    fun indexOf(ix: Int): Int {
        for (i in 0 until size)
            if (array[i] == ix) return i
        return -1
    }

    override fun toArray() = array.copyOfRange(0, size)

    override fun map(transform: (Int) -> Int) =
            IntList(IntArray(size) { i ->
                transform(this.array[i])
            })

    override fun iterator() = object : IntIterator() {
        private var ptr = 0
        override fun hasNext() = ptr < size
        override fun nextInt(): Int {
            if (ptr >= size) throw NoSuchElementException()
            return array[ptr++]
        }
    }

    override fun permutation(rng: Random) = object : IntIterator() {
        private var ptr = 0
        private var perm = IntPermutation(size, rng)
        override fun hasNext() = ptr < size
        override fun nextInt(): Int {
            if (ptr >= size) throw NoSuchElementException()
            return array[perm.encode(ptr++)]
        }
    }

    override fun random(rng: Random) = array[rng.nextInt(size)]

    override fun add(ix: Int): Boolean {
        if (array.size == size)
            array = array.copyOf(max(array.size, 1) * 2)
        array[size++] = ix
        return true
    }

    fun removeAt(ix: Int): Int {
        val v = array[ix]
        size--
        for (i in ix until size)
            array[i] = array[i + 1]
        array[size] = 0
        return v
    }

    override fun remove(ix: Int): Boolean {
        val indexOf = indexOf(ix)
        if (indexOf >= 0) {
            size--
            for (i in indexOf until size)
                array[i] = array[i + 1]
            array[size] = 0
            return true
        }
        return false
    }
}

typealias IntEntry = Long

fun IntEntry.key() = this.toInt()
fun IntEntry.value() = (this ushr (Int.SIZE_BITS)).toInt()
fun entry(key: Int, value: Int) = (value.toLong() shl Int.SIZE_BITS) or (key.toLong() and 0xFFFFFFFFL)

/**
 * Specialized open addressing hash map for storing int variables.
 * It uses linear probing (remove operation works only with linear probing).
 */
class IntHashMap private constructor(private var table: LongArray, size: Int, val nullKey: Int = 0) : Iterable<IntEntry> {

    constructor(initialSize: Int = 4, nullKey: Int = 0) : this(LongArray(tableSizeFor(initialSize)) { entry(nullKey, 0) }, 0, nullKey)

    var size: Int = size
        private set

    fun copy() = IntHashMap(table.copyOf(), size, nullKey)

    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0

    fun clear() {
        table = LongArray(tableSizeFor(4))
        if (nullKey != 0) {
            val v = entry(nullKey, 0)
            table.forEachIndexed { i, _ -> table[i] = v }
        }
        size = 0
    }

    fun containsKey(ix: Int) = table[linearProbe(ix)].key() != nullKey

    fun keys(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i].key() != nullKey) result[ix++] = table[i].key()
        return result
    }

    fun values(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i].key() != nullKey) result[ix++] = table[i].value()
        return result
    }

    override fun iterator(): LongIterator {
        return object : LongIterator() {
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextLong(): IntEntry {
                while (table[ptr].key() == nullKey)
                    ptr++
                seen++
                return table[ptr++]
            }
        }
    }

    fun add(entry: IntEntry) = set(entry.key(), entry.value())

    operator fun get(key: Int, default: Int = 0): Int {
        require(key != nullKey)
        val entry = table[linearProbe(key)]
        return if (entry.key() != nullKey) entry.value()
        else default
    }

    operator fun set(key: Int, value: Int): Int {
        require(key != nullKey)
        var ix = linearProbe(key)
        val oldEntry = table[ix]
        if (oldEntry.key() == nullKey) {
            if (tableSizeFor(size + 1) > table.size) {
                val old = table
                table = LongArray(tableSizeFor(size + 1))
                if (nullKey != 0)
                    table.forEachIndexed { i, _ -> table[i] = entry(nullKey, 0) }
                size = 0
                for (i in old.indices)
                    if (old[i].key() != nullKey) add(old[i])
                ix = linearProbe(key)
            }
            size++
            table[ix] = entry(key, value)
        }
        table[ix] = entry(key, value)
        return oldEntry.value()
    }

    fun remove(ix: Int): Int {
        var i = linearProbe(ix)
        if (table[i].key() == nullKey) return 0
        size--

        var j = i
        val oldValue = table[i].value()
        table[i] = entry(nullKey, 0)
        while (true) {
            j = (j + 1) % table.size
            if (table[j].key() == nullKey) break
            val k = abs(hash(table[j].key())) % table.size
            if ((j > i && (k <= i || k > j)) || (j < i && (k <= i && k > j))) {
                table[i] = table[j]
                i = j
            }
            table[i] = entry(nullKey, 0)
        }
        return oldValue
    }

    private fun linearProbe(ix: Int): Int {
        var j = abs(hash(ix)) % table.size
        while ((table[j].key() != nullKey && table[j].key() != ix))
            j = (j + 1) % table.size
        return j
    }
}