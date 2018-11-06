package combo.util

import combo.sat.Ix
import kotlin.jvm.JvmStatic
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Specialized hash table for storing index variables. Storing only positive (>=0) integers.
 * Also supports getting a random value from the index. It uses linear probing.
 *
 * TODO better remove through Knuth's Algorithm R6.4
 */
class IndexSet(initialSize: Int = 16) : Iterable<Ix> {

    override fun iterator() = asSequence().iterator()

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

    private var rehashBig = 0
    private var rehashSmall = 0

    var size: Int = 0
        private set

    private var table: IntArray = IntArray(tableSizeFor(initialSize)) { -1 }
    private var occupied = 0

    fun clear() {
        table.forEachIndexed { i, _ -> table[i] = -1 }
        size = 0
        occupied = 0
    }

    operator fun contains(element: Int) = table[probeFindItem(element)] >= 0

    fun toArray(): IntArray {
        val result = IntArray(size)
        var ix = 0
        for (i in table.indices) {
            if (table[i] >= 0) {
                result[ix++] = table[i]
            }
        }
        return result
    }

    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0

    fun asSequence() = table.asSequence().filter { it >= 0 }

    fun random(rng: Random = Random.Default): Int {
        require(size > 0)
        var k = rng.nextInt(table.size)
        while (table[k] < 0) {
            k = (k + 1) % table.size
        }
        return table[k]
    }

    fun addAll(ls: IntArray) {
        for (i in ls.indices) {
            add(ls[i])
        }
    }

    fun addAll(s: Iterable<Int>) {
        s.forEach { this.add(it) }
    }

    fun add(l: Int): Boolean {
        require(l >= 0)
        if (isInSet(table, probeFindItem(l)))
            return false
        table[probAddSlot(l)] = l
        size++
        occupied++
        if (tableSizeFor(occupied) > table.size) {
            rehash()
            rehashBig++
        }
        return true
    }

    private fun rehash() {
        val old = table
        table = IntArray(tableSizeFor(size))
        table.forEachIndexed { i, _ -> table[i] = -1 }
        size = 0
        occupied = 0
        for (i in old.indices)
            if (old[i] >= 0) add(old[i])
    }

    fun remove(i: Int): Boolean {
        val pos = probeFindItem(i)
        if (table[pos] < 0) return false
        table[pos] = -2
        size--
        if (size > MIN_RESIZE && (occupied - size) / occupied.toDouble() > REHASH_THRESHOLD) {
            rehash()
            rehashSmall++
        }
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

    private fun tableSizeFor(size: Int): Int {
        return max(2, msb((size / LOAD_FACTOR).toInt()) * 2)
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
