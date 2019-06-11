package combo.bandit.univariate

import combo.math.*
import kotlin.jvm.JvmOverloads
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Bandit policy is used internally by either [UnivariateBandit] or some of the [combo.bandit.Bandit] implementations.
 */
interface BanditPolicy<E : VarianceEstimator> {

    fun evaluate(data: E, step: Long, maximize: Boolean, rng: Random): Float
    fun update(data: E, value: Float, weight: Float) = accept(data, value, weight)

    fun accept(data: E, value: Float, weight: Float) = data.accept(value, weight)

    /**
     * This will be copied to each bandit during initialization and during operation of [combo.bandit.Bandit] algorithms
     * where the number of arms are dynamic.
     */
    val prior: E

    @Suppress("UNCHECKED_CAST")
    fun baseData() = prior.copy() as E

    /**
     * This is called when arms are added without initializing, usually when loading historic data.
     */
    fun addArm(armData: E) {}

    /**
     * This is used when an arm is removed for various reasons, such as when loading historic data.
     */
    fun removeArm(armData: E) {}

    fun copy(): BanditPolicy<E> = this
}

/**
 * This method assigns a Bayesian posterior distribution to each arm and during each round selects the maximum from a
 * sampled value from each posterior. The prior is encoded directly into the [VarianceEstimator] as pseudo samples.
 */
class ThompsonSampling<E : VarianceEstimator> @JvmOverloads constructor(
        val posterior: UnivariatePosterior<E>, override val prior: E = posterior.defaultPrior())
    : BanditPolicy<E> {

    override fun evaluate(data: E, step: Long, maximize: Boolean, rng: Random): Float {
        val sample = posterior.sample(data, rng)
        return if (maximize) sample else -sample
    }

    override fun accept(data: E, value: Float, weight: Float) {
        posterior.update(data, value, weight)
    }

    override fun update(data: E, value: Float, weight: Float) {
        posterior.update(data, value, weight)
    }
}

/**
 * This method assigns a Bayesian posterior distribution to each arm and during each round selects the maximum from a
 * sampled value from each posterior. For conjugate posteriors with multiple parameters, ie. only normal and log-normal,
 * then the variance can be pooled such that it is estimated once for all bandits instead of a separate variance
 * for each bandit. The prior is encoded directly into the [VarianceEstimator] as pseudo samples.
 */
class PooledThompsonSampling<E : VarianceEstimator> @JvmOverloads constructor(
        val posterior: PooledUnivariatePosterior<E>, override val prior: E = posterior.defaultPrior())
    : BanditPolicy<E> {

    override fun evaluate(data: E, step: Long, maximize: Boolean, rng: Random): Float {
        val sample = posterior.sample(data, rng)
        return if (maximize) sample else -sample
    }

    override fun accept(data: E, value: Float, weight: Float) {
        posterior.update(data, value, weight)
    }

    override fun update(data: E, value: Float, weight: Float) {
        posterior.update(data, value, weight)
        posterior.pool.recalculate()
    }

    override fun removeArm(armData: E) {
        posterior.pool.removeArm(armData)
        posterior.pool.recalculate()
    }

    override fun addArm(armData: E) {
        posterior.pool.addArm(armData)
        posterior.pool.recalculate()
    }

    override fun copy() = PooledThompsonSampling(posterior.copy(), prior)
}

/**
 * This algorithm selects the best option deterministically by appending an uncertainty padding term to the mean.
 * This is designed for binomial rewards only, for normal rewards use [UCB1Normal], otherwise [UCB1Tuned].
 * @param alpha exploration parameter, with default 1.0. Higher alpha means more exploration and less exploration with
 * lower.
 */
class UCB1 @JvmOverloads constructor(val alpha: Float = 1.0f, override val prior: BinaryEstimator = BinarySum())
    : BanditPolicy<BinaryEstimator> {

    private var totalSamples = 0.0f

    override fun evaluate(data: BinaryEstimator, step: Long, maximize: Boolean, rng: Random): Float {
        return if (data.nbrWeightedSamples < 1.0f) Float.POSITIVE_INFINITY
        else {
            val score = if (maximize) data.mean else -data.mean
            score + alpha * sqrt(2 * ln(totalSamples) / data.nbrWeightedSamples)
        }
    }

    override fun update(data: BinaryEstimator, value: Float, weight: Float) {
        accept(data, value, weight)
        totalSamples += weight
    }

    override fun addArm(armData: BinaryEstimator) {
        totalSamples += armData.nbrWeightedSamples
    }

    override fun removeArm(armData: BinaryEstimator) {
        totalSamples -= armData.nbrWeightedSamples
    }

    override fun copy() = UCB1(alpha, prior).also {
        it.totalSamples = totalSamples

    }
}

/**
 * This is a version of [UCB1] for normal distributed rewards.
 * @param alpha exploration parameter, with default 1.0. Higher alpha means more exploration and less exploration with
 * lower.
 */
