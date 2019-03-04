@file:JvmName("IntCollections")

package combo.util

import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.random.Random

interface IntCollection : Iterable<Int> {
    val size: Int
    fun copy(): IntCollection
    operator fun contains(ix: Int): Boolean

    fun toArray(): IntArray {
        val arr = IntArray(size)
        for ((i, v) in this.withIndex()) arr[i] = v
        return arr
    }

    fun map(transform: (Int) -> Int): IntCollection

    override fun iterator(): IntIterator
    fun permutation(rng: Random): IntIterator

    fun random(rng: Random): Int

    companion object {

        fun hash(i: Int): Int {
            var x = i
            x = ((x shr 16) xor x) * 0x45d9f3b
            x = ((x ushr 16) xor x) * 0x45d9f3b
            x = (x ushr 16) xor x
            return x
        }

        const val LOAD_FACTOR = 0.55
        fun tableSizeFor(size: Int): Int {
            return max(2, Int.msb((size.toDouble() / LOAD_FACTOR).toInt()) * 2)
        }
    }
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

fun IntCollection.isEmpty() = size == 0
fun IntCollection.isNotEmpty() = size > 0

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

typealias IntEntry = Long

fun IntEntry.key() = this.toInt()
fun IntEntry.value() = (this ushr (Int.SIZE_BITS)).toInt()
fun entry(key: Int, value: Int) = (value.toLong() shl Int.SIZE_BITS) or (key.toLong() and 0xFFFFFFFFL)
