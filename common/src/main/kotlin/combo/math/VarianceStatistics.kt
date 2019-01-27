@file:JvmName("VarianceStatistics")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

interface VarianceStatistic : DataSample {

    override fun accept(value: Double) = accept(value, 1.0)
    fun accept(value: Double, weight: Double)

    override val nbrSamples: Long get() = nbrWeightedSamples.toLong()
    val nbrWeightedSamples: Double
    val mean: Double
    val squaredDeviations: Double get() = nbrWeightedSamples / variance
    val variance: Double get() = squaredDeviations / nbrWeightedSamples
    val standardDeviation: Double get() = sqrt(variance)
    val sum: Double get() = mean * nbrWeightedSamples

    fun copy(): VarianceStatistic

    operator fun component1() = mean
    operator fun component2() = variance
    operator fun component3() = nbrWeightedSamples
    override fun collect() = doubleArrayOf(mean)
}

class DescriptiveStatistic(val decorated: VarianceStatistic) : VarianceStatistic by decorated {
    var min: Double = Double.POSITIVE_INFINITY
        private set
    var max: Double = Double.NEGATIVE_INFINITY
        private set

    override fun accept(value: Double) {
        decorated.accept(value)
        max = max(value, max)
        min = min(value, min)
    }

    override fun accept(value: Double, weight: Double) {
        decorated.accept(value, weight)
        max = max(value, max)
        min = min(value, min)
    }
}

class SumData(sum: Double = 0.0, nbrWeightedSamples: Double = 0.0) : VarianceStatistic {

    override fun accept(value: Double, weight: Double) {
        sum += value * weight
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

    override fun copy() = SumData(sum, nbrWeightedSamples)
}

class RunningVariance(mean: Double = 0.0,
                      squaredDeviations: Double = 0.0,
                      nbrWeightedSamples: Double = 0.0) : VarianceStatistic {

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
            squaredDeviations = 0.0
        } else {
            val oldM = mean
            mean = oldM + (value - oldM) / nbrWeightedSamples
            squaredDeviations += (value - oldM) * (value - mean)
        }
    }

    fun combine(vs: VarianceStatistic) = RunningVariance().also {
        it.mean = (this.mean + vs.mean) / 2
        it.nbrWeightedSamples = this.nbrWeightedSamples + vs.nbrWeightedSamples
        it.squaredDeviations = (this.mean - it.mean).pow(2) + (vs.mean - it.mean).pow(2)
    }

    override fun toString() = "RunningVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
}

/**
 * Default window is 100
 */
class ExponentialDecayVariance(val beta: Double = 0.02,
                               mean: Double = 0.0,
                               variance: Double = 0.0,
                               nbrWeightedSamples: Double = 0.0) : VarianceStatistic {

    constructor(window: Int) : this(2.0 / (window - 1.0))

    override var mean = mean
        private set
    override var variance = variance
        private set
    override var nbrWeightedSamples = nbrWeightedSamples
        private set

    init {
        require(beta < 1.0 && beta > 0.0) { "Beta parameter must be within 0 to 1 range, got $beta." }
    }

    override fun copy(): VarianceStatistic {
        val copy = ExponentialDecayVariance(beta)
        copy.nbrWeightedSamples = nbrWeightedSamples
        copy.mean = mean
        copy.variance = variance
        return copy
    }

    override fun accept(value: Double, weight: Double) {
        nbrWeightedSamples += weight
        val diff = value - mean
        val inc = beta * diff * weight
        mean += inc
        variance = (1 - beta) * (variance + diff * inc) // TODO should be exp.?
    }

    override fun toString() = "ExponentialDecayVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples, beta=$beta)"
}

class FixedVariance(override val mean: Double, override val variance: Double, override val nbrWeightedSamples: Double) : VarianceStatistic {
    override fun copy() = FixedVariance(mean, variance, nbrWeightedSamples)
    override fun accept(value: Double, weight: Double) = throw UnsupportedOperationException()
    override fun toString() = "FixedVariance(mean=$mean, variance=$variance, nbrSamples=$nbrSamples)"
}
