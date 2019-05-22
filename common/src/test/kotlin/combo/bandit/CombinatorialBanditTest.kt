package combo.bandit

import combo.bandit.univariate.UCB1
import combo.bandit.univariate.UCB1Normal
import combo.bandit.univariate.UCB1Tuned
import combo.math.VarianceEstimator
import combo.sat.Problem
import combo.sat.solvers.ExhaustiveSolver

class CombinatorialBanditTest : BanditTest<Array<InstanceData<VarianceEstimator>>>() {

    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): CombinatorialBandit<VarianceEstimator> {
        val instances = ExhaustiveSolver(problem).apply { randomSeed = 0 }
                .asSequence().take(100).toList().toTypedArray()

        return when (type) {
            BanditType.BINOMIAL -> CombinatorialBandit(instances, UCB1()) as CombinatorialBandit<VarianceEstimator>
            BanditType.NORMAL -> CombinatorialBandit(instances, UCB1Normal()) as CombinatorialBandit<VarianceEstimator>
            BanditType.POISSON -> CombinatorialBandit(instances, UCB1Tuned()) as CombinatorialBandit<VarianceEstimator>
        }
    }
}
