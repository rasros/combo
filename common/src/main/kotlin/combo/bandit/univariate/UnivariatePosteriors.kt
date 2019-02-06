@file:JvmName("Posteriors")

package combo.bandit.univariate

import combo.math.*
import kotlin.jvm.JvmName
import kotlin.math.*
import kotlin.random.Random

interface UnivariatePosterior<E : VarianceEstimator> {

    fun sample(stat: E, pool: PooledVarianceEstimator?, rng: Random): Double

    fun update(stat: E, value: Double, weight: Double = 1.0) {
        stat.accept(value, weight)
    }

    fun defaultPrior(): E

    infix fun transform(t: (Double) -> Double): UnivariatePosterior<E> = object : UnivariatePosterior<E> {
        override fun update(stat: E, value: Double, weight: Double) {
            this@UnivariatePosterior.update(stat, t.invoke(value), weight)
        }

        override fun sample(stat: E, pool: PooledVarianceEstimator?, rng: Random) =
                this@UnivariatePosterior.sample(stat, pool, rng)

        override fun defaultPrior() = this@UnivariatePosterior.defaultPrior()
    }

    fun poolEstimator(prior: VarianceEstimator): PooledVarianceEstimator? = null
}

class PooledVarianceEstimator(private val prior: VarianceEstimator) {
    private val arms = HashSet<VarianceEstimator>()
    val nbrArms: Int
        get() = arms.size

    var nbrWeightedSamples: Double = prior.nbrWeightedSamples
        private set

    var squaredTotalDeviations: Double = prior.squaredDeviations
        private set

    val squaredMeanDeviations: Double
        get() = means.squaredDeviations

    val grandMean: Double
        get() = means.mean

    private var means = prior.copy()

    fun addArm(armData: VarianceEstimator) {
        arms.add(armData)
    }

    fun removeArm(armData: VarianceEstimator) {
        arms.remove(armData)
    }

    fun recalculate() {
        nbrWeightedSamples = prior.nbrWeightedSamples
        means = prior.copy()
        squaredTotalDeviations = prior.squaredDeviations
        for (g in arms) {
            means.accept(g.mean)
            squaredTotalDeviations += g.squaredDeviations
            nbrWeightedSamples += g.nbrWeightedSamples
        }
    }
}

object BinomialPosterior : UnivariatePosterior<CountData> {
    override fun defaultPrior() = CountData(1.0, 2.0)
    override fun sample(stat: CountData, pool: PooledVarianceEstimator?, rng: Random): Double {
        val alpha = stat.sum
        val beta = stat.nbrWeightedSamples - alpha
        return rng.nextBeta(alpha, beta)
    }
}

object PoissonPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(1.0, 0.0, 0.01)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random) = rng.nextGamma(stat.sum) / stat.nbrWeightedSamples
}

object GeometricPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(2.0, 0.0, 1.0)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random) =
            rng.nextBeta(stat.nbrWeightedSamples, stat.sum - stat.nbrWeightedSamples)
}

/**
 * Section 3.1 in https://arxiv.org/pdf/1303.3390.pdf
 */
object HierarchicalNormalPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 0.02)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random): Double {
        val pooledVariance = pool as PooledVarianceEstimator
        while (true) {
            val pooledAlpha = min(pooledVariance.nbrWeightedSamples, pooledVariance.nbrArms.toDouble()) / 2.0
            val pooledBeta = pooledVariance.squaredMeanDeviations / 2
            val poolVar = pooledBeta / rng.nextGamma(pooledAlpha)
            val poolMean = pooledVariance.grandMean
            if (!poolVar.isFinite()) continue

            val groupAlpha = max(1.0, (pooledVariance.nbrWeightedSamples - pooledVariance.nbrArms)) / 2.0
            val groupBeta = pooledVariance.squaredTotalDeviations / 2
            val errorVar = groupBeta / rng.nextGamma(groupAlpha)
            if (!errorVar.isFinite()) continue

            val Q = 1 / (1 / poolVar + stat.nbrWeightedSamples / errorVar)
            val mean = (poolMean / poolVar + stat.mean * stat.nbrWeightedSamples / errorVar) * Q
            val value = rng.nextNormal(mean, sqrt(Q))
            if (value.isFinite()) return value
        }
    }

    override fun poolEstimator(prior: VarianceEstimator) = PooledVarianceEstimator(prior)
}

object NormalPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 0.02)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random): Double {
        while (true) {
            val alpha = stat.nbrWeightedSamples / 2
            val beta = stat.squaredDeviations / 2
            val lambda = stat.nbrWeightedSamples
            val sampleVariance = beta / rng.nextGamma(alpha)
            if (!sampleVariance.isFinite()) continue
            val value = rng.nextNormal(stat.mean, sqrt(sampleVariance / lambda))
            if (value.isFinite()) return value
        }
    }
}

object LogNormalPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(0.0, 0.02, 2.0)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random): Double {
        while (true) {
            val alpha = stat.nbrWeightedSamples / 2
            val beta = stat.squaredDeviations / 2
            val lambda = stat.nbrWeightedSamples
            val sampleVariance = beta / rng.nextGamma(alpha)
            if (!sampleVariance.isFinite()) continue
            val sampleMean = rng.nextNormal(stat.mean, sqrt(sampleVariance / lambda))
            val value = exp(sampleMean + sampleVariance / 2)
            if (value.isFinite()) return value
        }
    }

    override fun update(stat: VarianceEstimator, value: Double, weight: Double) {
        stat.accept(ln(value), weight)
    }
}

object ExponentialPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(1.0, 0.0, 0.01)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random) = rng.nextGamma(stat.nbrWeightedSamples) / stat.sum
}

/**
 * mean of samples will be = fixedShape / mean
 */
class GammaScalePosterior(val fixedShape: Double) : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(1.0, 0.0, 0.01)
    override fun sample(stat: VarianceEstimator, pool: PooledVarianceEstimator?, rng: Random): Double {
        return rng.nextGamma(stat.nbrWeightedSamples * fixedShape) / stat.sum
    }
}

