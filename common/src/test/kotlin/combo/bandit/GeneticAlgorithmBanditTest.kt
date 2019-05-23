package combo.bandit

import combo.bandit.ga.GeneticAlgorithmBandit
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.bandit.univariate.UCB1
import combo.bandit.univariate.UCB1Tuned
import combo.sat.Problem

class GeneticAlgorithmBanditTest : BanditTest<GeneticAlgorithmBandit<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): GeneticAlgorithmBandit<*> {
        return when (type) {
            BanditType.BINOMIAL -> GeneticAlgorithmBandit(problem, UCB1())
            BanditType.NORMAL -> GeneticAlgorithmBandit(problem, ThompsonSampling(NormalPosterior))
            BanditType.POISSON -> GeneticAlgorithmBandit(problem, UCB1Tuned())
        }
    }
}
