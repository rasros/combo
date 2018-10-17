package combo.util

actual class ConcurrentLong actual constructor(private var value: Long) {

    actual fun getAndIncrement(): Long {
        return value++
    }

    actual fun compareAndSet(expect: Long, update: Long): Boolean {
        return if (expect == value) {
            value = update
            true
        } else false
    }

    actual fun get() = value
}

actual class ConcurrentInteger actual constructor(private var value: Int) {

    actual fun getAndIncrement(): Int {
        return value++
    }

    actual fun compareAndSet(expect: Int, update: Int): Boolean {
        return if (expect == value) {
            value = update
            true
        } else false
    }

    actual fun get() = value
}
