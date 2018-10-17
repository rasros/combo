package combo.math

import java.util.*

actual class Rng actual constructor(actual val seed: Long) {
    private val rng: Random = Random(seed)
    actual fun double() = rng.nextDouble()
    actual fun gaussian() = rng.nextGaussian()
    actual fun int(bound: Int) = rng.nextInt(bound)
    // java.util.Random(long) uses only 48 bits
    actual fun boolean(): Boolean = rng.nextBoolean()
}