package combo.bandit

import combo.math.DataSample
import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.ValidationException
import combo.util.EMPTY_INT_ARRAY
import kotlin.math.abs

interface Bandit {

    fun chooseOrThrow(assumptions: Literals = EMPTY_INT_ARRAY): Labeling

    fun choose(assumptions: Literals = EMPTY_INT_ARRAY) =
            try {
                chooseOrThrow(assumptions)
            } catch (e: ValidationException) {
                null
            }

    fun update(labeling: Labeling, result: Double, weight: Double = 1.0)
    val rewards: DataSample
}

interface PredictionBandit : Bandit {
    val trainAbsError: DataSample
    val testAbsError: DataSample

    fun predict(labeling: Labeling): Double
    fun train(labeling: Labeling, result: Double, weight: Double)

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        rewards.accept(result)
        testAbsError.accept(abs((result - predict(labeling)) * weight))
        train(labeling, result, weight)
        trainAbsError.accept(abs((result - predict(labeling)) * weight))
    }
}
