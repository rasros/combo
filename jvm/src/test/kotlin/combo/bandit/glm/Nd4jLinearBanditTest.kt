package combo.bandit.glm

import combo.bandit.BanditTest
import combo.bandit.TestParameters
import combo.math.Nd4jVectorFactory
import combo.math.vectors
import combo.model.Model

class Nd4jGreedyLinearBanditTest : BanditTest<LinearBandit>() {
    init {
        vectors = Nd4jVectorFactory
    }

    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.greedyBuilder(model.problem)
                    .exploration(1f)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()

}

class Nd4jPrecisionLinearBanditTest : BanditTest<LinearBandit>() {
    init {
        vectors = Nd4jVectorFactory
    }

    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.precisionBuilder(model.problem)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}

class Nd4jCovarianceLinearBanditTest : BanditTest<LinearBandit>() {
    init {
        vectors = Nd4jVectorFactory
    }

    override fun bandit(model: Model, parameters: TestParameters) =
            LinearBandit.covarianceBuilder(model.problem)
                    .family(parameters.variance())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}
