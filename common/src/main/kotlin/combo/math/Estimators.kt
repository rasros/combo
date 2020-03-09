package combo.math

import combo.util.FloatCircleBuffer
import kotlin.math.min
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

    override fun values() = floatArrayOf(mean)
    override fun labels() = longArrayOf(0L)

    fun combine(vs: VarianceEstimator): VarianceEstimator

    val squaredDeviations: Float get() = variance * nbrWeightedSamples
    val variance: Float get() = squaredDeviations / nbrWeightedSamples
    val standardDeviation: Float get() = sqrt(variance)

    fun updateSampleSize(newN: Float)

    override fun copy(): VarianceEstimator
}

fun combineMean(m1: Float, m2: Float, n1: Float, n2: Float): Float {
    val n = n1 + n2
    return if (n == 0f) 0f
    else (m1 * n1 + m2 * n2) / n
}

fun combineVariance(v1: Float, v2: Float, m1: Float, m2: Float, n1: Float, n2: Float): Float {
    val n = n1 + n2
    return if (n == 0f) 0f
    else (v1 * n1 + v2 * n2) / n + (m1 - m2) * (m1 - m2) * n1 * n2 / n / n
}

fun combinePrecision(p1: Float, p2: Float, m1: Float, m2: Float, n1: Float, n2: Float): Float {
    val n = n1 + n2
    return if (p1 == 0f || p2 == 0f) 0f
    else n * n / (n1 * n2 * (m1 - m2) * (m1 - m2) + n * (n1 / p1 + n2 / p2))
}

interface RemovableEstimator : VarianceEstimator {
    fun remove(value: Float, weight: Float = 1.0f)
    override fun combine(vs: VarianceEstimator): RemovableEstimator
    override fun copy(): RemovableEstimator
}

/**
 * This estimator is only used with poisson data, hence the variance is the mean.
 */
interface MeanEstimator : VarianceEstimator {
    override val variance: Float
        get() = mean

    override fun copy(): MeanEstimator
}

/**
 * This estimator is only used with binomial count data, hence the variance is a function of the mean.
 */
interface BinaryEstimator : VarianceEstimator {
    override val variance: Float
        get() = mean * (1 - mean)

    override fun copy(): BinaryEstimator
}

/**
 * This estimator is used for UCB1Tuned and UCB1Normal
 */
interface SquaredEstimator : VarianceEstimator {
    val meanOfSquares: Float
    override fun copy(): SquaredEstimator
}

/**
 * Calculates incremental mean and variance according to the Welford's online algorithm.
 */
