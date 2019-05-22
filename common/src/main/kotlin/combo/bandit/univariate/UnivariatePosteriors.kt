package combo.bandit.univariate

import combo.math.*
import kotlin.math.*
import kotlin.random.Random

interface UnivariatePosterior<E : VarianceEstimator> {

    fun sample(stat: E, rng: Random): Float

    fun update(stat: E, value: Float, weight: Float = 1.0f) {
        stat.accept(value, weight)
    }

    fun defaultPrior(): E
}

interface PooledUnivariatePosterior<E : VarianceEstimator> : UnivariatePosterior<E> {
    val pool: PooledVarianceEstimator
}

class PooledVarianceEstimator(val prior: VarianceEstimator) {
    private val arms = HashSet<VarianceEstimator>()
    val nbrArms: Int
        get() = arms.size

    var nbrWeightedSamples: Float = prior.nbrWeightedSamples
        private set

    var squaredTotalDeviations: Float = prior.squaredDeviations
        private set

    val squaredMeanDeviations: Float
        get() = means.squaredDeviations

    val grandMean: Float
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
    override fun defaultPrior() = CountData(1.0f, 2.0f)
    override fun sample(stat: CountData, rng: Random): Float {
        val alpha = stat.sum
        val beta = stat.nbrWeightedSamples - alpha
        return rng.nextBeta(alpha, beta)
    }
}

object PoissonPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(1.0f, 0.0f, 0.01f)
    override fun sample(stat: VarianceEstimator, rng: Random) = rng.nextGamma(stat.sum) / stat.nbrWeightedSamples
}

object GeometricPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(2.0f, 0.0f, 1.0f)
    override fun sample(stat: VarianceEstimator, rng: Random) =
            rng.nextBeta(stat.nbrWeightedSamples, (stat.sum - stat.nbrWeightedSamples))
}

/**
 * Section 3.1 in https://arxiv.org/pdf/1303.3390.pdf
 */
class HierarchicalNormalPosterior(override val pool: PooledVarianceEstimator) : PooledUnivariatePosterior<VarianceEstimator> {

    override fun defaultPrior() = RunningVariance(0.0f, 0.02f, 0.02f)
    override fun sample(stat: VarianceEstimator, rng: Random): Float {
        while (true) {
            val pooledAlpha = min(pool.nbrWeightedSamples, pool.nbrArms.toFloat()) / 2.0f
            val pooledBeta = pool.squaredMeanDeviations / 2
            val poolVar = pooledBeta / rng.nextGamma(pooledAlpha)
            val poolMean = pool.grandMean
            if (!poolVar.isFinite()) continue

            val groupAlpha = max(1.0f, (pool.nbrWeightedSamples - pool.nbrArms)) / 2.0f
            val groupBeta = pool.squaredTotalDeviations / 2
            val errorVar = groupBeta / rng.nextGamma(groupAlpha)
            if (!errorVar.isFinite()) continue

            val Q = 1 / (1 / poolVar + stat.nbrWeightedSamples / errorVar)
            val mean = (poolMean / poolVar + stat.mean * stat.nbrWeightedSamples / errorVar) * Q
            val value = rng.nextNormal(mean, sqrt(Q))
            if (value.isFinite()) return value
        }
    }
}

object NormalPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(0.0f, 0.02f, 0.02f)
    override fun sample(stat: VarianceEstimator, rng: Random): Float {
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
    override fun defaultPrior() = RunningVariance(0.0f, 0.02f, 2.0f)
    override fun sample(stat: VarianceEstimator, rng: Random): Float {
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

    override fun update(stat: VarianceEstimator, value: Float, weight: Float) {
        stat.accept(ln(value), weight)
    }
}

object ExponentialPosterior : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(1.0f, 0.0f, 0.01f)
    override fun sample(stat: VarianceEstimator, rng: Random) = rng.nextGamma(stat.nbrWeightedSamples) / stat.sum
}

/**
 * mean of samples will be = fixedShape / mean
 */
class GammaScalePosterior(val fixedShape: Float) : UnivariatePosterior<VarianceEstimator> {
    override fun defaultPrior() = RunningVariance(1.0f, 0.0f, 0.1f)
    override fun sample(stat: VarianceEstimator, rng: Random): Float {
        return rng.nextGamma(stat.nbrWeightedSamples * fixedShape) / stat.sum
    }
}