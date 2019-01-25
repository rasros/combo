package combo.bandit

import combo.math.DataSample
import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.ValidationException
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.abs

/**
 * A bandit optimizes an online binary decision problem. All bandits in combo are multi-variate, ie. there are multiple
 * binary decision variables.
 */
interface Bandit {

    fun chooseOrThrow(assumptions: Literals = EMPTY_INT_ARRAY): Labeling

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
    fun update(labeling: Labeling, result: Double, weight: Double = 1.0)

    /**
     * A sample of the total rewards obtained, for use in analysis and debugging.
     */
    val rewards: DataSample
}

/**
 * A [PredictionBandit] uses a machine learning model as part of the algorithm.
 */
interface PredictionBandit : Bandit {
    /**
     * The total absolute error obtained on a prediction before update.
     */
    val trainAbsError: DataSample
    /**
     * The total absolute error obtained on a prediction after update.
     */
    val testAbsError: DataSample

    /**
     * Evaluate the machine learning model on a [labeling].
     */
    fun predict(labeling: Labeling): Double

    fun train(labeling: Labeling, result: Double, weight: Double)

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        rewards.accept(result)
        testAbsError.accept(abs((result - predict(labeling)) * weight))
        train(labeling, result, weight)
        trainAbsError.accept(abs((result - predict(labeling)) * weight))
    }
}
