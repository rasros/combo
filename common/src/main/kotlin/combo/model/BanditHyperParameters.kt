package combo.model

import combo.bandit.BanditBuilder
import combo.bandit.BanditData
import combo.bandit.dt.*
import combo.bandit.glm.CovarianceLinearModel
import combo.bandit.glm.LinearBandit
import combo.bandit.glm.LinearData
import combo.bandit.glm.PrecisionLinearModel
import combo.bandit.nn.NeuralLinearBandit
import combo.bandit.nn.NeuralLinearData
import combo.math.BinaryEstimator
import combo.model.Model.Companion.model
import kotlin.math.max
import kotlin.math.pow

/**
 * Used to tune hyper-parameters defined in [metaModel]. The parameters in the meta model will override what is given
 * in the [baseBuilder], and the others are left as is.
 */
interface BanditHyperParameters<D : BanditData> {
    val metaModel: Model
    val baseBuilder: BanditBuilder<D>
    fun toBandit(metaAssignment: Assignment): BanditBuilder<D>
}

class DecisionTreeHyperParameters(override val baseBuilder: DecisionTreeBandit.Builder) : BanditHyperParameters<TreeData> {

    override val metaModel = model {
        int("delta", -256, 0)
        int("deltaDecay", -128, 127)
        int("tau", -32, 0)
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .delta(10f.pow(metaAssignment.getInt("delta") / 10f))
            .deltaDecay(10f.pow(metaAssignment.getInt("deltaDecay") / 10f))
            .tau(10f.pow(metaAssignment.getInt("tau") / 10f))
}

class RandomForestHyperParameters(override val baseBuilder: RandomForestBandit.Builder) : BanditHyperParameters<ForestData> {

    override val metaModel = model {
        if (baseBuilder.banditPolicy.prior is BinaryEstimator) nominal("splitMetric", EntropyReduction, ChiSquareTest, GiniCoefficient)
        int("delta", -256, 0)
        int("deltaDecay", -128, 127)
        int("tau", -32, 0)
        int("samplingMean", -8, 7)
        int("nbrVariables", -16, 0)
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .delta(10f.pow(metaAssignment.getFloat("delta") / 10f))
            .deltaDecay(10f.pow(metaAssignment.getFloat("deltaDecay") / 10f))
            .tau(10f.pow(metaAssignment.getFloat("tau") / 10f))
            .instanceSamplingMean(10f.pow(metaAssignment.getFloat("samplingMean") / 10f))
            .viewedVariables(max(1, (10f.pow(metaAssignment.getFloat("nbrVariables") / 10f) * baseBuilder.model.nbrVariables).toInt()))
}

class PrecisionLinearBanditHyperParameters(override val baseBuilder: LinearBandit.Builder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        int("precision", 0, 63)
        int("exploration", -64, 0)
        int("regularizationFactor", -256, -10)
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .linearModel(PrecisionLinearModel.Builder(baseBuilder.problem)
                    .family((baseBuilder.linearModel as PrecisionLinearModel).family)
                    .priorPrecision(10f.pow(metaAssignment.getInt("precision") / 10f))
                    .exploration(10f.pow(metaAssignment.getInt("exploration") / 10f))
                    .regularizationFactor(10f.pow(metaAssignment.getInt("regularizationFactor") / 10f)).build())
}

class CovarianceLinearBanditHyperParameters(override val baseBuilder: LinearBandit.Builder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        int("variance", -64, 0)
        int("exploration", -64, 0)
        int("regularizationFactor", -256, -10)
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .linearModel(CovarianceLinearModel.Builder(baseBuilder.problem)
                    .family((baseBuilder.linearModel as CovarianceLinearModel).family)
                    .priorVariance(10f.pow(metaAssignment.getInt("variance") / 10f))
                    .exploration(10f.pow(metaAssignment.getInt("exploration") / 10f))
                    .regularizationFactor(10f.pow(metaAssignment.getInt("regularizationFactor") / 10f)).build())
}

class NeuralLinearBanditHyperParameters(override val baseBuilder: NeuralLinearBandit.Builder) : BanditHyperParameters<NeuralLinearData> {
    override val metaModel = model {
        int("regularizationFactor", -256, -10)
        int("baseVariance", -64, 63)
        int("varianceUpdateDecay", -32, 0)
        int("weightUpdateDecay", -32, 0)
    }

    override fun toBandit(metaAssignment: Assignment): BanditBuilder<NeuralLinearData> {
        baseBuilder.networkBuilder.regularizationFactor(10f.pow(metaAssignment.getInt("regularizationFactor") / 10f))
        return baseBuilder.baseVariance(10f.pow(metaAssignment.getInt("baseVariance") / 10f))
                .varianceUpdateDecay(10f.pow(metaAssignment.getFloat("varianceUpdateDecay") / 10f))
                .weightUpdateDecay(10f.pow(metaAssignment.getFloat("weightUpdateDecay") / 10f))
    }
}
