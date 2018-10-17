@file:JvmName("Transforms")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.*

interface Transform {
    fun inverse(value: Double): Double
    fun apply(value: Double): Double
    fun backtransform(stat: VarianceStatistic): VarianceStatistic

    fun andThen(after: Transform) = object : Transform {
        override fun inverse(value: Double) = this@Transform.inverse(after.inverse(value))
        override fun apply(value: Double) = after.apply(this@Transform.apply(value))
        override fun backtransform(stat: VarianceStatistic) = this@Transform.backtransform(after.backtransform(stat))
    }
}

fun identity() = object : Transform {
    override fun inverse(value: Double) = value
    override fun apply(value: Double) = value
    override fun backtransform(stat: VarianceStatistic) = stat
}


fun shift(by: Double) = object : Transform {
    override fun inverse(value: Double) = value - by
    override fun apply(value: Double) = value + by
    override fun backtransform(stat: VarianceStatistic) =
            FixedVariance(stat.mean - by, stat.variance, stat.nbrWeightedSamples)
}

fun scale(by: Double) = object : Transform {
    override fun inverse(value: Double) = value / by
    override fun apply(value: Double) = value * by
    override fun backtransform(stat: VarianceStatistic) =
            FixedVariance(stat.mean / by, stat.variance / (by * by), stat.nbrWeightedSamples)
}

fun standard(mean: Double, standardDeviation: Double) = shift(-mean).andThen(scale(1 / standardDeviation))

fun arcSine() = object : Transform {
    override fun inverse(value: Double) = sin(value).pow(2)
    override fun apply(value: Double) = asin(sqrt(value))

    override fun backtransform(stat: VarianceStatistic) = stat.let {
        val x = inverse(it.mean)
        val c = cos(it.mean)
        val s = sin(it.mean)
        val x1 = 2 * s * c
        val x2 = 2 * (c * c - s * s)
        taylorStatistics(it.nbrWeightedSamples, x, x1, x2, it.variance)
    }
}

fun log() = object : Transform {
    override fun inverse(value: Double) = exp(value)
    override fun apply(value: Double) = ln(value)

    override fun backtransform(stat: VarianceStatistic) = stat.let {
        val mean = exp(it.mean + 0.5 * it.variance)
        val variance = (exp(it.variance) - 1) * exp(2 * it.mean + it.variance)
        FixedVariance(mean, variance, it.nbrWeightedSamples)
    }
}

fun squareRoot() = object : Transform {
    override fun inverse(value: Double) = value * value
    override fun apply(value: Double) = sqrt(value)

    override fun backtransform(stat: VarianceStatistic) =
            taylorStatistics(stat.nbrWeightedSamples,
                    inverse(stat.mean),
                    2 * stat.mean,
                    2.0, stat.variance)
}

fun logit() = object : Transform {
    override fun apply(value: Double): Double {
        return 1 / (1 + exp(-value))
    }

    override fun inverse(value: Double): Double {
        return -ln(1 / value - 1)
    }

    override fun backtransform(stat: VarianceStatistic): VarianceStatistic = TODO("Backtransform not supported")
}

fun clogLog() = object : Transform {
    override fun inverse(value: Double): Double {
        return 1 - exp(-exp(value))
    }

    override fun apply(value: Double): Double {
        return ln(-ln(1 - value))
    }

    override fun backtransform(stat: VarianceStatistic): VarianceStatistic = TODO("Backtransform not supported")
}

fun inverse() = object : Transform {
    override fun inverse(value: Double): Double {
        return 1 / value
    }

    override fun apply(value: Double): Double {
        return 1 / value
    }

    override fun backtransform(stat: VarianceStatistic): VarianceStatistic {
        val m = stat.mean
        val v = stat.variance
        val x = inverse(m)
        val x1 = -1 / (m * m)
        val x2 = 2 / (m * m * m)
        return taylorStatistics(stat.nbrWeightedSamples, x, x1, x2, v)
    }
}

fun negativeInverse() = object : Transform {
    override fun inverse(value: Double): Double {
        return -1 / value
    }

    override fun apply(value: Double): Double {
        return -1 / value
    }

    override fun backtransform(stat: VarianceStatistic): VarianceStatistic = TODO("Backtransform not supported")
}

fun inverseSquared() = object : Transform {
    override fun inverse(value: Double): Double {
        return 1 / sqrt(value)
    }

    override fun apply(value: Double): Double {
        return 1 / (value * value)
    }

    override fun backtransform(stat: VarianceStatistic): VarianceStatistic {
        val m = stat.mean
        val v = stat.variance
        val x = inverse(m)
        val x1 = -1 / (2 * sqrt(m) * m)
        val x2 = 3 / (4 * sqrt(m) * m * m)
        return taylorStatistics(stat.nbrWeightedSamples, x, x1, x2, v)
    }
}


private fun taylorStatistics(n: Double, x: Double, x1: Double, x2: Double, v: Double): VarianceStatistic {
    val mean = x + v / 2 * x2
    val variance = x1 * x1 * v
    return FixedVariance(mean, variance, n)
}
