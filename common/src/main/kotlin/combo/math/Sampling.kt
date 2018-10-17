@file:JvmName("Sampling")

package combo.math

import combo.util.ConcurrentLong
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.math.*

// TODO Random will be added to kotlin-std in 1.3
expect class Rng(seed: Long = nanos()) {
    fun double(): Double
    fun gaussian(): Double
    fun int(bound: Int = Int.MAX_VALUE): Int
    fun boolean(): Boolean
    val seed: Long
}

class RngSequence(startingSeed: Long) {
    private val permutation = LongPermutation(rng = Rng(startingSeed))
    private val counter: ConcurrentLong = ConcurrentLong()

    fun next(): Rng {
        val count = counter.getAndIncrement()
        return Rng(permutation.encode(count))
    }
}


fun Rng.long(bound: Long = Long.MAX_VALUE): Long {
    return abs((this.int() shl 32) + this.int()) % bound
}

fun Rng.double(min: Double = 0.0, max: Double = 1.0): Double {
    return min + double() * max
}

tailrec fun Rng.gaussian(mean: Double = 0.0, std: Double = 1.0,
                         min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): Double {
    val next = mean + gaussian() * std
    if (next < min || next > max) {
        return gaussian(mean, std, min, max)
    } else {
        return next
    }
}

/**
 * @param Cholesky decomposition
 * @param scale     L'*L*scale = scale*Sigma^-1
 */
fun Rng.multiGaussian(means: Vector, L: Matrix, scale: Double = 1.0) =
        means + Vector(DoubleArray(means.size) { gaussian() * scale }) * L

fun Rng.gamma(shape: Double, scale: Double = 1.0): Double {
    fun randomForShapeGreaterThan1(shape: Double): Double {
        val a = sqrt(2.0 * shape - 1.0)
        val b = shape - ln(4.0)
        val q = shape + 1 / a
        val d = 1 + ln(4.5)
        for (i in 1..20) {
            val u1 = double()
            val u2 = double()
            val v = a * ln(u1 / (1 - u1))
            val y = shape * exp(v)
            val z = u1 * u1 * u2
            val w = b + q * v - y
            if (w + d - 4.5 * z >= 0 || w >= ln(z)) {
                return y
            }
        }
        return gaussian(shape, sqrt(shape), min = 0.0)
    }

    fun randomForShapeLessThan1(shape: Double): Double {
        val b = (E + shape) / E
        for (i in 1..10) {
            val p = double(0.0, b)
            if (p > 1) {
                val y = -ln((b - p) / shape)
                if (double() <= y.pow(shape - 1.0)) {
                    return y
                }
            }
            val y = p.pow(1 / shape)
            if (double() <= exp(-y)) {
                return y
            }
        }
        return gaussian(shape, sqrt(shape), min = 0.0)
    }
    return scale *
            if (shape > 30) {
                gaussian(shape, sqrt(shape), min = 0.0)
            } else if (shape > 1) {
                randomForShapeGreaterThan1(shape)
            } else if (shape < 1) {
                randomForShapeLessThan1(shape)
            } else {
                -ln(1 - double())
            }
}

fun Rng.beta(alpha: Double, beta: Double): Double {
    val a = gamma(alpha)
    val b = gamma(beta)
    return a / (a + b)
}

fun Rng.inverseGamma(shape: Double, scale: Double): Double {
    return gamma(shape, 1.0 / scale)
}

fun Rng.poisson(lambda: Double): Int {
    // Knuth's algorithm
    return if (lambda < 30) {
        val l = exp(-lambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= double()
        } while (p > l)
        k - 1
    } else {
        round(gaussian(lambda, sqrt(lambda), min = 0.0)).toInt()
    }
}

fun Rng.binomial(p: Double, n: Int = 1): Int {
    return if (n < 50) {
        var sum = 0
        for (i in 0 until n)
            sum += if (double() < p) 1 else 0
        sum
    } else {
        round(gaussian(n * p, sqrt(n * p * (1 - p)), min = 0.0)).toInt()
    }
}

fun Rng.geometric(p: Double): Int {
    var u: Double
    do {
        u = double()
    } while (u == 0.0)
    return floor(ln(u) / ln(1 - p)).toInt()
}

fun Rng.exponential(rate: Double): Double {
    var u: Double
    do {
        u = double()
    } while (u == 0.0 || u == 1.0)
    return -ln(u) / rate
}
