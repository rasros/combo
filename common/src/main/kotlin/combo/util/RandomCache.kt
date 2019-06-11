package combo.util

import combo.math.IntPermutation
import kotlin.random.Random

class RandomCache<E>(private val maxSize: Int) {

    private val readWriteLock = ReentrantReadWriteLock()
    private val readLock = readWriteLock.readLock()
    private val writeLock = readWriteLock.writeLock()
    private val list = ArrayList<E>()

    fun get(rng: Random, filter: (E) -> Boolean, create: () -> E): E {
        readLock.lock()
        val e = try {
            if (list.size < maxSize) null
            else IntPermutation(list.size, rng).map { list[it] }.firstOrNull(filter)
        } finally {
            readLock.unlock()
        }
        return e ?: create().also {
            add(rng, it)
        }
    }

    fun add(rng: Random, e: E) {
        writeLock.lock()
        try {
            if (list.size < maxSize) list.add(e)
            else list[rng.nextInt(list.size)] = e
        } finally {
            writeLock.unlock()
        }
    }

    fun forEach(action: (E) -> Unit) {
        readLock.lock()
        try {
            list.forEach(action)
        } finally {
            readLock.unlock()
        }
    }

    fun find(predicate: (E) -> Boolean): E? {
        readLock.lock()
        try {
            return list.find(predicate)
        } finally {
            readLock.unlock()
        }
    }
}