package combo.util

import kotlin.jvm.JvmSynthetic

class IntHashMap<V> constructor(
        @PublishedApi @JvmSynthetic internal var table: IntIntHashMap,
        @PublishedApi @JvmSynthetic internal var values: ArrayList<V>) {

    constructor(initialSize: Int = 4, nullKey: Int = 0) : this(IntIntHashMap(initialSize, nullKey), ArrayList(initialSize))

    val size: Int get() = values.size
    fun copy() = IntHashMap(table.copy(), ArrayList(values))
    fun isEmpty() = size == 0
    fun isNotEmpty() = size > 0

    fun clear() {
        table.clear()
        values.clear()
    }

    fun containsKey(ix: Int) = table.containsKey(ix)
    fun keys() = table.keys()
    @Suppress("UNCHECKED_CAST")
    fun values(): Array<V> = (values as List<Any>).toTypedArray() as Array<V>

    operator fun get(key: Int): V? {
        val ix = table[key, -1]
        return if (ix >= 0) values[ix] else null
    }

    operator fun set(key: Int, value: V): V? {
        val ix = table[key, -1]
        return if (ix >= 0) values.set(ix, value)
        else {
            table[key] = values.size
            values.add(value)
            null
        }
    }

    fun remove(key: Int): V? {
        val ix = table.remove(key, -1)
        return if (ix >= 0) values.removeAt(ix)
        else null
    }

    inline fun <U> map(transform: (V) -> U): IntHashMap<U> = IntHashMap(table.copy(), values.mapTo(ArrayList(), transform))

    override fun toString() = "IntHashMap($size)"
}
