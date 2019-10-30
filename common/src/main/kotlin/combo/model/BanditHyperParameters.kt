package combo.model

import combo.bandit.BanditBuilder
import combo.bandit.BanditData
import combo.bandit.dt.*
import combo.bandit.glm.*
import combo.bandit.nn.NeuralLinearBandit
import combo.bandit.nn.NeuralLinearData
import combo.math.BinaryEstimator
import combo.model.Model.Companion.model

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
        if (baseBuilder.banditPolicy.prior is BinaryEstimator) nominal("splitMetric", EntropyReduction, ChiSquareTest, GiniCoefficient)
        nominal("delta", 0.001f, 0.01f, 0.05f, 0.1f, 0.2f, 0.3f)
        nominal("deltaDecay", 0.5f, 0.9f, 0.99f, 1f)
        nominal("tau", 0.01f, 0.05f, 0.1f, 0.2f, 0.3f)
    }

    override fun toBandit(metaAssignment: Assignment) =
            baseBuilder.splitMetric(metaAssignment["splitMetric"] ?: VarianceReduction)
                    .delta(metaAssignment.getFloat("delta"))
                    .deltaDecay(metaAssignment.getFloat("deltaDecay"))
                    .tau(metaAssignment.getFloat("tau"))
}

class RandomForestHyperParameters(override val baseBuilder: RandomForestBandit.Builder) : BanditHyperParameters<ForestData> {

    override val metaModel = model {
        if (baseBuilder.banditPolicy.prior is BinaryEstimator) nominal("splitMetric", EntropyReduction, ChiSquareTest, GiniCoefficient)
        nominal("delta", 0.001f, 0.01f, 0.05f, 0.1f, 0.2f, 0.3f)
        nominal("deltaDecay", 0.5f, 0.9f, 0.99f, 1f)
        nominal("tau", 0.01f, 0.05f, 0.1f, 0.2f, 0.3f)
        val vs = nominal("voteStrategy",
                { a: Assignment -> SumVotes(1f, a.getFloat("exploration"), a.get<(Assignment) -> LearningRateSchedule>("voteNoiseDecay1")!!.invoke(a)) },
                { a: Assignment -> BetaRandomizedVotes(1f, a.get<(Assignment) -> LearningRateSchedule>("voteNoiseDecay2")!!.invoke(a)) })
        model(vs.values[0]) {
            nominal("exploration", 0f, 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f)
            nominal("learningRate1", 0.1f, 0.5f, 1f)
            nominal("k1", 0, 01f, 0.1f, 0.5f, 1f)
            nominal("voteNoiseDecay1",
                    { a: Assignment -> ConstantRate(a.getFloat("learningRate1")) },
                    { a: Assignment -> StepDecay(a.getFloat("learningRate1"), a.getFloat("k1")) },
                    { a: Assignment -> ExponentialDecay(a.getFloat("learningRate1"), a.getFloat("k1")) })
        }
        model(vs.values[1]) {
            nominal("learningRate2", 0.1f, 0.5f, 1f)
            nominal("k2", 0, 01f, 0.1f, 0.5f, 1f)
            nominal("voteNoiseDecay2",
                    { a: Assignment -> ConstantRate(a.getFloat("learningRate2")) },
                    { a: Assignment -> StepDecay(a.getFloat("learningRate2"), a.getFloat("k2")) },
                    { a: Assignment -> ExponentialDecay(a.getFloat("learningRate2"), a.getFloat("k2")) })
        }
    }

    override fun toBandit(metaAssignment: Assignment) =
            baseBuilder.splitMetric(metaAssignment["splitMetric"] ?: VarianceReduction)
                    .delta(metaAssignment.getFloat("delta"))
                    .deltaDecay(metaAssignment.getFloat("deltaDecay"))
                    .tau(metaAssignment.getFloat("tau"))
                    .voteStrategy(SumVotes(1f, 0f))
                    .voteStrategy(metaAssignment.get<(Assignment) -> VoteStrategy>("voteStrategy")!!.invoke(metaAssignment))
}

