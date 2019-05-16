package combo.util

import combo.math.IntPermutation
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.random.Random
import java.util.concurrent.atomic.AtomicInteger as JavaAtomicInt
import java.util.concurrent.atomic.AtomicLong as JavaAtomicLong

actual class AtomicLong actual constructor(value: Long) {
    private val value: JavaAtomicLong = JavaAtomicLong(value)
    actual fun inc() = value.getAndIncrement()
    actual operator fun plus(value: Long) = this.value.addAndGet(value)
    actual fun get() = value.get()
}

actual class AtomicInt actual constructor(value: Int) {
    private val value: JavaAtomicInt = JavaAtomicInt(value)
    actual fun inc() = value.getAndIncrement()
    actual operator fun plus(value: Int) = this.value.addAndGet(value)
    actual fun get() = value.get()
}

actual class RandomConcurrentBuffer<E> actual constructor(private val maxSize: Int) {

    private val readWriteLock = ReentrantReadWriteLock()
    private val readLock = readWriteLock.readLock()
    private val writeLock = readWriteLock.writeLock()
    private val list = ArrayList<E>()

    actual fun get(rng: Random, filter: (E) -> Boolean, create: () -> E): E {
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

    actual fun add(rng: Random, e: E) {
        writeLock.lock()
        try {
            if (list.size < maxSize) list.add(e)
            else list[rng.nextInt(list.size)] = e
        } finally {
            writeLock.unlock()
        }
    }

    actual fun forEach(action: (E) -> Unit)  {
        readLock.lock()
        try {
            list.forEach(action)
        } finally {
            readLock.unlock()
        }
    }

    actual fun find(predicate: (E) -> Boolean): E? {
        readLock.lock()
        try {
            return list.find(predicate)
        } finally {
            readLock.unlock()
        }
    }
}