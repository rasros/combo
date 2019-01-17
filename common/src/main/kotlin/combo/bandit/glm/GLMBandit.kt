package combo.bandit.glm

import combo.bandit.PredictionBandit
import combo.math.DataSample
import combo.sat.Labeling
import combo.sat.Problem
import combo.sat.solvers.LinearObjective
import combo.sat.solvers.Optimizer

class GLMBandit(val problem: Problem,
                val maximize: Boolean = true,
                override val rewards: DataSample,
                val optimizer: Optimizer<LinearObjective>,
                override val trainAbsError: DataSample,
                override val testAbsError: DataSample) : PredictionBandit {

    override fun predict(labeling: Labeling): Double {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun train(labeling: Labeling, result: Double, weight: Double) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chooseOrThrow(assumptions: IntArray): Labeling {
        val weights = DoubleArray(0)

        val l = optimizer.optimize(LinearObjective(maximize, weights), assumptions)
        TODO("not implemented")
    }

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        TODO("not implemented")
    }
}
