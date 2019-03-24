package combo.bandit.univariate

import combo.math.RunningVariance
import combo.math.VarianceEstimator
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

    fun beginRound(rng: Random) {}
    fun evaluate(data: E, rng: Random): Float
    fun completeRound(data: E, value: Float, weight: Float) = updateData(data, value, weight)

    fun updateData(data: E, value: Float, weight: Float) = data.accept(value, weight)

    /**
     * This will be called when a bandit is initialized, it is often called multiple times during initialization.
     */
    fun baseData(): E

    /**
     * This is called when arms are added without initializing, usually when loading historic data.
     */
    fun addArm(armData: E) {}

    /**
     * This is used when an arm is removed for various reasons, such as when loading historic data.
     */
    fun removeArm(armData: E) {}
}

/**
 * This method assigns a Bayesian posterior distribution to each arm and during each round selects the maximum from a
 * sampled value from each posterior. The prior is encoded directly into the [VarianceEstimator] as pseudo samples.
 */
class ThompsonSampling<E : VarianceEstimator> @JvmOverloads constructor(
        val posterior: UnivariatePosterior<E>, val prior: E = posterior.defaultPrior()) : BanditPolicy<E> {

    private val pool = posterior.poolEstimator(prior)

    override fun evaluate(data: E, rng: Random) = posterior.sample(data, pool, rng)

    override fun updateData(data: E, value: Float, weight: Float) {
        posterior.update(data, value, weight)
    }

    override fun completeRound(data: E, value: Float, weight: Float) {
        posterior.update(data, value, weight)
        pool?.recalculate()
    }

    @Suppress("UNCHECKED_CAST")
    override fun baseData() = prior.copy() as E

    override fun removeArm(armData: E) {
        pool?.removeArm(armData)
        pool?.recalculate()
    }

    override fun addArm(armData: E) {
        pool?.addArm(armData)
        pool?.recalculate()
    }
}

/**
 * This algorithm selects the best option deterministically by appending an uncertainty padding term to the mean.
 * Depending on the type of reward there are improvements available in either [UCB1Tuned] or [UCB1Normal].
 * @param alpha exploration parameter, with default 1.0. Higher alpha means more exploration and less exploration with
 * lower.
 */
class UCB1 @JvmOverloads constructor(val alpha: Float = 1.0f) : BanditPolicy<VarianceEstimator> {

    private var totalSamples = 0.0

    override fun evaluate(data: VarianceEstimator, rng: Random) =
            if (data.nbrWeightedSamples <= 1.0) Float.POSITIVE_INFINITY
            else (data.mean + alpha * sqrt(2 * ln(totalSamples) / data.nbrWeightedSamples)).toFloat()

    override fun baseData() = RunningVariance()

    override fun completeRound(data: VarianceEstimator, value: Float, weight: Float) {
        data.accept(value, weight)
        totalSamples += weight
    }

    override fun addArm(armData: VarianceEstimator) {
        totalSamples += armData.nbrWeightedSamples
    }

    override fun removeArm(armData: VarianceEstimator) {
        totalSamples -= armData.nbrWeightedSamples
    }
}

/**
 * This is an improvement over UCB1 for normal distributed rewards.
 * @param alpha exploration parameter, with default 1.0. Higher alpha means more exploration and less exploration with
 * lower.
 */
class UCB1Normal @JvmOverloads constructor(val alpha: Float = 1.0f) : BanditPolicy<SquaredEstimator> {

    private var nbrArms = 0

    override fun evaluate(data: SquaredEstimator, rng: Random) =
            if (data.nbrWeightedSamples < 8 * ln(nbrArms.toFloat()) || nbrArms <= 1) Float.POSITIVE_INFINITY
            else {
                val nj = data.nbrWeightedSamples
                val p1 = (data.meanOfSquares - nj * data.mean * data.mean) / (nj - 1)
                data.mean + alpha * sqrt(16 * p1 * (ln(nbrArms - 1.0f) / nj))
            }

    override fun baseData() = SquaredEstimator()

    override fun addArm(armData: SquaredEstimator) {
        nbrArms++
    }

    override fun removeArm(armData: SquaredEstimator) {
        nbrArms--
    }
}

/**
 * This is a mostly strict improvement over UCB1 for binary rewards.
 * @param alpha exploration parameter, with default 1.0. Higher alpha means more exploration and less exploration with
 * lower.
 */
