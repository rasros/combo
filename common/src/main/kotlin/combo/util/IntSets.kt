@file:JvmName("IntSets")

package combo.util

import combo.sat.Ix
import combo.util.IntSet.Companion.tableSizeFor
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

interface IntSet : Iterable<Ix> {

    fun clear()

    fun toArray(): IntArray

    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0
    val size: Int

    override fun iterator(): IntIterator

    fun random(rng: Random = Random.Default): Ix

    operator fun contains(ix: Ix): Boolean

    fun add(ix: Ix): Boolean

    fun addAll(ixs: IntArray) {
        for (i in ixs.indices)
            add(ixs[i])
    }

    fun addAll(ixs: Iterable<Int>) {
        ixs.forEach { this.add(it) }
    }

    fun remove(ix: Ix): Boolean

    companion object {
        fun tableSizeFor(size: Int): Int {
            return max(2, msb(size) * 2)
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
}


/**
 * Specialized hash table for storing index variables. Storing only positive (>=0) integers.
 * Also supports getting a random value from the index. It uses linear probing.
 *
 * TODO better remove through Knuth's Algorithm R6.4
 */
class HashIntSet(initialSize: Int = 16) : IntSet {

    private companion object {
        private const val REHASH_THRESHOLD = 0.5
        private const val LOAD_FACTOR = 0.7
        private const val MIN_RESIZE = 64

        @JvmStatic
        fun hash(i: Int): Int {
            var x = i
            x = ((x shr 16) xor x) * 0x45d9f3b
            x = ((x ushr 16) xor x) * 0x45d9f3b
            x = (x ushr 16) xor x
            return x
        }
    }

    override var size: Int = 0
        private set

    private var table: IntArray = IntArray(tableSizeFor((initialSize.toDouble() / LOAD_FACTOR).toInt())) { -1 }
    private var occupied = 0

    override fun clear() {
        table.forEachIndexed { i, _ -> table[i] = -1 }
        size = 0
        occupied = 0
    }

    override operator fun contains(ix: Int) = table[probeFindItem(ix)] >= 0

    override fun toArray(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices) {
            if (table[i] >= 0) {
                result[ix++] = table[i]
            }
        }
        return result
    }

    fun asSequence() = table.asSequence().filter { it >= 0 }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            private var seen = 0
            private var ptr = 0
            override fun hasNext() = seen < size

            override fun nextInt(): Int {
                while (table[ptr] < 0)
                    ptr++
                seen++
                return table[ptr]
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

    override fun add(ix: Int): Boolean {
        require(ix >= 0)
        if (isInSet(table, probeFindItem(ix)))
            return false
        table[probAddSlot(ix)] = ix
        size++
        occupied++
        if (tableSizeFor((occupied.toDouble() / LOAD_FACTOR).toInt()) > table.size)
            rehash()
        return true
    }

    private fun rehash() {
        val old = table
        table = IntArray(tableSizeFor((size.toDouble() / LOAD_FACTOR).toInt()))
        table.forEachIndexed { i, _ -> table[i] = -1 }
        size = 0
        occupied = 0
        for (i in old.indices)
            if (old[i] >= 0) add(old[i])
    }

    override fun remove(ix: Int): Boolean {
        val pos = probeFindItem(ix)
        if (table[pos] < 0) return false
        table[pos] = -2
        size--
        if (size > MIN_RESIZE && (occupied - size) / occupied.toDouble() > REHASH_THRESHOLD)
            rehash()
        return true
    }

    private fun probeFindItem(i: Int): Int {
        var k = abs(hash(i) % table.size)
        while ((table[k] >= 0 && table[k] != i) || table[k] == -2)
            k = (k + 1) % table.size
        return k
    }

    private fun probAddSlot(i: Int): Int {
        var k = abs(hash(i) % table.size)
        while ((table[k] >= 0 && table[k] != i))
            k = (k + 1) % table.size
        return k
    }

    private fun isInSet(table: IntArray, pos: Int): Boolean {
        return table[pos] >= 0
    }
}

open class ArrayIntSet constructor(protected var array: IntArray, size: Int) : IntSet {

    constructor(initialSize: Int = 16) : this(IntArray(initialSize), 0)

    final override var size: Int = 0
        protected set

    init {
        this.size = size
    }

    override fun clear() {
        size = 0
    }

    override operator fun contains(ix: Ix) = indexOf(ix) >= 0

    open fun indexOf(ix: Ix): Ix {
        for (i in 0 until size) if (array[i] == ix) return i
        return -1
    }

    override fun toArray() = array.sliceArray(0 until size)

    override fun iterator() = object : IntIterator() {
        private var ptr = 0
        override fun hasNext() = ptr < size
        override fun nextInt() = array[ptr++]
    }

    override fun random(rng: Random) = array[rng.nextInt(size)]

    override fun add(ix: Ix): Boolean {
        require(ix >= 0)
        if (contains(ix)) return false
        if (array.size == size + 1)
            array = array.copyOf(IntSet.tableSizeFor(size + 1))
        array[size++] = ix
        return true
    }

    override fun remove(ix: Ix): Boolean {
        val i = indexOf(ix)
        if (i >= 0) {
            size--
            for (j in i until size) {
                array[j] = array[j + 1]
            }
            return true
        }
        return false
    }
}

class SortedArrayIntSet constructor(array: IntArray, size: Int) : ArrayIntSet(array, size) {
    constructor(initialSize: Int = 16) : this(IntArray(initialSize), 0)

    override fun indexOf(ix: Ix) = binarySearch(ix)

    override fun add(ix: Ix): Boolean {
        require(ix >= 0)
        if (array.size == size)
            array = array.copyOf(IntSet.tableSizeFor(size + 1))
        val i = binarySearch(ix)
        if (i >= 0) return false
        for (j in size downTo (-i))
            array[j] = array[j - 1]
        array[-i - 1] = ix
        size++
        return true
    }

    private fun binarySearch(ix: Int): Int {
        if (size <= 64) {
            for (i in 0 until size) {
                if (ix == array[i]) return i
                else if (array[i] > ix) return -(i + 1)
            }
            return -(size + 1)
        } else {
            var low = 0
            var high = size - 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                val midVal = array[mid]
                when {
                    midVal < ix -> low = mid + 1
                    midVal > ix -> high = mid - 1
                    else -> return mid
                }
            }
            return -(low + 1)
        }
    }
}
