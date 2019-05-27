package combo.bandit

import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.sat.Instance
import combo.sat.Literals
import combo.sat.ValidationException
import combo.util.EmptyCollection
import combo.util.IntCollection
import kotlin.math.abs

/**
 * A bandit optimizes an online binary decision problem. These bandits are multi-variate,
 * ie. there are multiple binary decision variables.
 *
 * TODO further bandit explanation, rewards optimization, arms, choose/update loop, iid, stochastic non-adversial.
 */
interface Bandit<D> {

    /**
     * TODO
     */
    fun chooseOrThrow(assumptions: IntCollection = EmptyCollection): Instance

    /**
     * TODO
     */
    fun choose(assumptions: IntCollection = EmptyCollection) =
            try {
                chooseOrThrow(assumptions)
            } catch (e: ValidationException) {
                null
            }

    /**
     * Update the result of an instance.
     *
     * @param instance the assigned values used for the result.
     * @param result the reward obtained. If this constitutes multiple rewards then set weight appropriately and divide
     * by the [weight].
     * @param weight update strength. Can be used to signal importance of a result. The higher value the more the
     * algorithm is updated. If these are number of trials (or observations) in for example a binomial distributed
     * reward, then the result should be divided by weight before calling update (ie. the [result] should be mean).
     */
    fun update(instance: Instance, result: Float, weight: Float = 1.0f)

    /**
     * Add historic data to the bandit, this can be used to store and re-start the bandit. Any existing data is combined
     * with the imported data importing. It is not recommended to do import of any data that the bandit has already
     * seen, since that will cause an underestimation of variance in the rewards.
     *
     * @param data added to the bandit.
     * @param restructure whether the bandit structure should exactly fit that of the imported data. If set to true
     * some data might be lost in the import but can be used to keep multiple parallel bandits in sync. If set to false
     * the merge will only be on existing summary statistics.
     */
    fun importData(data: D, restructure: Boolean = false)

    /**
     * Exports all data to use for external storage. They can be used in a new [Bandit] instance that
     * continues optimizing through the [importData] function.
     */
    fun exportData(): D

    /**
     * A sample of the total rewards obtained, for use in analysis and debugging.
     */
    var rewards: DataSample

    /**
     * Set the random seed to a specific value to have a reproducible algorithm.
     */
    var randomSeed: Int

    /**
     * Whether the bandit should maximize or minimize the total rewards.
     */
    var maximize: Boolean
}

/**
 * A [PredictionBandit] uses a machine learning model as part of the algorithm. This machine learning algorithm can also
 * be used to make predictions about an [Instance]. In addition, further
 */
interface PredictionBandit<D> : Bandit<D> {

    /**
     * The total absolute error obtained on a prediction before update.
     */
    var trainAbsError: DataSample

    /**
     * The total absolute error obtained on a prediction after update.
     */
    var testAbsError: DataSample

    /**
     * Evaluate the machine learning model on a [instance].
     */
    fun predict(instance: Instance): Float

    fun train(instance: Instance, result: Float, weight: Float)

    override fun update(instance: Instance, result: Float, weight: Float) {
        rewards.accept(result, weight)
        testAbsError.accept(abs((result - predict(instance)) * weight))
        train(instance, result, weight)
        trainAbsError.accept(abs((result - predict(instance)) * weight))
    }
}


/**
 * This class holds the data in the leaf nodes. The order of the literals in [setLiterals] is significant and cannot
 * be changed.
 */
data class LiteralData<out E : VarianceEstimator>(val setLiterals: Literals, val data: E) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (other !is LiteralData<*>) return false
        return setLiterals.contentEquals(other.setLiterals) && data == other.data
    }

    override fun hashCode(): Int {
        var result = setLiterals.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}

data class InstanceData<out E : VarianceEstimator>(val instance: Instance, val data: E) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (other !is InstanceData<*>) return false
        return instance == other.instance && data == other.data
    }

    override fun hashCode(): Int {
        var result = instance.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}
