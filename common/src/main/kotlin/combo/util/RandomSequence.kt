package combo.util

import kotlin.random.Random

/**
 * This is an alternative for thread local random which also works for javascript.
 */
class RandomSequence(val randomSeed: Int) {
    private val counter = AtomicInt(randomSeed)
    fun next() = Random(counter.getAndIncrement())
}