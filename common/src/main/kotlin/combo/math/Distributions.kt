package combo.math

import kotlin.math.ln
import kotlin.math.sqrt

fun normalInverseCdf(p: Float): Float {
    return if (p < 0.5f) -nicdfApproximation(sqrt(-2.0f * ln(p)))
    else nicdfApproximation(sqrt(-2.0f * ln(1 - p)))
}

private fun nicdfApproximation(t: Float): Float {
    return t - ((0.010328f * t + 0.802853f) * t + 2.515517f) /
            (((0.001308f * t + 0.189269f) * t + 1.432788f) * t + 1.0f)
}
