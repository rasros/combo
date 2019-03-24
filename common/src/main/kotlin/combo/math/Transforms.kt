@file:JvmName("Transforms")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.*

interface Transform {
    // TODO need inverse???
    fun inverse(value: Float): Float

    fun apply(value: Float): Float

    fun andThen(after: Transform) = object : Transform {
        override fun inverse(value: Float) = this@Transform.inverse(after.inverse(value))
        override fun apply(value: Float) = after.apply(this@Transform.apply(value))
    }
}

object IdentityTransform : Transform {
    override fun inverse(value: Float) = value
    override fun apply(value: Float) = value
}


class ShiftTransform(val by: Float) : Transform {
    override fun inverse(value: Float) = value - by
    override fun apply(value: Float) = value + by
}

class ScaleTransform(val by: Float) : Transform {
    override fun inverse(value: Float) = value / by
    override fun apply(value: Float) = value * by
}

object ArcSineTransform : Transform {
    override fun inverse(value: Float) = sin(value).pow(2)
    override fun apply(value: Float) = asin(sqrt(value))
}

object LogTransform : Transform {
    override fun inverse(value: Float) = exp(value)
    override fun apply(value: Float) = ln(value)
}

object SquareRootTransform : Transform {
    override fun inverse(value: Float) = value * value
    override fun apply(value: Float) = sqrt(value)
}

object LogitTransform : Transform {
    override fun apply(value: Float): Float {
        return 1 / (1 + exp(-value))
    }

    override fun inverse(value: Float): Float {
        return -ln(1 / value - 1)
    }
}

object ClogLogTransform : Transform {
    override fun inverse(value: Float): Float {
        return 1 - exp(-exp(value))
    }

    override fun apply(value: Float): Float {
        return ln(-ln(1 - value))
    }
}

object InverseTransform : Transform {
    override fun inverse(value: Float): Float {
        return 1 / value
    }

    override fun apply(value: Float): Float {
        return 1 / value
    }
}

object NegativeInverseTransform : Transform {
    override fun inverse(value: Float): Float {
        return -1 / value
    }

    override fun apply(value: Float): Float {
        return -1 / value
    }
}

object InverseSquaredTransform : Transform {
    override fun inverse(value: Float): Float {
        return 1 / sqrt(value)
    }

    override fun apply(value: Float): Float {
        return 1 / (value * value)
    }
}

