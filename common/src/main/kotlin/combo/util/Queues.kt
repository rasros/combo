package combo.util

import kotlin.math.min
import kotlin.random.Random

class ArrayQueue<E> : Iterable<E> {

    @Suppress("UNCHECKED_CAST")
    private var array: Array<E?> = arrayOfNulls<Any?>(4) as Array<E?>
    private var write = 0
    private var read = 0

    private var mask = array.size - 1

    fun add(e: E) {
        array[write] = e
        write = (write + 1) and mask
        if (write == read) {
            write = array.size
            val breakPoint = array.size - read
            @Suppress("UNCHECKED_CAST")
            val a: Array<E?> = arrayOfNulls<Any?>(array.size shl 1) as Array<E?>
            for (i in read until read + breakPoint) a[i - read] = array[i]
            for (i in 0 until read) a[breakPoint + i] = array[i]
            array = a
            read = 0
            mask = array.size - 1
        }
    }

    fun remove() = poll() ?: throw NoSuchElementException()

    fun poll(): E? {
        val e = array[read] ?: return null
        array[read] = null
        read = (read + 1) and mask
        return e
    }

    fun peek(): E? = array[read]

    fun addAll(es: Iterable<E>) {
        for (e in es)
            add(e)
    }

    override fun iterator() = object : Iterator<E> {
        var ptr = read
        override fun hasNext() = array[ptr] != null
        override fun next(): E {
            if (array[ptr] == null) throw NoSuchElementException()
            val value = array[ptr]
            ptr = (ptr + 1) and mask
            return value!!
        }
    }

    val size: Int get() = if (write >= read) write - read
    else array.size - (read - write)
}

class CircleBuffer<E>(bufferSize: Int) : Iterable<E> {

    @Suppress("UNCHECKED_CAST")
    private var array: Array<E?> = arrayOfNulls<Any?>(bufferSize) as Array<E?>
    private var write = 0

    fun add(e: E) {
        array[write] = e
        size = min(array.size, size + 1)
        write = (write + 1) % array.size
    }

    override fun iterator() = object : Iterator<E> {
        private var ptr = 0
        override fun hasNext() = ptr < size
        override fun next(): E {
            if (ptr >= size) throw NoSuchElementException()
            return array[ptr++]!!
        }
    }

    var size: Int = 0
        private set
}

expect class RandomConcurrentBuffer<E>(maxSize: Int) {
    fun get(rng: Random, filter: (E) -> Boolean, create: () -> E): E
    fun add(rng: Random, e: E)
    fun find(predicate: (E) -> Boolean): E?
    fun forEach(action: (E) -> Unit)
}
