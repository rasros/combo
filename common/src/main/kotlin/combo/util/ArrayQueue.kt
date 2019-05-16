package combo.util

import kotlin.jvm.Transient


class ArrayQueue<E> {

    @Suppress("UNCHECKED_CAST")
    private var array: Array<E?> = Array<Any?>(4) { null } as Array<E?>
    private var write = 0
    private var read = 0

    @Transient
    private var mask = array.size - 1

    fun add(e: E) {
        if ((size + 1) == array.size) {
            array = array.copyOf(array.size * 2)
            mask = array.size - 1
        }
        array[write] = e
        write = (write + 1) and mask
        size++
    }

    fun remove() = poll() ?: throw NoSuchElementException()

    fun poll(): E? {
        return if (size == 0) null
        else {
            array[read].also {
                array[read] = null
                read = (read + 1) and mask
                size--
            }
        }
    }

    fun peek(): E? = array[read]

    fun addAll(es: Iterable<E>) {
        for (e in es)
            add(e)
    }

    var size: Int = 0
        private set
}