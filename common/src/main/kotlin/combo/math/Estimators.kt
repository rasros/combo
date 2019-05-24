package combo.math

import kotlin.math.sqrt

interface VarianceEstimator : DataSample {
    override fun accept(value: Float) = accept(value, 1.0f)

    /**
     * @param value include value in estimate
     * @param weight frequency weight
     */
    override fun accept(value: Float, weight: Float)

    override val nbrSamples: Long get() = nbrWeightedSamples.toLong()
    val nbrWeightedSamples: Float
    val mean: Float
    val sum: Float get() = mean * nbrWeightedSamples

    override fun toArray() = floatArrayOf(mean)

    fun combine(vs: VarianceEstimator) = RunningMean(
            (mean * nbrWeightedSamples + vs.mean * vs.nbrWeightedSamples) / (nbrWeightedSamples + vs.nbrWeightedSamples),
            nbrWeightedSamples + vs.nbrWeightedSamples)

    val squaredDeviations: Float get() = variance * nbrWeightedSamples
    val variance: Float get() = squaredDeviations / nbrWeightedSamples
    val standardDeviation: Float get() = sqrt(variance)

    fun copy(): VarianceEstimator
}

/**
 * This estimator is only used with poisson data, hence the variance is the mean.
 */
interface MeanEstimator : VarianceEstimator {
    override val variance: Float
        get() = mean
}

/**
 * This estimator is only used with binomial count data, hence the variance is a function of the mean.
 */
interface BinaryEstimator : VarianceEstimator {
    override val variance: Float
        get() = mean * (1 - mean)
}

/**
 * Calculates incremental mean and variance according to the Welford's online algorithm.
 */
class RunningVariance(mean: Float = 0.0f, squaredDeviations: Float = 0.0f, nbrWeightedSamples: Float = 0.0f)
    : VarianceEstimator {

    override var mean = mean
        private set
    override var squaredDeviations = squaredDeviations
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    override fun accept(value: Float, weight: Float) {
        nbrWeightedSamples += weight
        if (nbrWeightedSamples == weight) {
            mean = value
        } else {
            val oldM = mean
            mean = oldM + (value - oldM) * (weight / nbrWeightedSamples)
            squaredDeviations += weight * (value - oldM) * (value - mean)
        }
    }

    override fun toString() = "RunningVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
    override fun copy() = RunningVariance(mean, squaredDeviations, nbrWeightedSamples)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RunningVariance) return false
        return mean == other.mean &&
                squaredDeviations == other.squaredDeviations &&
                nbrWeightedSamples == other.nbrWeightedSamples
    }

    override fun hashCode(): Int {
        var result = mean.hashCode()
        result = 31 * result + squaredDeviations.hashCode()
        result = 31 * result + nbrWeightedSamples.hashCode()
        return result
    }
}

/**
 * This calculates a moving average and variance by assigning old values an exponentially decaying weight. The storage
 * requirement is constant and does not depend on the size of the window of the moving average.
 * @param beta strength of the update. For finite samples n the optimal parameter can be set to: beta = 2/n+1.
 * Default n is 99
 */
class ExponentialDecayVariance(var beta: Float = 0.02f, mean: Float = 0.0f, variance: Float = 0.0f, nbrWeightedSamples: Float = 0.0f)
    : VarianceEstimator {

    constructor(window: Int) : this(2.0f / (window + 1.0f))

    override var mean = mean
        private set
    override var variance = variance
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    init {
        require(beta < 1.0f && beta > 0.0f) { "Beta (1-decay) parameter must be within 0 to 1 range, got $beta." }
    }

    override fun accept(value: Float, weight: Float) {
        nbrWeightedSamples += weight
        if (nbrWeightedSamples == weight) {
            mean = value
        } else {
            val adjustedBeta = if (weight == 1.0f) beta
            else weight * beta / (1 - beta + weight * beta)
            val diff = value - mean
            val inc = adjustedBeta * diff
            mean += inc
            variance = (1 - adjustedBeta) * (variance + inc * diff)
        }

    }

    override fun toString() = "ExponentialDecayVariance(beta=$beta, mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
    override fun copy() = ExponentialDecayVariance(beta, mean, variance, nbrWeightedSamples)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExponentialDecayVariance) return false
        return mean == other.mean &&
                variance == other.variance &&
                nbrWeightedSamples == other.nbrWeightedSamples
    }

    override fun hashCode(): Int {
        var result = mean.hashCode()
        result = 31 * result + variance.hashCode()
        result = 31 * result + nbrWeightedSamples.hashCode()
        return result
    }
}

class BinarySum(sum: Float = 0.0f, nbrWeightedSamples: Float = 0.0f) : BinaryEstimator {

    override fun accept(value: Float, weight: Float) {
        require(value in 0.0f..1.0f) { "BinarySum can only be used with Binomial data." }
        sum += value * weight
        nbrWeightedSamples += weight
    }

    override var sum = sum
        private set

    override var nbrWeightedSamples: Float = nbrWeightedSamples
        private set

    override val mean: Float
        get() = sum / nbrWeightedSamples

    override val variance: Float
        get() = mean * (1 - mean)

    override fun toString() = "BinarySum(sum=$sum, nbrSamples=$nbrSamples)"
    override fun copy() = BinarySum(sum, nbrWeightedSamples)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinarySum) return false
        return sum == other.sum && nbrWeightedSamples == other.nbrWeightedSamples
    }

    override fun hashCode(): Int {
        var result = sum.hashCode()
        result = 31 * result + nbrWeightedSamples.hashCode()
        return result
    }
}

class RunningMean(mean: Float = 0.0f, nbrWeightedSamples: Float = 0.0f) : MeanEstimator {

    override var mean = mean
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    override fun accept(value: Float, weight: Float) {
        nbrWeightedSamples += weight
        if (nbrWeightedSamples == weight) {
            mean = value
        } else {
            val oldM = mean
            mean = oldM + (value - oldM) * (weight / nbrWeightedSamples)
        }
    }

    override fun toString() = "RunningMean(mean=$mean, nbrSamples=$nbrSamples)"
    override fun copy() = RunningMean(mean, nbrWeightedSamples)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RunningMean) return false
        return mean == other.mean && nbrWeightedSamples == other.nbrWeightedSamples
    }

    override fun hashCode(): Int {
        var result = mean.hashCode()
        result = 31 * result + nbrWeightedSamples.hashCode()
        return result
    }
}