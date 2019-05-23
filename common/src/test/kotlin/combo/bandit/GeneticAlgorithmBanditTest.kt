package combo.bandit

import combo.bandit.ga.GeneticAlgorithmBandit
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.bandit.univariate.UCB1
import combo.bandit.univariate.UCB1Tuned
import combo.math.VarianceEstimator
import combo.sat.Problem

class GeneticAlgorithmBanditTest : BanditTest<Array<InstanceData<VarianceEstimator>>>() {
    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): GeneticAlgorithmBandit<VarianceEstimator> {
        return when (type) {
            BanditType.BINOMIAL -> GeneticAlgorithmBandit(problem, UCB1()) as GeneticAlgorithmBandit<VarianceEstimator>
            BanditType.NORMAL -> GeneticAlgorithmBandit(problem, ThompsonSampling(NormalPosterior))
            BanditType.POISSON -> GeneticAlgorithmBandit(problem, UCB1Tuned()) as GeneticAlgorithmBandit<VarianceEstimator>
        }
    }
}
