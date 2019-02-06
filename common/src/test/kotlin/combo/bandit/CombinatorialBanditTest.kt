package combo.bandit

import combo.bandit.univariate.UCB1
import combo.math.VarianceEstimator
import combo.sat.BitFieldLabelingFactory
import combo.sat.Problem
import combo.sat.solvers.ExhaustiveSolver

class CombinatorialBanditTest : BanditTest<Array<LabelingData<VarianceEstimator>>>() {

    override fun bandit(problem: Problem, type: BanditType) = CombinatorialBandit(
            ExhaustiveSolver(problem).apply {
                randomSeed = 0L
                labelingFactory = BitFieldLabelingFactory
            }.sequence().toList().toTypedArray().let { if (it.size > 100) it.sliceArray(0 until 100) else it },
            UCB1())
}