@file:JvmName("VarianceFunctions")

package combo.bandit.glm

import combo.math.*
import kotlin.jvm.JvmName

interface VarianceFunction {
    fun dispersion(mle: Double): Double
    fun variance(mean: Double): Double
    fun canonicalLink(): Transform
}

interface UnitScaleResponse : VarianceFunction {
    override fun dispersion(mle: Double) = 1.0
}

interface EstimatedScaleResponse : VarianceFunction {
    override fun dispersion(mle: Double) = mle
}

object NormalVariance : EstimatedScaleResponse {
    override fun variance(mean: Double) = 1.0
    override fun canonicalLink() = IdentityTransform
}

object BinomialVariance : UnitScaleResponse {
    override fun variance(mean: Double) = mean * (1 - mean)
    override fun canonicalLink() = LogitTransform
}

typealias BernoulliVariance = BinomialVariance

object PoissonVariance : UnitScaleResponse {
    override fun variance(mean: Double) = mean
    override fun canonicalLink() = LogTransform
}

object GammaVariance : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean * mean
    override fun canonicalLink() = NegativeInverseTransform
}

object InverseGaussianVariance : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean * mean * mean
    override fun canonicalLink() = InverseSquaredTransform
}

object ExponentialVariance : UnitScaleResponse {
    override fun variance(mean: Double) = 1.0 / (mean * mean)
    override fun canonicalLink() = NegativeInverseTransform
}

object QuasiBinomialVariance : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean * (1 - mean)
    override fun canonicalLink() = LogitTransform
}

object QuasiPoissonVariance : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean
    override fun canonicalLink() = LogTransform
}
