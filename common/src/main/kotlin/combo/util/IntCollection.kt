package combo.util

import combo.math.IntPermutation
import combo.sat.Ix
import combo.util.IntSet.Companion.LOAD_FACTOR
import combo.util.IntSet.Companion.hash
import combo.util.IntSet.Companion.tableSizeFor
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

fun collectionOf(array: IntArray) =
        if (array.size > 20) IntSet().apply { addAll(array) }
        else IntList(array)

interface IntCollection : Iterable<Ix> {

    val size: Int
    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0
    fun copy(): IntCollection
    fun clear()
    operator fun contains(ix: Ix): Boolean
    fun toArray(): IntArray
    fun map(transform: (Int) -> Int): IntCollection

    override fun iterator(): IntIterator
    fun permutation(rng: Random = Random): IntIterator

    fun random(rng: Random = Random.Default): Int
    fun add(ix: Ix): Boolean
    fun addAll(ixs: IntArray) = ixs.fold(false) { any, it -> this.add(it) || any }
    fun addAll(ixs: Iterable<Ix>) = ixs.fold(false) { any, it -> this.add(it) || any }
    fun remove(ix: Ix): Boolean
}

/**
 * Specialized open addressing hash table for storing index variables. Storing only positive (>=0) integers.
 * Also supports getting a random value from the index. It uses linear probing (remove operation works only with linear
 * probing).
 */
class IntSet private constructor(private var table: IntArray, size: Int) : IntCollection {

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

    override var size: Int = size
        private set

    override fun copy() = IntSet(table.copyOf(), size)

    override fun clear() {
        table = table.copyOf(tableSizeFor(4))
        table.forEachIndexed { i, _ -> table[i] = -1 }
        size = 0
    }

    override operator fun contains(ix: Int) = table[linearProbe(ix)] >= 0

    override fun toArray(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices)
            if (table[i] >= 0) result[ix++] = table[i]
        return result
    }

    override fun map(transform: (Int) -> Int): IntCollection {
        val mapped = IntSet(size)
        for (i in table) {
            if (i >= 0) mapped.add(transform(i))
        }
        return mapped
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextInt(): Int {
                seen++
                while (table[ptr] < 0)
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
                seen++
                while (table[perm.encode(ptr)] < 0)
                    ptr = (ptr + 1) % table.size
                return table[perm.encode(ptr)].also {
                    ptr = (ptr + 1) % table.size
                }
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
        size++
        table[linearProbe(ix)] = ix
        return true
    }

    private fun rehash() {
        val old = table
        table = IntArray(tableSizeFor((size.toDouble() / LOAD_FACTOR).toInt()))
        table.forEachIndexed { i, _ -> table[i] = -1 }
        size = 0
        for (i in old.indices)
            if (old[i] >= 0) add(old[i])
    }

    override fun remove(ix: Ix): Boolean {
        var i = linearProbe(ix)
        if (table[i] < 0) return false
        size--

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

class IntList private constructor(private var array: IntArray, size: Int) : IntCollection {

    constructor(initialSize: Int = 4) : this(IntArray(initialSize), 0)
    constructor(array: IntArray) : this(array, array.size)

    override var size: Int = size
        private set

    override fun copy() = IntList(array.copyOf(), size)

    override fun clear() {
        size = 0
    }

    override operator fun contains(ix: Ix) = indexOf(ix) >= 0

    operator fun get(index: Int) = array[index]

    fun indexOf(ix: Int): Int {
        for (i in 0 until size) {
            if (array[i] == ix) return i
        }
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
        override fun nextInt() = array[ptr++]
    }

    override fun permutation(rng: Random) = object : IntIterator() {
        private var ptr = 0
        private var perm = IntPermutation(size, rng)
        override fun hasNext() = ptr < size
        override fun nextInt() = array[perm.encode(ptr++)]
    }

    override fun random(rng: Random) = array[rng.nextInt(size)]

    override fun add(ix: Int): Boolean {
        if (array.size == size)
            array = array.copyOf(array.size * 2)
        array[size++] = ix
        return true
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
