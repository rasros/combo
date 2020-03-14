package combo.bandit.nn

import combo.bandit.BanditTest
import combo.bandit.TestParameters
import combo.bandit.glm.CovarianceLinearModel
import combo.model.Model

class DL4jNeuralLinearBanditTest : BanditTest<NeuralLinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters): NeuralLinearBandit {
        return NeuralLinearBandit.Builder(
                DL4jNetwork.Builder(model.problem)
                        .output(parameters.variance().canonicalLink())
                        .hiddenLayerWidth(10)
                        .hiddenLayers(1)
                        .learningRate(0.01f)
                        .regularizationFactor(0.1f)
                        .initWeightVariance(0.005f)
                        .randomSeed(parameters.randomSeed))
                .rewards(parameters.rewards)
                .epochs(3)
                .batchSize(2)
                .maximize(parameters.maximize)
                .linearModel(CovarianceLinearModel.Builder(10)
                        .family(parameters.variance())
                        .priorVariance(1f)
                        .exploration(1f)
                        .build())
                .build()
    }
}