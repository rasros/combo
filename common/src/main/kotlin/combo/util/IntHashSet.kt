package combo.util

import combo.math.IntPermutation
import kotlin.math.abs
import kotlin.random.Random

/**
 * Specialized open addressing hash table for storing int variables.
 * It uses linear probing (remove operation works only with linear probing).
 */
class IntHashSet private constructor(private var table: IntArray, size: Int, val nullValue: Int = 0) : MutableIntCollection {

    /**
     * @param initialSize ensure the capacity of this many items
     * @param nullValue use this to represent null values. This value cannot be added to the set.
     */
    constructor(initialSize: Int = 4, nullValue: Int = 0) : this(IntArray(IntCollection.tableSizeFor(initialSize)) { nullValue }, 0, nullValue)

    override var size: Int = size
        private set

    override fun copy() = IntHashSet(table.copyOf(), size, nullValue)

    override fun clear() {
        table = IntArray(IntCollection.tableSizeFor(4))
        if (nullValue != 0)
            table.forEachIndexed { i, _ -> table[i] = nullValue }
        size = 0
    }

    override operator fun contains(ix: Int) = table[linearProbe(ix)] != nullValue

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
        assert(ix != nullValue)
        if (table[linearProbe(ix)] != nullValue)
            return false

        if (IntCollection.tableSizeFor(size + 1) > table.size) {
            val old = table
            table = IntArray(IntCollection.tableSizeFor(size + 1))
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
            val k = abs(IntCollection.hash(table[j])) % table.size
            if ((j > i && (k <= i || k > j)) || (j < i && (k <= i && k > j))) {
                table[i] = table[j]
                i = j
            }
            table[i] = nullValue
        }
        return true
    }

    private fun linearProbe(ix: Int): Int {
        var j = abs(IntCollection.hash(ix)) % table.size
        while ((table[j] != nullValue && table[j] != ix))
            j = (j + 1) % table.size
        return j
    }

    override fun toString() = "IntHashSet($size)"
}