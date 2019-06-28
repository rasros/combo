package combo.bandit

import combo.math.DataSample
import combo.sat.Instance
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
interface Bandit<D : BanditData> {

    /**
     * Generate the next instance to try out, throwing [ValidationException] on failure.
     * @param assumptions these are values that must be set by the returned instance, the format is in Dimacs.
     */
    fun chooseOrThrow(assumptions: IntCollection = EmptyCollection): Instance

    /**
     * Generate the next instance to try out, returning null on failure.
     * @param assumptions these are values that must be set by the returned instance, the format is in Dimacs.
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
     * Update multiple results, all arrays must be same length.
     */
    fun updateAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray? = null) {
        require(instances.size == results.size) { "Arrays must be same length." }
        if (weights != null) require(weights.size == results.size) { "Arrays must be same length." }
        for (i in instances.indices)
            update(instances[i], results[i], weights?.get(i) ?: 1.0f)
    }

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

interface BanditData {
    fun migrate(from: IntArray, to: IntArray): BanditData
}

/**
 * A [PredictionBandit] uses a machine learning model as part of the algorithm. This machine learning algorithm can also
 * be used to make predictions about an [Instance]. In addition, further
 */
interface PredictionBandit<D : BanditData> : Bandit<D> {

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

interface BanditParameters {
    /**
     * Set the random seed to a specific value to have a reproducible algorithm. By default current system time.
     */
    val randomSeed: Int

    /**
     * Whether the bandit should maximize or minimize the total rewards. By default true.
     */
    val maximize: Boolean

    /**
     * All rewards are added to this for inspecting how well the bandit performs. By default [VoidSample].
     */
    val rewards: DataSample
}

