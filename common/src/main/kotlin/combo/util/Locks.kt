@file:JvmName("Locks")

package combo.util

import kotlin.jvm.JvmName

expect interface Condition {
    fun await()
    fun signal()
    fun signalAll()
}

expect interface Lock {
    fun lock()
    fun unlock()
    fun tryLock(): Boolean
    fun newCondition(): Condition
}

expect interface ReadWriteLock {
    fun readLock(): Lock
    fun writeLock(): Lock
}

expect class ReentrantLock() : Lock
expect class ReentrantReadWriteLock() : ReadWriteLock

inline fun <T> Lock.withLock(action: () -> T): T {
    lock()
    try {
        return action()
    } finally {
        unlock()
    }
}

inline fun <T> ReadWriteLock.read(action: () -> T): T {
    val rl = readLock()
    rl.lock()
    try {
        return action()
    } finally {
        rl.unlock()
    }
}

inline fun <T> ReadWriteLock.write(action: () -> T): T {
    val wl = writeLock()
    wl.lock()
    try {
        return action()
    } finally {
        wl.unlock()
    }
}
