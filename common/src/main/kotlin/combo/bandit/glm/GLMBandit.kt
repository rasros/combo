package combo.bandit.glm

import combo.bandit.PredictionBandit
import combo.math.DataSample
import combo.sat.Labeling
import combo.sat.SolverConfig
import combo.sat.solvers.LinearObjective
import combo.sat.solvers.Optimizer

class GLMBandit(override val config: SolverConfig,
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

    override fun chooseOrThrow(contextLiterals: IntArray): Labeling {
        val weights = DoubleArray(0)

        val l = optimizer.optimize(LinearObjective(weights), contextLiterals)
        TODO("not implemented")
    }

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        TODO("not implemented")
    }
}