class RunningVariance(mean: Float = 0.0f, squaredDeviations: Float = 0.0f, nbrWeightedSamples: Float = 0.0f)
    : RemovableEstimator {

    override var mean = mean
        private set
    override var squaredDeviations = squaredDeviations
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    override fun accept(value: Float, weight: Float) {
        require(weight >= 0.0f)
        nbrWeightedSamples += weight
        val oldM = mean
        mean = oldM + (value - oldM) * (weight / nbrWeightedSamples)
        squaredDeviations += weight * (value - oldM) * (value - mean)
    }

    override fun remove(value: Float, weight: Float) {
        require(nbrWeightedSamples > weight)
        nbrWeightedSamples -= weight
        val oldM = mean
        mean = oldM - (value - oldM) * (weight / (nbrWeightedSamples))
        squaredDeviations -= weight * (value - oldM) * (value - mean)
    }

    override fun updateSampleSize(newN: Float) {
        squaredDeviations *= newN / nbrWeightedSamples
        nbrWeightedSamples = newN
    }

    override fun toString() = "RunningVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
    override fun copy() = RunningVariance(mean, squaredDeviations, nbrWeightedSamples)

    override fun combine(vs: VarianceEstimator): RunningVariance {
        val n1 = nbrWeightedSamples
        val n2 = vs.nbrWeightedSamples
        val v1 = variance
        val v2 = vs.variance
        val m1 = mean
        val m2 = vs.mean
        val n = n1 + n2
        val m = combineMean(m1, m2, n1, n2)
        val v = combineVariance(v1, v2, m1, m2, n1, n2)
        return RunningVariance(m, v * n, n)
    }

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
 * requirement is constant and does not depend on the size of the window of the moving average. The [nbrSamples] is
 * capped to the window size.
 * @param beta strength of the update. For finite samples n the optimal parameter can be set to: beta = 2/n+1.
 * Default n is 99.
 */
class ExponentialDecayVariance(val beta: Float = 0.02f, mean: Float = 0.0f, variance: Float = 0.0f, nbrWeightedSamples: Float = 0.0f)
    : VarianceEstimator {

    constructor(window: Int) : this(2.0f / (window + 1.0f))

    override var mean = mean
        private set
    override var variance = variance
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set
    private val maxSize: Float = 2 / beta - 1

    init {
        require(beta < 1.0f && beta > 0.0f) { "Beta (decay parameter) must be within 0 to 1 range, got $beta." }
    }

    override fun accept(value: Float, weight: Float) {
        nbrWeightedSamples = min(maxSize, nbrWeightedSamples + weight)
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

    override fun updateSampleSize(newN: Float) {
        nbrWeightedSamples = newN
    }

    override fun toString() = "ExponentialDecayVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
    override fun copy() = ExponentialDecayVariance(beta, mean, variance, nbrWeightedSamples)

    override fun combine(vs: VarianceEstimator): ExponentialDecayVariance {
        val n1 = nbrWeightedSamples
        val n2 = vs.nbrWeightedSamples
        val v1 = variance
        val v2 = vs.variance
        val m1 = mean
        val m2 = vs.mean
        val n = n1 + n2
        val m = combineMean(m1, m2, n1, n2)
        val v = combineVariance(v1, v2, m1, m2, n1, n2)
        return ExponentialDecayVariance(beta, m, v, min(maxSize, n))
    }

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

class BinarySum(sum: Float = 0.0f, nbrWeightedSamples: Float = 0.0f) : BinaryEstimator, RemovableEstimator {

    override fun accept(value: Float, weight: Float) {
        require(value in 0.0f..1.0f) { "BinarySum can only be used with Binomial data." }
        sum += value * weight
        nbrWeightedSamples += weight
    }

    override fun remove(value: Float, weight: Float) {
        require(value in 0.0f..1.0f) { "BinarySum can only be used with Binomial data." }
        require(nbrWeightedSamples >= weight)
        sum -= value * weight
        nbrWeightedSamples -= weight
    }

    override var sum = sum
        private set

    override var nbrWeightedSamples: Float = nbrWeightedSamples
        private set

    override val mean: Float
        get() = sum / nbrWeightedSamples

    override val variance: Float
        get() = mean * (1 - mean)

    override fun updateSampleSize(newN: Float) {
        sum *= newN / nbrWeightedSamples
        nbrWeightedSamples = newN
    }

    override fun toString() = "BinarySum(sum=$sum, nbrSamples=$nbrSamples, mean=$mean)"
    override fun copy() = BinarySum(sum, nbrWeightedSamples)

    override fun combine(vs: VarianceEstimator) = BinarySum(sum + vs.sum, nbrWeightedSamples + vs.nbrWeightedSamples)

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

class RunningMean(mean: Float = 0.0f, nbrWeightedSamples: Float = 0.0f) : MeanEstimator, RemovableEstimator {

    override var mean = mean
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    override fun accept(value: Float, weight: Float) {
        nbrWeightedSamples += weight
        val oldM = mean
        mean = oldM + (value - oldM) * (weight / nbrWeightedSamples)
    }

    override fun remove(value: Float, weight: Float) {
        require(nbrWeightedSamples >= weight)
        nbrWeightedSamples -= weight
        val oldM = mean
        mean = oldM - (value - oldM) * (weight / nbrWeightedSamples)
    }

    override fun updateSampleSize(newN: Float) {
        mean *= newN / nbrWeightedSamples
        nbrWeightedSamples = newN
    }

    override fun toString() = "RunningMean(mean=$mean, nbrSamples=$nbrSamples)"
    override fun copy() = RunningMean(mean, nbrWeightedSamples)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RunningMean) return false
        return mean == other.mean && nbrWeightedSamples == other.nbrWeightedSamples
    }

    override fun combine(vs: VarianceEstimator): RunningMean {
        val n1 = nbrWeightedSamples
        val n2 = vs.nbrWeightedSamples
        val m1 = mean
        val m2 = vs.mean
        val m = combineMean(m1, m2, n1, n2)
        return RunningMean(m, n1 + n2)
    }

    override fun hashCode(): Int {
        var result = mean.hashCode()
        result = 31 * result + nbrWeightedSamples.hashCode()
        return result
    }
}

class RunningSquaredMeans private constructor(private val base: RunningVariance, meanOfSquares: Float)
    : RemovableEstimator by base, SquaredEstimator {

    constructor(mean: Float = 0.0f,
                meanOfSquares: Float = 0.0f,
                squaredDeviations: Float = 0.0f,
                nbrWeightedSamples: Float = 0.0f) : this(RunningVariance(mean, squaredDeviations, nbrWeightedSamples), meanOfSquares)

    override var meanOfSquares: Float = meanOfSquares
        private set

    override fun accept(value: Float) = accept(value, 1.0f)

    override fun accept(value: Float, weight: Float) {
        base.accept(value, weight)
        val oldMS = meanOfSquares
        meanOfSquares = oldMS + (value * value - oldMS) * (weight / nbrWeightedSamples)
    }

    override fun remove(value: Float, weight: Float) {
        base.remove(value, weight)
        val oldMS = meanOfSquares
        meanOfSquares = oldMS - (value * value - oldMS) * (weight / nbrWeightedSamples)
    }

    override fun combine(vs: VarianceEstimator): RunningSquaredMeans {
        vs as SquaredEstimator
        val ms1 = meanOfSquares
        val ms2 = vs.meanOfSquares
        val n1 = nbrWeightedSamples
        val n2 = vs.nbrWeightedSamples
        val ms = combineMean(ms1, ms2, n1, n2)
        return RunningSquaredMeans(base.combine(vs), ms)
    }

    override fun copy() = RunningSquaredMeans(mean, meanOfSquares, squaredDeviations, nbrWeightedSamples)
    override fun toString() = "RunningSquaredMeans(mean=$mean, meanOfSquares=$meanOfSquares, squaredDeviations=$squaredDeviations, nbrSamples=$nbrSamples)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RunningSquaredMeans) return false
        return base == other.base && meanOfSquares == other.meanOfSquares
    }

    override fun hashCode(): Int {
        var result = base.hashCode()
        result = 31 * result + meanOfSquares.hashCode()
        return result
    }
}

class WindowedEstimator(val windowSize: Int, val base: RemovableEstimator) : BinaryEstimator, MeanEstimator, VarianceEstimator by base {

    private val values = FloatCircleBuffer(windowSize)
    private val weights = FloatCircleBuffer(windowSize)

    override val variance: Float get() = base.variance

    override fun accept(value: Float, weight: Float) {
        val oldV = values.add(value)
        val oldW = weights.add(weight)
        if (oldW > 0.0f) base.remove(oldV, oldW)
        base.accept(value, weight)
    }

    override fun combine(vs: VarianceEstimator) = WindowedEstimator(windowSize, base.combine(vs))
    override fun copy() = WindowedEstimator(windowSize, base.copy())
    override fun toString() = "WindowedEstimator(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowedEstimator) return false
        return base == other.base
    }

    override fun hashCode() = base.hashCode()
}

class WindowedSquaredEstimator(val windowSize: Int, val base: RunningSquaredMeans = RunningSquaredMeans()) : SquaredEstimator by base {

    private val values = FloatCircleBuffer(windowSize)
    private val weights = FloatCircleBuffer(windowSize)

    override val variance: Float get() = base.variance

    override fun accept(value: Float, weight: Float) {
        val oldV = values.add(value)
        val oldW = weights.add(weight)
        if (oldW > 0.0f) base.remove(oldV, oldW)
        base.accept(value, weight)
    }

    override fun combine(vs: VarianceEstimator) = WindowedSquaredEstimator(windowSize, base.combine(vs))
    override fun copy() = WindowedSquaredEstimator(windowSize, base.copy())
    override fun toString() = "WindowedSquaredEstimator(mean=$mean, meanOfSquares=$meanOfSquares, variance=$variance, nbrSamples=$nbrSamples)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WindowedSquaredEstimator) return false
        return base == other.base
    }

    override fun hashCode() = base.hashCode()
}
