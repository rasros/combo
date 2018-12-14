package combo.bandit

import combo.math.DataSample
import combo.sat.Labeling
import combo.sat.ValidationException
import combo.sat.SolverConfig
import kotlin.math.abs

interface Bandit {

    fun chooseOrThrow(contextLiterals: IntArray): Labeling

    fun choose(contextLiterals: IntArray) =
            try {
                chooseOrThrow(contextLiterals)
            } catch (e: ValidationException) {
                null
            }

    fun update(labeling: Labeling, result: Double, weight: Double)
    val config: SolverConfig
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
