package combo.bandit.glm

import combo.math.*

interface VarianceFunction {
    fun dispersion(mle: Float): Float
    fun variance(mean: Float): Float
    fun canonicalLink(): Transform
}

interface UnitScaleResponse : VarianceFunction {
    override fun dispersion(mle: Float) = 1.0f
}

interface EstimatedScaleResponse : VarianceFunction {
    override fun dispersion(mle: Float) = mle
}

object NormalVariance : EstimatedScaleResponse {
    override fun variance(mean: Float) = 1.0f
    override fun canonicalLink() = IdentityTransform
}

object BinomialVariance : UnitScaleResponse {
    override fun variance(mean: Float) = mean * (1 - mean)
    override fun canonicalLink() = LogitTransform
}

typealias BernoulliVariance = BinomialVariance

object PoissonVariance : UnitScaleResponse {
    override fun variance(mean: Float) = mean
    override fun canonicalLink() = LogTransform
}

object GammaVariance : EstimatedScaleResponse {
    override fun variance(mean: Float) = mean * mean
    override fun canonicalLink() = NegativeInverseTransform
}

object InverseGaussianVariance : EstimatedScaleResponse {
    override fun variance(mean: Float) = mean * mean * mean
    override fun canonicalLink() = InverseSquaredTransform
}

object ExponentialVariance : UnitScaleResponse {
    override fun variance(mean: Float) = 1.0f / (mean * mean)
    override fun canonicalLink() = NegativeInverseTransform
}

object QuasiBinomialVariance : EstimatedScaleResponse {
    override fun variance(mean: Float) = mean * (1 - mean)
    override fun canonicalLink() = LogitTransform
}

object QuasiPoissonVariance : EstimatedScaleResponse {
    override fun variance(mean: Float) = mean
    override fun canonicalLink() = LogTransform
}
