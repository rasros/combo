package combo.util

actual interface Condition {
    actual fun await()
    actual fun signal()
}

actual interface ReadWriteLock {
    actual fun readLock(): Lock
    actual fun writeLock(): Lock
}

actual interface Lock {
    actual fun lock()
    actual fun unlock()
    actual fun newCondition(): Condition
    actual fun tryLock(): Boolean
}

actual class ReentrantLock : Lock {

    private val condition = object : Condition {
        override fun await() {}
        override fun signal() {}
    }

    actual fun hasWaiters(condition: Condition) = false
    fun getHoldCount() = 0

    override fun newCondition(): Condition = condition
    override fun lock() {}
    override fun unlock() {}
    override fun tryLock() = true
}

actual class ReentrantReadWriteLock : ReadWriteLock {
    private val lock = ReentrantLock()
    override fun readLock() = lock
    actual override fun writeLock() = lock
}

actual typealias ReentrantWriteLock = ReentrantLock
