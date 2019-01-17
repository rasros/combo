package combo.bandit

import combo.math.DataSample
import combo.sat.Labeling
import combo.sat.ValidationException
import kotlin.math.abs

interface Bandit {

    fun chooseOrThrow(assumptions: IntArray): Labeling

    fun choose(assumptions: IntArray) =
            try {
                chooseOrThrow(assumptions)
            } catch (e: ValidationException) {
                null
            }

    fun update(labeling: Labeling, result: Double, weight: Double)
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
        update(labeling, result, weight)
        trainAbsError.accept(abs((result - predict(labeling)) * weight))
    }
}