class GreedyLinearBanditHyperParameters(override val baseBuilder: LinearBandit.GreedyBuilder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        nominal("exploration", 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f)
        nominal("regularizationFactor", 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f)
        val algo = nominal("algorithm",
                { a: Assignment ->
                    SGD(ConstantRate(a.subAssignment("sgd").getFloat("eta")))
                },
                { a: Assignment ->
                    SGD(StepDecay(a.subAssignment("sgd").getFloat("eta"),
                            a.subAssignment("sgd").getFloat("k")))
                },
                { a: Assignment ->
                    SGD(ExponentialDecay(a.subAssignment("sgd").getFloat("eta"),
                            a.subAssignment("sgd").getFloat("k")))
                },
                { a: Assignment ->
                    AdaGrad(baseBuilder.problem.nbrValues,
                            a.subAssignment("adaGrad").getFloat("eta"))
                },
                { a: Assignment ->
                    RMSProp(baseBuilder.problem.nbrValues,
                            a.subAssignment("rmsProp").getFloat("eta"),
                            a.subAssignment("rmsProp").getFloat("beta"))
                },
                { a: Assignment ->
                    Adam(baseBuilder.problem.nbrValues,
                            a.subAssignment("adam").getFloat("eta"),
                            a.subAssignment("adam").getFloat("beta1"),
                            a.subAssignment("adam").getFloat("beta2"))
                })
        val sgd = model("sgd") {
            nominal("eta", 1e-3f, 1e-2f, 1e-1f)
            val k = optionalNominal("k", 1e-3f, 1e-2f, 1e-1f, 1f)
            impose { k reifiedEquivalent disjunction(algo.values[1], algo.values[2]) }
        }
        impose { sgd reifiedEquivalent disjunction(algo.values[0], algo.values[1], algo.values[2]) }
        val adaGrad = model(algo.values[3], "adaGrad") {
            nominal("eta", 1e-3f, 1e-2f, 1e-1f)
        }
        val rmsProp = model(algo.values[4], "rmsProp") {
            nominal("eta", 1e-3f, 1e-2f, 1e-1f)
            nominal("beta", 0.5f, 0.9f, 0.99f)
        }
        val adam = model(algo.values[5], "adam") {
            nominal("eta", 1e-3f, 1e-2f, 1e-1f)
            nominal("beta1", 0.5, 0.9f, 0.99f)
            nominal("beta2", 0.99f, 0.999f, 0.9999f)
        }
        impose { excludes(sgd, adaGrad, rmsProp, adam) }
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .updater(metaAssignment.get<(Assignment) -> SGDAlgorithm>("algorithm")!!.invoke(metaAssignment))
            .exploration(metaAssignment.getFloat("exploration"))
            .regularizationFactor(metaAssignment.getFloat("regularizationFactor"))
}

class PrecisionLinearBanditHyperParameters(override val baseBuilder: LinearBandit.PrecisionBuilder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        nominal("precision", 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f, 1e1f, 1e2f, 1e3f, 1e4f)
        nominal("exploration", 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f)
        nominal("regularizationFactor", 0f, 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f)
        val algo = nominal("learningRate",
                { a: Assignment -> ConstantRate(a.getFloat("eta")) },
                { a: Assignment -> StepDecay(a.getFloat("eta"), a.getFloat("k")) },
                { a: Assignment -> ExponentialDecay(a.getFloat("eta"), a.getFloat("k")) })
        nominal("eta", 1e-3f, 1e-2f, 1e-1f, 1f)
        val k = optionalNominal("k", 1e-3f, 1e-2f, 1e-1f, 1f)
        impose { k reifiedEquivalent disjunction(algo.values[1], algo.values[2]) }
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .priorPrecision(metaAssignment.getFloat("precision"))
            .learningRate(metaAssignment.get<(Assignment) -> LearningRateSchedule>("learningRate")!!.invoke(metaAssignment))
            .exploration(metaAssignment.getFloat("exploration"))
            .regularizationFactor(metaAssignment.getFloat("regularizationFactor"))
}

class CovarianceLinearBanditHyperParameters(override val baseBuilder: LinearBandit.CovarianceBuilder) : BanditHyperParameters<LinearData> {

    override val metaModel = model {
        nominal("variance", 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f, 1e1f, 1e2f, 1e3f, 1e4f)
        nominal("exploration", 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f, 1f)
        nominal("regularizationFactor", 0f, 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f)
        val algo = nominal("learningRate",
                { a: Assignment -> ConstantRate(a.getFloat("eta")) },
                { a: Assignment -> StepDecay(a.getFloat("eta"), a.getFloat("k")) },
                { a: Assignment -> ExponentialDecay(a.getFloat("eta"), a.getFloat("k")) })
        nominal("eta", 1e-3f, 1e-2f, 1e-1f)
        val k = optionalNominal("k", 1e-3f, 1e-2f, 1e-1f, 1f)
        impose { k reifiedEquivalent disjunction(algo.values[1], algo.values[2]) }
    }

    override fun toBandit(metaAssignment: Assignment) = baseBuilder
            .priorVariance(metaAssignment.getFloat("variance"))
            .learningRate(metaAssignment.get<(Assignment) -> LearningRateSchedule>("learningRate")!!.invoke(metaAssignment))
            .exploration(metaAssignment.getFloat("exploration"))
            .regularizationFactor(metaAssignment.getFloat("regularizationFactor"))
}

class NeuralLinearBanditHyperParameters(override val baseBuilder: NeuralLinearBandit.Builder) : BanditHyperParameters<NeuralLinearData> {
    override val metaModel = model {
        nominal("regularizationFactor", 0f, 1e-6f, 1e-5f, 1e-4f, 1e-3f, 1e-2f, 1e-1f)
    }

    override fun toBandit(metaAssignment: Assignment): BanditBuilder<NeuralLinearData> {
        baseBuilder.networkBuilder.regularizationFactor(metaAssignment.getFloat("regularizationFactor"))
        return baseBuilder
    }
}