class UCB1Normal @JvmOverloads constructor(
        val alpha: Float = 1.0f, override val prior: SquaredEstimator = RunningSquaredMeans())
    : BanditPolicy<SquaredEstimator> {

    private var nbrArms = 0

    override fun evaluate(data: SquaredEstimator, step: Long, maximize: Boolean, rng: Random): Float {
        return if (data.nbrWeightedSamples < 8 * ln(nbrArms.toFloat()) || nbrArms <= 1) Float.POSITIVE_INFINITY
        else {
            val score = if (maximize) data.mean else -data.mean
            val nj = data.nbrWeightedSamples
            val p1 = (data.meanOfSquares - nj * data.mean * data.mean) / (nj - 1)
            score + alpha * sqrt(16 * p1 * (ln(nbrArms - 1.0f) / nj))
        }
    }

    override fun addArm(armData: SquaredEstimator) {
        nbrArms++
    }

    override fun removeArm(armData: SquaredEstimator) {
        nbrArms--
    }

    override fun copy() = UCB1Normal(alpha, prior).also {
        it.nbrArms = nbrArms
    }
}

/**
 * This is a mostly strict improvement over UCB1 for binary rewards.
 * @param alpha exploration parameter, with default 1.0. Higher alpha means more exploration and less exploration with
 * lower.
 */
class UCB1Tuned @JvmOverloads constructor(
        val alpha: Float = 1.0f, override val prior: SquaredEstimator = RunningSquaredMeans())
    : BanditPolicy<SquaredEstimator> {

    private var totalSamples = 0.0f

    override fun evaluate(data: SquaredEstimator, step: Long, maximize: Boolean, rng: Random): Float {
        return if (data.nbrWeightedSamples <= 1.0f) Float.POSITIVE_INFINITY
        else {
            val padding = ln(totalSamples) / data.nbrWeightedSamples
            val V = data.meanOfSquares - data.mean * data.mean + sqrt(2f * padding)
            val score = if (maximize) data.mean else -data.mean
            score + alpha * sqrt(padding * min(0.25f, V))
        }
    }

    override fun update(data: SquaredEstimator, value: Float, weight: Float) {
        accept(data, value, weight)
        totalSamples += weight
    }

    override fun addArm(armData: SquaredEstimator) {
        totalSamples += armData.nbrWeightedSamples
    }

    override fun removeArm(armData: SquaredEstimator) {
        totalSamples -= armData.nbrWeightedSamples
    }

    override fun copy() = UCB1Tuned(alpha, prior).also {
        it.totalSamples = totalSamples
    }
}

class UniformSelection(override val prior: RunningVariance = RunningVariance()) : BanditPolicy<VarianceEstimator> {
    override fun evaluate(data: VarianceEstimator, step: Long, maximize: Boolean, rng: Random) = rng.nextFloat()
}

/**
 * Epsilon greedy is the most basic bandit policy. Each round a random arm is used with probability [epsilon], otherwise
 * the best alternative is used.
 */
class EpsilonGreedy @JvmOverloads constructor(
        val epsilon: Float = 0.1f, override val prior: VarianceEstimator = RunningVariance())
    : BanditPolicy<VarianceEstimator> {

    init {
        require(epsilon in 0.0..1.0) { "Epsilon parameter must be within 0-1, got $epsilon" }
    }


    override fun evaluate(data: VarianceEstimator, step: Long, maximize: Boolean, rng: Random): Float {
        return if (Random(step).nextFloat() < epsilon) {
            rng.nextFloat()
        } else {
            if (maximize) data.mean else -data.mean
        }
    }
}

/**
 * Epsilon-n greedy is an improvement over EpsilonGreedy, where [epsilon] is decreased with the number of steps. In this
 * version, [epsilon] can be greater than 1.
 */
class EpsilonDecreasing @JvmOverloads constructor(
        val epsilon: Float = 2.0f, val decay: Float = 0.5f, override val prior: RunningVariance = RunningVariance())
    : BanditPolicy<VarianceEstimator> {

    private var totalSamples = 0.0f

    init {
        require(epsilon > 0.0) { "Epsilon parameter must be within 0-1, got $epsilon" }
    }

    override fun evaluate(data: VarianceEstimator, step: Long, maximize: Boolean, rng: Random): Float {
        val eps = min(1.0f, epsilon / totalSamples.pow(decay))
        return if (Random(step).nextFloat() < eps) {
            rng.nextFloat()
        } else {
            if (maximize) data.mean else -data.mean
        }
    }

    override fun update(data: VarianceEstimator, value: Float, weight: Float) {
        accept(data, value, weight)
        totalSamples += weight
    }

    override fun addArm(armData: VarianceEstimator) {
        totalSamples += armData.nbrWeightedSamples
    }

    override fun removeArm(armData: VarianceEstimator) {
        totalSamples -= armData.nbrWeightedSamples
    }

    override fun copy() = EpsilonDecreasing(epsilon, decay, prior).also {
        it.totalSamples = totalSamples
    }
}
