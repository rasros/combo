package combo.bandit

import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.sat.Instance
import combo.sat.Literals
import combo.sat.ValidationException
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.abs

/**
 * A bandit optimizes an online binary decision problem. These bandits are multi-variate,
 * ie. there are multiple binary decision variables.
 */
interface Bandit<D> {

    fun chooseOrThrow(assumptions: Literals = EMPTY_INT_ARRAY): Instance

    fun choose(assumptions: Literals = EMPTY_INT_ARRAY) =
            try {
                chooseOrThrow(assumptions)
            } catch (e: ValidationException) {
                null
            }

    /**
     * Add the results of a bandit evaluation. The [weight] can be used to increase the importance of the update, for
     * binomial this is interpreted as the n-parameter.
     */
    fun update(instance: Instance, result: Double, weight: Double = 1.0)

    /**
     * Add historic data to the bandit, this can be used to store and re-start the bandit. In general, any existing
     * data is lost when importing.
     */
    fun importData(historicData: D)

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
    var randomSeed: Long

    /**
     * Whether the bandit should maximize or minimize the total rewards.
     */
    var maximize: Boolean
}

/**
 * A [PredictionBandit] uses a machine learning model as part of the algorithm.
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
    fun predict(instance: Instance): Double

    fun train(instance: Instance, result: Double, weight: Double)

    override fun update(instance: Instance, result: Double, weight: Double) {
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
class LiteralData<E : VarianceEstimator>(val setLiterals: Literals, val data: E)

class LabelingData<E : VarianceEstimator>(val instance: Instance, val data: E)
