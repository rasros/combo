package combo.util

import kotlin.math.abs

/**
 * Specialized open addressing hash map for storing int variables.
 * It uses linear probing (remove operation works only with linear probing).
 */
class IntIntHashMap private constructor(private var table: LongArray, size: Int, val nullKey: Int = 0) : Iterable<IntEntry> {

    constructor(initialSize: Int = 4, nullKey: Int = 0) : this(LongArray(IntCollection.tableSizeFor(initialSize)) { entry(nullKey, 0) }, 0, nullKey)

    var size: Int = size
        private set

    fun isEmpty() = size == 0

    fun copy() = IntIntHashMap(table.copyOf(), size, nullKey)

    fun clear() {
        table = LongArray(IntCollection.tableSizeFor(4))
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
        assert(key != nullKey)
        val entry = table[linearProbe(key)]
        return if (entry.key() != nullKey) entry.value()
        else default
    }

    operator fun set(key: Int, value: Int): Int {
        assert(key != nullKey)
        var ix = linearProbe(key)
        val oldEntry = table[ix]
        if (oldEntry.key() == nullKey) {
            if (IntCollection.tableSizeFor(size + 1) > table.size) {
                val old = table
                table = LongArray(IntCollection.tableSizeFor(size + 1))
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

    fun remove(ix: Int, default: Int = 0): Int {
        var i = linearProbe(ix)
        if (table[i].key() == nullKey) return default
        size--

        var j = i
        val oldValue = table[i].value()
        table[i] = entry(nullKey, 0)
        while (true) {
            j = (j + 1) % table.size
            if (table[j].key() == nullKey) break
            val k = abs(IntCollection.hash(table[j].key())) % table.size
            if ((j > i && (k <= i || k > j)) || (j < i && (k <= i && k > j))) {
                table[i] = table[j]
                i = j
            }
            table[i] = entry(nullKey, 0)
        }
        return oldValue
    }

    private fun linearProbe(ix: Int): Int {
        var j = abs(IntCollection.hash(ix)) % table.size
        while ((table[j].key() != nullKey && table[j].key() != ix))
            j = (j + 1) % table.size
        return j
    }

    override fun toString() = "IntIntHashMap($size)"
}