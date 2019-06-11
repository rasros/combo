package combo.util

import java.util.concurrent.atomic.AtomicInteger as JavaAtomicInt
import java.util.concurrent.atomic.AtomicLong as JavaAtomicLong

actual typealias AtomicLong = java.util.concurrent.atomic.AtomicLong
actual typealias AtomicInt = java.util.concurrent.atomic.AtomicInteger
actual typealias AtomicReference<V> = java.util.concurrent.atomic.AtomicReference<V>
