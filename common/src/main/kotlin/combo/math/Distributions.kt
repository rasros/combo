package combo.math

import kotlin.math.*

fun chi2CdfDf1(x: Float): Float {
    return if (x < 1) sqrt(0.6223257f * x - 0.1584199f * x * x)
    else 1 - 0.3173105f * exp(-0.67975855f * (x - 1))
}

fun normInvCdf(p: Float): Float {
    return if (p < 0.5f) -nicdfApproximation(sqrt(-2.0f * ln(p)))
    else nicdfApproximation(sqrt(-2.0f * ln(1 - p)))
}

private fun nicdfApproximation(t: Float): Float {
    return t - ((0.010328f * t + 0.802853f) * t + 2.515517f) /
            (((0.001308f * t + 0.189269f) * t + 1.432788f) * t + 1.0f)
}

// Sigmoid approximation
//fun normCdf(x: Float) = 1 / (1 + exp(-1.65451f * x))
fun normCdf(x: Float): Float {
    val absx = x.absoluteValue
    val div = 0.226f + 0.64f * absx + 0.33f * sqrt(absx * absx + 3)
    val y = 1 - INV_SQRT2PI * exp(-absx * absx / 2) / div
    return if (x < 0) 1 - y else y
}

private val INV_SQRT2PI = (1 / sqrt(2 * PI)).toFloat()

/**
 * This is inspired by the Scheffé-Tukey approximation, but with improved accuracy for low df2 and fixed df1=1.
 * Still, accuracy is very poor for low df2 or low x.
 */
fun fCdfDf1(x: Float, df2: Float): Float {
    val lambda = x * 0.9282823f * (1 - exp(0.06499344f + df2 * (-0.17084715f + -0.7394519f / x)))
    return chi2CdfDf1(lambda)
}