@file:JvmName("Transforms")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.*

interface Transform {
    fun inverse(value: Double): Double
    fun apply(value: Double): Double
    fun backtransform(stat: Estimator): Estimator

    fun andThen(after: Transform) = object : Transform {
        override fun inverse(value: Double) = this@Transform.inverse(after.inverse(value))
        override fun apply(value: Double) = after.apply(this@Transform.apply(value))
        override fun backtransform(stat: Estimator) = this@Transform.backtransform(after.backtransform(stat))
    }
}

object IdentityTransform : Transform {
    override fun inverse(value: Double) = value
    override fun apply(value: Double) = value
    override fun backtransform(stat: Estimator) = stat
}


class ShiftTransform(val by: Double) : Transform {
    override fun inverse(value: Double) = value - by
    override fun apply(value: Double) = value + by
    override fun backtransform(stat: Estimator) =
            FixedVariance(stat.mean - by, stat.variance, stat.nbrWeightedSamples)
}

class ScaleTransform(val by: Double) : Transform {
    override fun inverse(value: Double) = value / by
    override fun apply(value: Double) = value * by
    override fun backtransform(stat: Estimator) =
            FixedVariance(stat.mean / by, stat.variance / (by * by), stat.nbrWeightedSamples)
}

object ArcSineTransform : Transform {
    override fun inverse(value: Double) = sin(value).pow(2)
    override fun apply(value: Double) = asin(sqrt(value))

    override fun backtransform(stat: Estimator) = stat.let {
        val x = inverse(it.mean)
        val c = cos(it.mean)
        val s = sin(it.mean)
        val x1 = 2 * s * c
        val x2 = 2 * (c * c - s * s)
        taylorStatistics(it.nbrWeightedSamples, x, x1, x2, it.variance)
    }
}

object LogTransform : Transform {
    override fun inverse(value: Double) = exp(value)
    override fun apply(value: Double) = ln(value)

    override fun backtransform(stat: Estimator) = stat.let {
        val mean = exp(it.mean + 0.5 * it.variance)
        val variance = (exp(it.variance) - 1) * exp(2 * it.mean + it.variance)
        FixedVariance(mean, variance, it.nbrWeightedSamples)
    }
}

object SquareRootTransform : Transform {
    override fun inverse(value: Double) = value * value
    override fun apply(value: Double) = sqrt(value)

    override fun backtransform(stat: Estimator) =
            taylorStatistics(stat.nbrWeightedSamples,
                    inverse(stat.mean),
                    2 * stat.mean,
                    2.0, stat.variance)
}

object LogitTransform : Transform {
    override fun apply(value: Double): Double {
        return 1 / (1 + exp(-value))
    }

    override fun inverse(value: Double): Double {
        return -ln(1 / value - 1)
    }

    override fun backtransform(stat: Estimator): Estimator = TODO("Backtransform not supported")
}

object ClogLogTransform : Transform {
    override fun inverse(value: Double): Double {
        return 1 - exp(-exp(value))
    }

    override fun apply(value: Double): Double {
        return ln(-ln(1 - value))
    }

    override fun backtransform(stat: Estimator): Estimator = TODO("Backtransform not supported")
}

object InverseTransform : Transform {
    override fun inverse(value: Double): Double {
        return 1 / value
    }

    override fun apply(value: Double): Double {
        return 1 / value
    }

    override fun backtransform(stat: Estimator): Estimator {
        val m = stat.mean
        val v = stat.variance
        val x = inverse(m)
        val x1 = -1 / (m * m)
        val x2 = 2 / (m * m * m)
        return taylorStatistics(stat.nbrWeightedSamples, x, x1, x2, v)
    }
}

object NegativeInverseTransform : Transform {
    override fun inverse(value: Double): Double {
        return -1 / value
    }

    override fun apply(value: Double): Double {
        return -1 / value
    }

    override fun backtransform(stat: Estimator): Estimator = TODO("Backtransform not supported")
}

object InverseSquaredTransform : Transform {
    override fun inverse(value: Double): Double {
        return 1 / sqrt(value)
    }

    override fun apply(value: Double): Double {
        return 1 / (value * value)
    }

    override fun backtransform(stat: Estimator): Estimator {
        val m = stat.mean
        val v = stat.variance
        val x = inverse(m)
        val x1 = -1 / (2 * sqrt(m) * m)
        val x2 = 3 / (4 * sqrt(m) * m * m)
        return taylorStatistics(stat.nbrWeightedSamples, x, x1, x2, v)
    }
}

private fun taylorStatistics(n: Double, x: Double, x1: Double, x2: Double, v: Double): Estimator {
    val mean = x + v / 2 * x2
    val variance = x1 * x1 * v
    return FixedVariance(mean, variance, n)
}
