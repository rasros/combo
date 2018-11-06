@file:JvmName("Sampling")

package combo.math

import combo.util.ConcurrentLong
import kotlin.jvm.JvmName
import kotlin.math.*
import kotlin.random.Random

class RandomSequence(startingSeed: Long) {
    private val permutation = LongPermutation(rng = Random(startingSeed))
    private val counter: ConcurrentLong = ConcurrentLong()

    fun next(): Random {
        val count = counter.getAndIncrement()
        return Random(permutation.encode(count))
    }
}

class ExtendedRandom(val rng: Random) {

    private var nextGaussian: Double = 0.0
    private var haveNextGaussian = false

    fun nextGaussian(): Double {
        return if (haveNextGaussian) {
            haveNextGaussian = false
            nextGaussian
        } else {
            val u1 = rng.nextDouble()
            val u2 = rng.nextDouble()
            val r = sqrt(-2.0 * ln(u1))
            val theta = 2 * PI * u2
            haveNextGaussian = true
            nextGaussian = r * cos(theta)
            r * sin(theta)
        }
    }
}

fun ExtendedRandom.nextGaussian(mean: Double = 0.0, std: Double = 1.0,
                        min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): Double {
    val next = mean + nextGaussian() * std
    if (next < min || next > max) {
        return nextGaussian(mean, std, min, max)
    } else {
        return next
    }
}

/**
 * @param Cholesky decomposition
 * @param scale     L'*L*scale = scale*Sigma^-1
 */
fun ExtendedRandom.multiGaussian(means: Vector, L: Matrix, scale: Double = 1.0) =
        means + Vector(DoubleArray(means.size) { nextGaussian() * scale }) * L

fun ExtendedRandom.gamma(shape: Double, scale: Double = 1.0): Double {
    fun randomForShapeGreaterThan1(shape: Double): Double {
        val a = sqrt(2.0 * shape - 1.0)
        val b = shape - ln(4.0)
        val q = shape + 1 / a
        val d = 1 + ln(4.5)
        for (i in 1..20) {
            val u1 = rng.nextDouble()
            val u2 = rng.nextDouble()
            val v = a * ln(u1 / (1 - u1))
            val y = shape * exp(v)
            val z = u1 * u1 * u2
            val w = b + q * v - y
            if (w + d - 4.5 * z >= 0 || w >= ln(z)) {
                return y
            }
        }
        return nextGaussian(shape, sqrt(shape), min = 0.0)
    }

    fun randomForShapeLessThan1(shape: Double): Double {
        val b = (E + shape) / E
        for (i in 1..10) {
            val p = rng.nextDouble(0.0, b)
            if (p > 1) {
                val y = -ln((b - p) / shape)
                if (rng.nextDouble() <= y.pow(shape - 1.0)) {
                    return y
                }
            }
            val y = p.pow(1 / shape)
            if (rng.nextDouble() <= exp(-y)) {
                return y
            }
        }
        return nextGaussian(shape, sqrt(shape), min = 0.0)
    }
    return scale *
            if (shape > 30) {
                nextGaussian(shape, sqrt(shape), min = 0.0)
            } else if (shape > 1) {
                randomForShapeGreaterThan1(shape)
            } else if (shape < 1) {
                randomForShapeLessThan1(shape)
            } else {
                -ln(1 - rng.nextDouble())
            }
}

fun ExtendedRandom.beta(alpha: Double, beta: Double): Double {
    val a = gamma(alpha)
    val b = gamma(beta)
    return a / (a + b)
}

fun ExtendedRandom.inverseGamma(shape: Double, scale: Double): Double {
    return gamma(shape, 1.0 / scale)
}

fun ExtendedRandom.poisson(lambda: Double): Int {
    // Knuth's algorithm
    return if (lambda < 30) {
        val l = exp(-lambda)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= rng.nextDouble()
        } while (p > l)
        k - 1
    } else {
        round(nextGaussian(lambda, sqrt(lambda), min = 0.0)).toInt()
    }
}

fun ExtendedRandom.binomial(p: Double, n: Int = 1): Int {
    return if (n < 50) {
        var sum = 0
        for (i in 0 until n)
            sum += if (rng.nextDouble() < p) 1 else 0
        sum
    } else {
        round(nextGaussian(n * p, sqrt(n * p * (1 - p)), min = 0.0)).toInt()
    }
}

fun ExtendedRandom.geometric(p: Double): Int {
    var u: Double
    do {
        u = rng.nextDouble()
    } while (u == 0.0)
    return floor(ln(u) / ln(1 - p)).toInt()
}

fun ExtendedRandom.exponential(rate: Double): Double {
    var u: Double
    do {
        u = rng.nextDouble()
    } while (u == 0.0 || u == 1.0)
    return -ln(u) / rate
}
