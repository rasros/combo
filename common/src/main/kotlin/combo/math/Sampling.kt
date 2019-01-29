@file:JvmName("Sampling")

package combo.math

import combo.util.AtomicLong
import kotlin.jvm.JvmName
import kotlin.math.*
import kotlin.random.Random

class RandomSequence(val startingSeed: Long) {
    private val permutation = LongPermutation(rng = Random(startingSeed))
    private val counter: AtomicLong = AtomicLong()

    fun next(): Random {
        val count = counter.getAndIncrement()
        return Random(permutation.encode(count))
    }
}

fun Random.nextDoublePos(): Double {
    while (true) {
        val u = nextDouble()
        if (u > 0.0) return u
    }
}

fun Random.nextGaussian(mean: Double = 0.0, std: Double = 1.0): Double {
    var u: Double
    var s: Double
    do {
        u = nextDouble() * 2 - 1
        val v = nextDouble() * 2 - 1
        s = u * u + v * v
    } while (s >= 1.0 || s == 0.0)
    val mul = sqrt(-2.0 * ln(s) / s)
    return mean + std * u * mul
}

fun Random.nextGamma(alpha: Double, beta: Double): Double {
    // Gamma(alpha,lambda) generator using Marsaglia and Tsang method
    // Algorithm 4.33
    if (alpha < 1.0) {
        val u = nextDoublePos()
        return nextGamma(1.0 + alpha, beta) * u.pow(1.0 / alpha);
    } else {
        val d = alpha - 1.0 / 3.0
        val c = (1.0 / 3.0) / sqrt(d)

        var v: Double
        var x: Double

        while (true) {
            do {
                x = nextGaussian()
                v = 1.0 + c * x
            } while (v <= 0)

            v = v * v * v
            val u = nextDoublePos()

            if (u < 1 - 0.0331 * x * x * x * x)
                break

            if (ln(u) < 0.5 * x * x + d * (1 - v + ln(v)))
                break
        }
        return beta * d * v
    }
}

fun Random.nextBeta(alpha: Double, beta: Double): Double {
    val a = nextGamma(alpha, 1.0)
    val b = nextGamma(beta, 1.0)
    return a / (a + b)
}

fun Random.inverseGamma(shape: Double, scale: Double): Double {
    return nextGamma(shape, 1.0 / scale)
}

/**
 * Approximation implementation, it is only used in simulations.
 */
fun Random.nextPoisson(lambda: Double): Int {
    // Knuth's algorithm
    return if (lambda < 30) {
        val p = exp(-lambda)
        var n = 0
        var r = 1.0

        while (n < 1000 * lambda) {
            val u = nextDouble()
            r *= u
            if (r >= p) n++
            else return n
        }
        return n
    } else max(0.0, nextGaussian(lambda + .5, sqrt(lambda))).toInt()
}

/**
 * Approximation implementation, it is only used in simulations.
 */
fun Random.nextBinomial(p: Double, n: Int = 1): Int {
    if (p > 0.5) return n - nextBinomial(1 - p, n)
    val np = n * p
    val np1p = np * (1 - p)
    return if (n <= 5) {
        var sum = 0
        for (i in 0 until n)
            sum += if (nextDouble() < p) 1 else 0
        sum
    } else if (np < 20 && n > 100 && p < 0.05)
        min(n, nextPoisson(np))
    else
        min(n, max(0.0, nextGaussian(np + 0.5, sqrt(np1p))).toInt())
}

fun Random.nextGeometric(p: Double): Int {
    val u = nextDoublePos()
    return if (p == 1.0) 1
    else (ln(u) / ln(1 - p) + 1).toInt()
}

fun Random.nextExponential(rate: Double): Double {
    var u: Double
    do {
        u = nextDouble()
    } while (u == 0.0 || u == 1.0)
    return -ln(u) / rate
}
