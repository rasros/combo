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


fun gaussian() = object : EstimatedScaleResponse {
    override fun variance(mean: Double) = 1.0
    override fun canonicalLink() = identity()
}

fun binomial() = object : UnitScaleResponse {
    override fun variance(mean: Double) = mean * (1 - mean)
    override fun canonicalLink() = logit()
}

fun bernoulli() = binomial()

fun poisson() = object : UnitScaleResponse {
    override fun variance(mean: Double) = mean
    override fun canonicalLink() = log()
}

fun gamma() = object : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean * mean
    override fun canonicalLink() = negativeInverse()
}

fun inverseGaussian() = object : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean * mean * mean
    override fun canonicalLink() = inverseSquared()
}

fun exponential() = object : UnitScaleResponse {
    override fun variance(mean: Double) = 1.0 / (mean * mean)
    override fun canonicalLink() = negativeInverse()
}

fun quasiBinomial() = object : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean * (1 - mean)
    override fun canonicalLink() = logit()
}

fun quasiPoisson() = object : EstimatedScaleResponse {
    override fun variance(mean: Double) = mean
    override fun canonicalLink() = log()
}
