package combo.bandit.glm

import combo.bandit.BanditTest
import combo.bandit.TestParameters
import combo.model.Model

class DiagonalCovarianceLinearBanditTest : BanditTest<LinearBandit<*>>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.diagonalCovarianceBuilder(model.problem)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}

class FullCovarianceLinearBanditTest : BanditTest<LinearBandit<*>>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.fullCovarianceBuilder(model.problem)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}
