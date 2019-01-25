@file:JvmName("Posteriors")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

interface Posterior {

    fun sample(rng: Random, stat: VarianceStatistic): Double

    fun update(stat: VarianceStatistic, value: Double, weight: Double = 1.0) {
        stat.accept(value, weight)
    }

    fun defaultPrior(): VarianceStatistic

    fun transformed(t: Transform): Posterior = object : Posterior {
        override fun sample(rng: Random, stat: VarianceStatistic) = t.inverse(this@Posterior.sample(rng, stat))

        override fun update(stat: VarianceStatistic, value: Double, weight: Double) {
            this@Posterior.update(stat, t.apply(value), weight)
        }

        override fun defaultPrior() = t.backtransform(this@Posterior.defaultPrior())
    }
}

object BinomialPosterior : Posterior {
    /** results in uniform prior over p */
    override fun defaultPrior() = SumData(1.0, 2.0)

    override fun sample(rng: Random, stat: VarianceStatistic): Double {
        val alpha = stat.sum
        val beta = stat.nbrWeightedSamples - alpha
        return rng.beta(alpha, beta)
    }
}

object PoissonPosterior : Posterior {
    /** results in wide GammaVariance(0.01, 0.01) prior over p */
    override fun defaultPrior() = SumData(0.01, 0.01)

    override fun sample(rng: Random, stat: VarianceStatistic) =
            rng.gamma(stat.sum, 1.0 / stat.nbrWeightedSamples)
}

object GeometricPosterior : Posterior {
    /** results in uniform prior over p */
    override fun defaultPrior() = SumData(2.0, 1.0)

    override fun sample(rng: Random, stat: VarianceStatistic) =
            rng.beta(stat.nbrWeightedSamples, stat.sum - stat.nbrWeightedSamples)
}

object NormalPosterior : Posterior {
    /** this design does not permit individual prior for sigma and mu */
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 0.02)

    override fun sample(rng: Random, stat: VarianceStatistic): Double {
        val alpha = stat.nbrWeightedSamples / 2.0
        val beta = stat.squaredDeviations / 2.0
        val sampleVariance = generateSequence { 1 / rng.inverseGamma(alpha, beta) }
                .map { sqrt(it / stat.nbrWeightedSamples) }
                .first { !it.isNaN() && !it.isInfinite() }
        return generateSequence { rng.nextGaussian(stat.mean, sampleVariance) }
                .first { !it.isNaN() && !it.isInfinite() }
    }
}

object LogNormalPosterior : Posterior {
    /** same prior as normal */
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 0.02)

    override fun sample(rng: Random, stat: VarianceStatistic): Double {
        val alpha = stat.nbrWeightedSamples / 2.0
        val beta = stat.squaredDeviations / 2.0
        var sample: Double
        do {
            val sampleVariance = generateSequence { 1 / rng.inverseGamma(alpha, beta) }
                    .map { sqrt(it / stat.nbrWeightedSamples) }
                    .first { !it.isNaN() && !it.isInfinite() }
            val sampleMean = generateSequence { rng.nextGaussian(stat.mean, sampleVariance) }
                    .first { !it.isNaN() && !it.isInfinite() }
            sample = exp(sampleMean + sampleVariance * 0.5)
        } while (sample.isNaN() || sample.isInfinite())
        return sample
    }

    override fun update(stat: VarianceStatistic, value: Double, weight: Double) {
        stat.accept(ln(value), weight)
    }
}

object ExponentialPosterior : Posterior {
    override fun defaultPrior() = SumData(0.0001, 0.01)
    override fun sample(rng: Random, stat: VarianceStatistic) = rng.gamma(stat.nbrWeightedSamples, 1.0 / stat.sum)
}

/**
 * mean of samples will be = fixedShape / mean
 */
class GammaScalePosterior(val fixedShape: Double) : Posterior {
    override fun defaultPrior() = SumData(0.0001, 0.01)
    override fun sample(rng: Random, stat: VarianceStatistic): Double {
        return rng.gamma(stat.nbrWeightedSamples * fixedShape, 1.0 / stat.sum)
    }
}

