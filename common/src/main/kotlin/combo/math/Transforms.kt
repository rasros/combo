@file:JvmName("Transforms")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.*

interface Transform {
    // TODO need inverse???
    fun inverse(value: Double): Double
    fun apply(value: Double): Double

    fun andThen(after: Transform) = object : Transform {
        override fun inverse(value: Double) = this@Transform.inverse(after.inverse(value))
        override fun apply(value: Double) = after.apply(this@Transform.apply(value))
    }
}

object IdentityTransform : Transform {
    override fun inverse(value: Double) = value
    override fun apply(value: Double) = value
}


class ShiftTransform(val by: Double) : Transform {
    override fun inverse(value: Double) = value - by
    override fun apply(value: Double) = value + by
}

class ScaleTransform(val by: Double) : Transform {
    override fun inverse(value: Double) = value / by
    override fun apply(value: Double) = value * by
}

object ArcSineTransform : Transform {
    override fun inverse(value: Double) = sin(value).pow(2)
    override fun apply(value: Double) = asin(sqrt(value))
}

object LogTransform : Transform {
    override fun inverse(value: Double) = exp(value)
    override fun apply(value: Double) = ln(value)
}

object SquareRootTransform : Transform {
    override fun inverse(value: Double) = value * value
    override fun apply(value: Double) = sqrt(value)
}

object LogitTransform : Transform {
    override fun apply(value: Double): Double {
        return 1 / (1 + exp(-value))
    }

    override fun inverse(value: Double): Double {
        return -ln(1 / value - 1)
    }
}

object ClogLogTransform : Transform {
    override fun inverse(value: Double): Double {
        return 1 - exp(-exp(value))
    }

    override fun apply(value: Double): Double {
        return ln(-ln(1 - value))
    }
}

object InverseTransform : Transform {
    override fun inverse(value: Double): Double {
        return 1 / value
    }

    override fun apply(value: Double): Double {
        return 1 / value
    }
}

object NegativeInverseTransform : Transform {
    override fun inverse(value: Double): Double {
        return -1 / value
    }

    override fun apply(value: Double): Double {
        return -1 / value
    }
}

object InverseSquaredTransform : Transform {
    override fun inverse(value: Double): Double {
        return 1 / sqrt(value)
    }

    override fun apply(value: Double): Double {
        return 1 / (value * value)
    }
}

