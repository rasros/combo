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
            LinearBandit.Builder(model.problem)
                    .linearModel(GreedyLinearModel.Builder(model.problem)
                            .link(parameters.variance().canonicalLink()).build())
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
            LinearBandit.Builder(model.problem)
                    .linearModel(DiagonalizedLinearModel.Builder(model.problem)
                            .family(parameters.variance()).build())
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
            LinearBandit.Builder(model.problem)
                    .linearModel(CovarianceLinearModel.Builder(model.problem)
                            .family(parameters.variance()).build())
                    .randomSeed(parameters.randomSeed)
                    .maximize(parameters.maximize)
                    .rewards(parameters.rewards)
                    .build()
}
