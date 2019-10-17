package combo.bandit.glm

import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.math.DataSample
import combo.math.Transform
import combo.math.VoidSample
import combo.math.vectors
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.LinearObjective
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos
import kotlin.jvm.JvmStatic
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Generalized linear model (GLM) bandit with Thompson sampling. The most important configuration parameter is [family],
 * which should be set according to the type of rewards the bandit receives.
 *
 * @param problem The problem contains the [combo.sat.Constraint]s and the number of variables.
 * @param family Type of response data. This is an important parameter to set.
 * @param model Initial weight data to start with.
 * @param randomSeed Set the random seed to a specific value to have a reproducible algorithm.
 * @param maximize Whether the bandit should maximize or minimize the total rewards. By default true.
 * @param link Specify GLM link function, otherwise [family]'s [VarianceFunction.canonicalLink] is used.
 * @param optimizer Which optimizer to use for maximization of the linear function.
 * @param learningRate Slows learning rate. In theory not needed due but will help if other parameters are slightly miss-specified.
 * @param learningRateGrowth Increases learning rate until it reaches 1.
 * @param batchThreshold Use mini-batch mode if the number of updates in [trainAll] is larger than this.
 * @param rewards All rewards are added to this for inspecting how well the bandit performs.
 * @param trainAbsError The total absolute error obtained on a prediction before update.
 * @param testAbsError The total absolute error obtained on a prediction after update.
 */
