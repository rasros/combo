package combo.bandit.glm

import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.math.DataSample
import combo.math.Transform
import combo.math.VoidSample
import combo.math.vectors
import combo.sat.BitArray
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.LinearObjective
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos
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
 * @param learningRate What step size to take at each step.
 * @param rewards All rewards are added to this for inspecting how well the bandit performs.
 * @param trainAbsError The total absolute error obtained on a prediction before update.
 * @param testAbsError The total absolute error obtained on a prediction after update.
 */
class LinearBandit(val problem: Problem,
                   val model: LinearModel,
                   override val randomSeed: Int = nanos().toInt(),
                   override val maximize: Boolean = true,
                   val optimizer: Optimizer<LinearObjective> = LocalSearch.Builder(problem).randomSeed(randomSeed).build(),
                   override val rewards: DataSample = VoidSample,
                   override val trainAbsError: DataSample = VoidSample,
                   override val testAbsError: DataSample = VoidSample)
    : PredictionBandit<LinearData> {

    private val randomSequence = RandomSequence(randomSeed)

    init {
        require(model.weights.size == problem.nbrValues) {
            "Weight prior parameter must be same size as problem: ${problem.nbrValues}."
        }
    }

    override fun predict(instance: Instance) = model.predict(instance)

    override fun train(instance: Instance, result: Float, weight: Float) {
        model.train(instance, result, weight)
    }

    override fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
        model.trainAll(instances, results, weights)
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        if (problem.nbrValues == 0) return BitArray(0)
        val rng = randomSequence.next()
        val sampled = model.sample(rng)
        return optimizer.optimizeOrThrow(LinearObjective(maximize, sampled), assumptions)
    }

    override fun optimalOrThrow(assumptions: IntCollection): Instance {
        return optimizer.optimizeOrThrow(LinearObjective(maximize, model.weights), assumptions)
    }

    override fun importData(data: LinearData) {
        val ratio = if (data.step <= 0L) 1f
        else data.step.toFloat() / (model.step + data.step).toFloat()
        model.importData(data, ratio, ratio)
    }

    override fun exportData() = model.exportData()

    companion object {
        fun greedyBuilder(problem: Problem) = GreedyBuilder(problem)
        fun precisionBuilder(problem: Problem) = PrecisionBuilder(problem)
        fun covarianceBuilder(problem: Problem) = CovarianceBuilder(problem)
    }

    abstract class Builder(val problem: Problem) : PredictionBanditBuilder<LinearData> {

        protected var family: VarianceFunction = NormalVariance
        protected var randomSeed: Int = nanos().toInt()
        protected var rewards: DataSample = VoidSample
        protected var maximize: Boolean = true
        protected var link: Transform? = null
        protected var loss: Transform = MSELoss
        protected var exploration: Float = 1f
        protected var regularization: Transform = MSELoss
        protected var regularizationFactor: Float = 1e-5f
        protected var optimizer: Optimizer<LinearObjective>? = null
        protected var bias: Float? = null
        protected var trainAbsError: DataSample = VoidSample
        protected var testAbsError: DataSample = VoidSample
        protected var startingStep: Long = 0L

        protected var data: LinearData? = null

        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }
        override fun parallel() = ParallelPredictionBandit.Builder(this)
        override fun importData(data: LinearData) = apply { this.data = data }

        /** Initial bias term. This has no effect if importing data. */
        fun bias(bias: Float) = apply { this.bias = bias }

        /** Loss function of the regression, by default it is mean squared loss. */
        fun loss(loss: Transform) = apply { this.loss = loss }

        /** Type of regularization to apply, by default L2. */
        fun regularization(regularization: Transform) = apply { this.regularization = regularization }

        /** Regularization constant. */
        fun regularizationFactor(regularizationFactor: Float) = apply { this.regularizationFactor = regularizationFactor }

        /** Noise added (or multiplied) to weights during [choose]. */
        fun exploration(exploration: Float) = apply { this.exploration = exploration }

        /** Starting step counter. */
        fun startingStep(step: Long) = apply { this.startingStep = step }

        /** Type of response data. For binary data use [BinomialVariance], for normal data use [NormalVariance], etc. */
        fun family(family: VarianceFunction) = apply { this.family = family }

        /** Specify GLM link function, otherwise [family]'s [VarianceFunction.canonicalLink] is used. */
        fun link(link: Transform) = apply { this.link = link }

        /** Which optimizer to use for maximization of the linear function. */
        fun optimizer(optimizer: Optimizer<LinearObjective>) = apply { this.optimizer = optimizer }

        protected fun defaultLink() = link ?: family.canonicalLink()
        protected fun defaultOptimizer() = optimizer ?: LocalSearch.Builder(problem)
                .randomSeed(randomSeed).fallbackCached().build()

        protected fun defaultBias() = bias ?: if (family is PoissonVariance) 1.001f else 0f

        abstract override fun build(): LinearBandit
    }

    class GreedyBuilder(problem: Problem) : Builder(problem) {
        init {
            exploration = .1f
        }

        private var biasRate: LearningRateSchedule = ExponentialDecay()
        private var updater: SGDAlgorithm = SGD(ExponentialDecay())

        fun biasRate(biasRate: LearningRateSchedule) = apply { this.biasRate = biasRate }

        /** Stochastic gradient descent algorithm to use. */
        fun updater(updater: SGDAlgorithm) = apply { this.updater = updater }

        override fun build(): LinearBandit {
            val lm = GreedyLinearModel(defaultLink(), loss, regularization, regularizationFactor, updater, exploration,
                    startingStep, vectors.zeroVector(problem.nbrValues), biasRate, defaultBias())
            return LinearBandit(problem, lm, randomSeed, maximize, defaultOptimizer(), rewards, trainAbsError, testAbsError)
        }
    }

    class PrecisionBuilder(problem: Problem) : Builder(problem) {
        private var learningRate: LearningRateSchedule = ExponentialDecay()
        private var priorPrecision: Float = 1f

        fun priorPrecision(priorPrecision: Float) = apply { this.priorPrecision = priorPrecision }

        fun learningRate(learningRate: LearningRateSchedule) = apply { this.learningRate = learningRate }

        override fun build(): LinearBandit {
            val lm = PrecisionLinearModel(family, defaultLink(), loss, regularization, regularizationFactor, learningRate,
                    exploration, startingStep, vectors.zeroVector(problem.nbrValues),
                    vectors.vector(FloatArray(problem.nbrValues) { priorPrecision }), defaultBias(), priorPrecision)
            return LinearBandit(problem, lm, randomSeed, maximize, defaultOptimizer(), rewards, trainAbsError, testAbsError)
        }
    }

    class CovarianceBuilder(problem: Problem) : Builder(problem) {
        private var learningRate: LearningRateSchedule = ExponentialDecay()
        private var priorVariance: Float = 1f

        fun priorVariance(priorVariance: Float) = apply { this.priorVariance = priorVariance }

        fun learningRate(learningRate: LearningRateSchedule) = apply { this.learningRate = learningRate }

        override fun build(): LinearBandit {
            val n = problem.nbrValues
            val Hinv = vectors.zeroMatrix(n, n)
            val L = vectors.zeroMatrix(n, n)
            for (i in 0 until n) {
                Hinv[i, i] = priorVariance
                L[i, i] = sqrt(priorVariance)
            }
            val lm = CovarianceLinearModel(family, defaultLink(), loss, regularization, regularizationFactor, learningRate,
                    exploration, startingStep, vectors.zeroVector(problem.nbrValues), Hinv, L, defaultBias(), 1f / priorVariance)
            return LinearBandit(problem, lm, randomSeed, maximize, defaultOptimizer(), rewards, trainAbsError, testAbsError)
        }
    }
}