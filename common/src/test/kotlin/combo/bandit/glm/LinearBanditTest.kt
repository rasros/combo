package combo.bandit.glm

import combo.bandit.BanditTest
import combo.bandit.TestParameters
import combo.model.Model

class GreedyLinearBanditTest : BanditTest<LinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.Builder(model.problem)
                    .linearModel(GreedyLinearModel.Builder(model.problem)
                            .link(parameters.variance().canonicalLink()).build())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}

class PrecisionLinearBanditTest : BanditTest<LinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.Builder(model.problem)
                    .linearModel(PrecisionLinearModel.Builder(model.problem)
                            .family(parameters.variance()).build())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}

class CovarianceLinearBanditTest : BanditTest<LinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.Builder(model.problem)
                    .linearModel(CovarianceLinearModel.Builder(model.problem)
                            .family(parameters.variance()).build())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}