class LinearBandit<L : LinearData>(val problem: Problem,
                                   val family: VarianceFunction,
                                   model: L,
                                   override val randomSeed: Int = nanos().toInt(),
                                   override val maximize: Boolean = true,
                                   val link: Transform = family.canonicalLink(),
                                   val optimizer: Optimizer<LinearObjective> = LocalSearch.Builder(problem).randomSeed(randomSeed).build(),
                                   val learningRate: Float = 0.1f,
                                   val learningRateGrowth: Float = 1.05f,
                                   val batchThreshold: Int = 3,
                                   override val rewards: DataSample = VoidSample,
                                   override val trainAbsError: DataSample = VoidSample,
                                   override val testAbsError: DataSample = VoidSample)
    : PredictionBandit<L> {

    private val randomSequence = RandomSequence(randomSeed)
    private val model: LinearModel<L> =
            when (model) {
                is DiagonalCovarianceData ->
                    DiagonalModel(family, link, vectors.vector(model.weights), vectors.vector(model.precision),
                            model.bias, model.biasPrecision, learningRate)
                is FullCovarianceData ->
                    FullModel(family, link, vectors.vector(model.weights), vectors.matrix(model.covariance),
                            vectors.matrix(model.covarianceL), model.bias, model.biasPrecision, learningRate)
                else -> error("sealed class")
            }.let {
                @Suppress("UNCHECKED_CAST")
                it as LinearModel<L>
            }

    init {
        require(model.weights.size == problem.nbrValues) { "Weight prior parameter must be same size as problem: ${problem.nbrValues}." }
    }

    override fun predict(instance: Instance) = link.apply(model.bias + (instance dot model.weights))

    override fun train(instance: Instance, result: Float, weight: Float) {
        model.train(instance, result, weight)
        model.learningRate = min(1f, learningRate * learningRateGrowth)
    }

    override fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
        if (instances.size < batchThreshold) super.trainAll(instances, results, weights)
        else model.trainAll(instances, results, weights)
        model.learningRate = min(1f, learningRate * learningRateGrowth.pow(instances.size))
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val rng = randomSequence.next()
        val sampled = model.sample(rng)
        return optimizer.optimizeOrThrow(LinearObjective(maximize, sampled), assumptions)
    }

    override fun importData(data: L) = model.importData(data)
    override fun exportData() = model.exportData()

    companion object {
        /** Build linear model with full covariance. Size of covariance is quadratic in [Problem.nbrValues]. */
        @JvmStatic
        fun fullCovarianceBuilder(problem: Problem) = FullCovarianceBuilder(problem)

        /** Build linear modle with diagonalized covariance. Size of variance is linear in [Problem.nbrValues]. */
        @JvmStatic
        fun diagonalCovarianceBuilder(problem: Problem) = DiagonalCovarianceBuilder(problem)
    }

    abstract class Builder<L : LinearData>(val problem: Problem) : PredictionBanditBuilder<L> {
        protected var regularizationFactor: Float = 1.0f
        protected var learningRate: Float = 0.1f
        protected var learningRateGrowth: Float = 1.01f
        protected var family: VarianceFunction = NormalVariance
        protected var randomSeed: Int = nanos().toInt()
        protected var rewards: DataSample = VoidSample
        protected var trainAbsError: DataSample = VoidSample
        protected var testAbsError: DataSample = VoidSample
        protected var maximize: Boolean = true
        protected var link: Transform? = null
        protected var optimizer: Optimizer<LinearObjective>? = null
        protected var batchThreshold: Int = 3
        protected var model: L? = null
        protected var bias: Float? = null

        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }
        override fun parallel() = ParallelPredictionBandit.Builder(this)

        /** Initial bias term. This has no effect if importing data. */
        fun bias(bias: Float) = apply { this.bias = bias }

        /** Used in initialization of the covariance matrix. This has no effect if importing data. */
        fun regularizationFactor(regularizationFactor: Float) = apply { this.regularizationFactor = regularizationFactor }

        /** Slows learning rate. In theory not needed due but will help if other parameters are slightly miss-specified. */
        fun learningRate(learningRate: Float) = apply { this.learningRate = learningRate }

        /** Increases learning rate until it reaches 1. */
        fun learningRateGrowth(learningRateGrowth: Float) = apply { this.learningRateGrowth = learningRateGrowth }

        /**
         * Type of response data. This is an important parameter to set.
         * For binary data use [BinomialVariance], for normal data use [NormalVariance], etc.
         */
        fun family(family: VarianceFunction) = apply { this.family = family }

        /** Specify GLM link function, otherwise [family]'s [VarianceFunction.canonicalLink] is used. */
        fun link(link: Transform) = apply { this.link = link }

        /** Use mini-batch mode if the number of updates in [trainAll] is larger than this. */
        fun batchThreshold(batchThreshold: Int) = apply { this.batchThreshold = batchThreshold }

        /** Which optimizer to use for maximization of the linear function. */
        fun optimizer(optimizer: Optimizer<LinearObjective>) = apply { this.optimizer = optimizer }

        abstract override fun build(): LinearBandit<L>
    }

    class DiagonalCovarianceBuilder(problem: Problem) : Builder<DiagonalCovarianceData>(problem) {
        override fun importData(data: DiagonalCovarianceData) = apply { this.model = data }
        override fun build(): LinearBandit<DiagonalCovarianceData> {
            val n = problem.nbrValues
            val optimizer = optimizer ?: LocalSearch.Builder(problem).randomSeed(randomSeed).build()
            val bias = bias ?: if (family is PoissonVariance) 1f else 0f
            val model = model ?: DiagonalCovarianceData(
                    FloatArray(n), FloatArray(n) { regularizationFactor }, bias, regularizationFactor)
            return LinearBandit(problem, family, model, randomSeed, maximize, link ?: family.canonicalLink(), optimizer,
                    learningRate, learningRateGrowth, batchThreshold, rewards, trainAbsError, testAbsError)
        }
    }

    class FullCovarianceBuilder(problem: Problem) : Builder<FullCovarianceData>(problem) {
        override fun importData(data: FullCovarianceData) = apply { this.model = data }
        override fun build(): LinearBandit<FullCovarianceData> {
            val n = problem.nbrValues
            val bias = bias ?: if (family is PoissonVariance) 1f else 0f
            val optimizer = optimizer ?: LocalSearch.Builder(problem).randomSeed(randomSeed).build()
            val model = model ?: FullCovarianceData(
                    FloatArray(n), Array(n) { FloatArray(n) }, Array(n) { FloatArray(n) }, bias, 0.1f * regularizationFactor).apply {
                for (i in 0 until n) {
                    covariance[i][i] = regularizationFactor
                    covarianceL[i][i] = sqrt(regularizationFactor)
                }
            }
            return LinearBandit(problem, family, model, randomSeed, maximize, link ?: family.canonicalLink(), optimizer,
                    learningRate, learningRateGrowth, batchThreshold, rewards, trainAbsError, testAbsError)
        }
    }
}