class UCB1Tuned @JvmOverloads constructor(val alpha: Float = 1.0f) : BanditPolicy<SquaredEstimator> {
    private var totalSamples = 0.0f
    override fun evaluate(data: SquaredEstimator, rng: Random) =
            if (data.nbrWeightedSamples <= 1.0f) Float.POSITIVE_INFINITY
            else {
                val padding = ln(totalSamples) / data.nbrWeightedSamples
                val V = data.meanOfSquares - data.mean * data.mean + sqrt(2f * padding)
                data.mean + alpha * sqrt(padding * min(0.25f, V))
            }

    override fun baseData() = SquaredEstimator()

    override fun completeRound(data: SquaredEstimator, value: Float, weight: Float) {
        data.accept(value, weight)
        totalSamples += weight
    }

    override fun addArm(armData: SquaredEstimator) {
        totalSamples += armData.nbrWeightedSamples
    }

    override fun removeArm(armData: SquaredEstimator) {
        totalSamples -= armData.nbrWeightedSamples
    }
}

class SquaredEstimator private constructor(private val base: RunningVariance, meanOfSquares: Float) : VarianceEstimator by base {
    constructor(mean: Float = 0.0f,
                meanOfSquares: Float = 0.0f,
                squaredDeviations: Float = 0.0f,
                nbrWeightedSamples: Float = 0.0f) : this(RunningVariance(mean, squaredDeviations, nbrWeightedSamples), meanOfSquares)

    var meanOfSquares: Float = meanOfSquares
        private set

    override fun accept(value: Float, weight: Float) {
        base.accept(value, weight)
        if (weight == nbrWeightedSamples)
            meanOfSquares = value * value
        else {
            val oldMS = meanOfSquares
            meanOfSquares = oldMS + (value * value - oldMS) * (weight / nbrWeightedSamples)
        }
    }

    override fun copy() = SquaredEstimator(mean, meanOfSquares, squaredDeviations, nbrWeightedSamples)
    override fun toString() = "SquaredEstimator(mean=$mean, meanOfSquares=$meanOfSquares, squaredDeviations=$squaredDeviations, nbrSamples=$nbrSamples)"
}

/**
 * Epsilon greedy is the most basic bandit policy. Each round a random arm is used with probability [epsilon], otherwise
 * the best alternative is used.
 */
class EpsilonGreedy @JvmOverloads constructor(
        val epsilon: Float = 0.1f) : BanditPolicy<VarianceEstimator> {

    init {
        require(epsilon in 0.0..1.0) { "Epsilon parameter must be within 0-1, got $epsilon" }
    }

    private var nextP = 0.0f

    override fun evaluate(data: VarianceEstimator, rng: Random): Float {
        return if (nextP < epsilon)
            rng.nextFloat()
        else data.mean
    }

    override fun baseData() = RunningVariance()
    override fun beginRound(rng: Random) {
        nextP = rng.nextFloat()
    }
}

/**
 * Epsilon-n greedy is an improvement over EpsilonGreedy, where [epsilon] is decreased with the number of steps. In this
 * version, [epsilon] can be greater than 1.
 */
class EpsilonDecreasing @JvmOverloads constructor(
        val epsilon: Float = 2.0f, val decay: Float = 0.5f) : BanditPolicy<VarianceEstimator> {

    private var totalSamples = 0.0f
    private var nextP = 0.0f

    init {
        require(epsilon > 0.0) { "Epsilon parameter must be within 0-1, got $epsilon" }
    }

    override fun evaluate(data: VarianceEstimator, rng: Random): Float {
        val eps = min(1.0f, epsilon / totalSamples.pow(decay))
        return if (nextP < eps)
            rng.nextFloat()
        else data.mean
    }

    override fun baseData() = RunningVariance()

    override fun beginRound(rng: Random) {
        nextP = rng.nextFloat()
    }

    override fun completeRound(data: VarianceEstimator, value: Float, weight: Float) {
        data.accept(value, weight)
        totalSamples += weight
    }

    override fun addArm(armData: VarianceEstimator) {
        totalSamples += armData.nbrWeightedSamples
    }

    override fun removeArm(armData: VarianceEstimator) {
        totalSamples -= armData.nbrWeightedSamples
    }
}
