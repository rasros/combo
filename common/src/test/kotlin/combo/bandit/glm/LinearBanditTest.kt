package combo.bandit.glm

import combo.bandit.BanditTest
import combo.bandit.TestParameters
import combo.math.vectors
import combo.model.Model

class GreedyLinearBanditTest : BanditTest<LinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.greedyBuilder(model.problem)
                    .exploration(1f)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()

}

class PrecisionLinearBanditTest : BanditTest<LinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.precisionBuilder(model.problem)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}

class CovarianceLinearBanditTest : BanditTest<LinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.covarianceBuilder(model.problem)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}
