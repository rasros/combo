package combo.model

import combo.bandit.BanditBuilder
import combo.bandit.BanditData
import combo.bandit.dt.*
import combo.bandit.glm.LinearBandit
import combo.bandit.glm.LinearData
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
        int("delta", -300, 0) // 0..1
        int("deltaDecay", -100, 10) // 0..10
        int("tau", -100, 0) // 0..1
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .delta(10f.pow(metaAssignment.getInt("delta") / 10f))
            .deltaDecay(10f.pow(metaAssignment.getInt("deltaDecay") / 10f))
            .tau(10f.pow(metaAssignment.getInt("tau") / 10f))
}

class RandomForestHyperParameters(override val baseBuilder: RandomForestBandit.Builder) : BanditHyperParameters<ForestData> {

    override val metaModel = model {
        if (baseBuilder.banditPolicy.prior is BinaryEstimator) nominal("splitMetric", EntropyReduction, ChiSquareTest, GiniCoefficient)
        int("delta", -300, 0) // 0..1
        int("deltaDecay", -100, 10) // 0..10
        int("tau", -100, 0) // 0..1
        int("samplingMean", -15, 15)
        int("nbrVariables", -15, 0)
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .delta(10f.pow(metaAssignment.getFloat("delta") / 10f))
            .deltaDecay(10f.pow(metaAssignment.getFloat("deltaDecay") / 10f))
            .tau(10f.pow(metaAssignment.getFloat("tau") / 10f))
            .instanceSamplingMean(10f.pow(metaAssignment.getFloat("samplingMean") / 10f))
            .viewedVariables(max(1, (10f.pow(metaAssignment.getFloat("nbrVariables") / 10f) * baseBuilder.model.nbrVariables).toInt()))
}

class PrecisionLinearBanditHyperParameters(override val baseBuilder: LinearBandit.PrecisionBuilder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        int("precision", 0, 40) // 1..1e4
        int("exploration", -60, 0) // 1e-6..1
        int("regularizationFactor", -460, -10) // 0..1e-1f
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .priorPrecision(10f.pow(metaAssignment.getInt("precision") / 10f))
            .exploration(10f.pow(metaAssignment.getInt("exploration") / 10f))
            .regularizationFactor(10f.pow(metaAssignment.getInt("regularizationFactor") / 10f))
}

class CovarianceLinearBanditHyperParameters(override val baseBuilder: LinearBandit.CovarianceBuilder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        int("variance", -40, 0) // 1e-4..1
        int("exploration", -60, 0) // 1e-6..1
        int("regularizationFactor", -460, -10) // 0..1e-1f
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .priorVariance(10f.pow(metaAssignment.getInt("variance") / 10f))
            .exploration(10f.pow(metaAssignment.getInt("exploration") / 10f))
            .regularizationFactor(10f.pow(metaAssignment.getInt("regularizationFactor") / 10f))
}

class NeuralLinearBanditHyperParameters(override val baseBuilder: NeuralLinearBandit.Builder) : BanditHyperParameters<NeuralLinearData> {
    override val metaModel = model {
        nominal("regularizationFactor", 0f, 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f)
        nominal("baseVariance", 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f, 1e1f, 1e2f, 1e3f, 1e4f)
        nominal("varianceUpdateDecay", 0f, 1 - 1e-4f, 1 - 1e-3f, 1 - 1e-2f, 1 - 1e-1f)
        nominal("weightUpdateDecay", 0f, 1 - 1e-4f, 1 - 1e-3f, 1 - 1e-2f, 1 - 1e-1f)
    }

    override fun toBandit(metaAssignment: Assignment): BanditBuilder<NeuralLinearData> {
        baseBuilder.networkBuilder.regularizationFactor(metaAssignment.getFloat("regularizationFactor"))
        return baseBuilder.baseVariance(metaAssignment.getFloat("baseVariance"))
                .varianceUpdateDecay(metaAssignment.getFloat("varianceUpdateDecay"))
                .weightUpdateDecay(metaAssignment.getFloat("weightUpdateDecay"))
    }
}
