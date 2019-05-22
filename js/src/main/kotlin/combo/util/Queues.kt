package combo.util

import combo.math.IntPermutation
import kotlin.random.Random

actual class RandomConcurrentBuffer<E> actual constructor(private val maxSize: Int) {

    private val list = ArrayList<E>()

    actual fun get(rng: Random, filter: (E) -> Boolean, create: () -> E): E {
        val e = if (list.size < maxSize) null
        else IntPermutation(list.size, rng).map { list[it] }.firstOrNull(filter)
        return e ?: create().also {
            add(rng, it)
        }
    }

    actual fun add(rng: Random, e: E) {
        if (list.size < maxSize) list.add(e)
        else list[rng.nextInt(list.size)] = e
    }

    actual fun find(predicate: (E) -> Boolean) = list.find(predicate)
    actual fun forEach(action: (E) -> Unit) = list.forEach(action)
}
