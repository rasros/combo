package combo.bandit.nn

import combo.bandit.BanditTest
import combo.bandit.TestParameters
import combo.bandit.glm.ConstantRate
import combo.bandit.glm.CovarianceLinearModel
import combo.bandit.glm.HuberLoss
import combo.model.Model
import org.junit.Ignore

@Ignore
class DL4jNeuralLinearBanditTest : BanditTest<NeuralLinearBandit>() {
    override fun bandit(model: Model, parameters: TestParameters): NeuralLinearBandit {
        return NeuralLinearBandit.Builder(
                DL4jNetwork.Builder(model.problem)
                        .output(parameters.variance().canonicalLink())
                        .hiddenLayerWidth(20)
                        .hiddenLayers(2)
                        .initWeightVariance(0.5f)
                        .randomSeed(parameters.randomSeed))
                .rewards(parameters.rewards)
                .baseVariance(0.01f)
                .batchSize(100)
                .varianceUpdateDecay(0f)
                .weightUpdateDecay(0f)
                .linearModel(CovarianceLinearModel.Builder(20)
                        .family(parameters.variance())
                        .priorVariance(0.1f)
                        .learningRate(ConstantRate(0.1f))
                        .build())
                .build()
    }
}