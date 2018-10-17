@file:JvmName("Posteriors")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

interface Posterior {

    fun sample(rng: Rng, stat: VarianceStatistic): Double

    fun update(stat: VarianceStatistic, value: Double, weight: Double = 1.0) {
        stat.accept(value, weight)
    }

    fun defaultPrior(): VarianceStatistic

    fun transformed(t: Transform): Posterior = object : Posterior {
        override fun sample(rng: Rng, stat: VarianceStatistic) = t.inverse(this@Posterior.sample(rng, stat))

        override fun update(stat: VarianceStatistic, value: Double, weight: Double) {
            this@Posterior.update(stat, t.apply(value), weight)
        }

        override fun defaultPrior() = t.backtransform(this@Posterior.defaultPrior())
    }
}

fun binomial() = object : Posterior {
    /** results in uniform prior over p */
    override fun defaultPrior() = RunningVariance().apply { accept(.5, 2.0) }

    override fun sample(rng: Rng, stat: VarianceStatistic): Double {
        val alpha = stat.sum()
        val beta = stat.nbrWeightedSamples - alpha
        return rng.beta(alpha, beta)
    }
}

fun poisson() = object : Posterior {
    /** results in wide Gamma(0.01, 0.01) prior over p */
    override fun defaultPrior() = RunningVariance().apply { accept(1.0, .01) }

    override fun sample(rng: Rng, stat: VarianceStatistic) =
            rng.gamma(stat.sum(), 1.0 / stat.nbrWeightedSamples)
}

fun geometric() = object : Posterior {
    /** results in uniform prior over p */
    override fun defaultPrior() = RunningVariance().apply { accept(2.0, 1.0) }

    override fun sample(rng: Rng, stat: VarianceStatistic) =
            rng.beta(stat.nbrWeightedSamples, stat.sum() - stat.nbrWeightedSamples)
}

fun normal() = object : Posterior {
    /** this design does not permit individual prior for sigma and mu */
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 0.02)

    override fun sample(rng: Rng, stat: VarianceStatistic): Double {
        val alpha = stat.nbrWeightedSamples / 2.0
        val beta = stat.squaredDeviations / 2.0
        val sampleVariance = generateSequence { 1 / rng.inverseGamma(alpha, beta) }
                .map { sqrt(it / stat.nbrWeightedSamples) }
                .first { !it.isNaN() && !it.isInfinite() }
        return generateSequence { rng.gaussian(stat.mean, sampleVariance) }
                .first { !it.isNaN() && !it.isInfinite() }
    }
}

fun logNormal() = object : Posterior {
    /** same prior as normal */
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 0.02)

    override fun sample(rng: Rng, stat: VarianceStatistic): Double {
        val alpha = stat.nbrWeightedSamples / 2.0
        val beta = stat.squaredDeviations / 2.0
        var sample: Double
        do {
            val sampleVariance = generateSequence { 1 / rng.inverseGamma(alpha, beta) }
                    .map { sqrt(it / stat.nbrWeightedSamples) }
                    .first { !it.isNaN() && !it.isInfinite() }
            val sampleMean = generateSequence { rng.gaussian(stat.mean, sampleVariance) }
                    .first { !it.isNaN() && !it.isInfinite() }
            sample = exp(sampleMean + sampleVariance * 0.5)
        } while (sample.isNaN() || sample.isInfinite())
        return sample
    }


    override fun update(stat: VarianceStatistic, value: Double, weight: Double) {
        stat.accept(ln(value), weight)
    }
}

fun exponential() = object : Posterior {
    override fun defaultPrior() = RunningVariance().apply { accept(0.01, 0.01) }

    override fun sample(rng: Rng, stat: VarianceStatistic) = rng.gamma(stat.nbrWeightedSamples, 1.0 / stat.sum())
}

/**
 * mean of samples will be = fixedShape / mean
 */
fun gammaScale(fixedShape: Double) = object : Posterior {
    override fun defaultPrior() = RunningVariance().apply { accept(0.01, 0.01) }

    override fun sample(rng: Rng, stat: VarianceStatistic): Double {
        return rng.gamma(stat.nbrWeightedSamples * fixedShape, 1 / stat.sum())
    }
}

