@file:JvmName("VarianceEstimators")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.pow
import kotlin.math.sqrt

interface VarianceEstimator : DataSample {

    override fun accept(value: Double) = accept(value, 1.0)

    /**
     * @param value include value in estimate
     * @param weight frequency weight
     */
    override fun accept(value: Double, weight: Double)

    override val nbrSamples: Long get() = nbrWeightedSamples.toLong()
    val nbrWeightedSamples: Double
    val mean: Double
    val squaredDeviations: Double get() = variance * nbrWeightedSamples
    val variance: Double get() = squaredDeviations / nbrWeightedSamples
    val standardDeviation: Double get() = sqrt(variance)
    val sum: Double get() = mean * nbrWeightedSamples

    fun copy(): VarianceEstimator
    fun combine(vs: VarianceEstimator): VarianceEstimator
    override fun toArray() = doubleArrayOf(mean)
}

/**
 * Calculates incremental mean and variance according to the Welford's online algorithm.
 */
class RunningVariance(mean: Double = 0.0,
                      squaredDeviations: Double = 0.0,
                      nbrWeightedSamples: Double = 0.0) : VarianceEstimator {

    override var mean = mean
        private set
    override var squaredDeviations = squaredDeviations
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    override fun copy(): RunningVariance {
        val copy = RunningVariance()
        copy.mean = mean
        copy.squaredDeviations = squaredDeviations
        copy.nbrWeightedSamples = nbrWeightedSamples
        return copy
    }

    override fun accept(value: Double, weight: Double) {
        nbrWeightedSamples += weight
        if (nbrWeightedSamples == weight) {
            mean = value
        } else {
            val oldM = mean
            mean = oldM + (value - oldM) * (weight / nbrWeightedSamples)
            squaredDeviations += weight * (value - oldM) * (value - mean)
        }
    }

    override fun combine(vs: VarianceEstimator) = RunningVariance().also {
        it.mean = (this.mean + vs.mean) / 2
        it.nbrWeightedSamples = this.nbrWeightedSamples + vs.nbrWeightedSamples
        it.squaredDeviations = (this.mean - it.mean).pow(2) + (vs.mean - it.mean).pow(2)
    }

    override fun toString() = "RunningVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
}

/**
 * This calculates a moving average and variance by assigning old values an exponentially decaying weight. The storage
 * requirement is constant and does not depend on the size of the window of the moving average.
 * @param beta strength of the update. For finite samples n the optimal parameter can be set to: beta = 2/n+1.
 * Default n is 99
 */
class ExponentialDecayVariance(var beta: Double = 0.02,
                               mean: Double = 0.0,
                               variance: Double = 0.0,
                               nbrWeightedSamples: Double = 0.0) : VarianceEstimator {

    constructor(window: Int) : this(2.0 / (window + 1.0))

    override var mean = mean
        private set
    override var variance = variance
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    init {
        require(beta < 1.0 && beta > 0.0) { "Beta (1-decay) parameter must be within 0 to 1 range, got $beta." }
    }

    override fun copy(): VarianceEstimator {
        val copy = ExponentialDecayVariance(beta)
        copy.nbrWeightedSamples = nbrWeightedSamples
        copy.mean = mean
        copy.variance = variance
        return copy
    }

    override fun accept(value: Double, weight: Double) {
        nbrWeightedSamples += weight
        if (nbrWeightedSamples == weight) {
            mean = value
        } else {
            val adjustedBeta = if (weight == 1.0) beta
            else weight * beta / (1 - beta + weight * beta)
            val diff = value - mean
            val inc = adjustedBeta * diff
            mean += inc
            variance = (1 - adjustedBeta) * (variance + inc * diff)
        }

    }

    override fun toString() = "ExponentialDecayVariance(beta=$beta, mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"

    override fun combine(vs: VarianceEstimator) = ExponentialDecayVariance().also {
        it.mean = (this.mean + vs.mean) / 2
        it.nbrWeightedSamples = this.nbrWeightedSamples + vs.nbrWeightedSamples
        it.variance = ((this.mean - it.mean).pow(2) + (vs.mean - it.mean).pow(2)) / it.nbrWeightedSamples
    }
}

/**
 * This estimator is only used with binomial count data, hence the variance depends on the mean.
 */
class CountData(sum: Double = 0.0, nbrWeightedSamples: Double = 0.0) : VarianceEstimator {

    override fun accept(value: Double, weight: Double) {
        require(value in 0.0..weight) { "CountData can only be used with Binomial data." }
        sum += value
        nbrWeightedSamples += weight
    }

    override var sum = sum
        private set

    override var nbrWeightedSamples: Double = nbrWeightedSamples
        private set

    override val mean: Double
        get() = sum / nbrWeightedSamples

    override val variance: Double
        get() = mean * (1 - mean)

    override fun copy() = CountData(sum, nbrWeightedSamples)

    override fun combine(vs: VarianceEstimator) =
            CountData(sum + vs.sum, nbrWeightedSamples + vs.nbrWeightedSamples)

    override fun toString() = "CountData(sum=$sum, nbrSamples=$nbrSamples)"
}
