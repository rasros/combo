package combo.bandit.glm

import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.math.*
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.dot
import combo.sat.optimizers.LinearObjective
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.sat.toFloatArray
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos
import kotlin.jvm.JvmStatic
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

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
                                   learningRate: Float = 0.1f,
                                   val learningRateGrowth: Float = 1.01f,
                                   val batchThreshold: Int = 3,
                                   override val rewards: DataSample = VoidSample,
                                   override val trainAbsError: DataSample = VoidSample,
                                   override val testAbsError: DataSample = VoidSample)
    : PredictionBandit<L> {

    var learningRate: Float = learningRate
        private set
    private val randomSequence = RandomSequence(randomSeed)
    private val model: LinearModel<L> =
            when (model) {
                is DiagonalCovarianceData ->
                    DiagonalModel(model.weights, model.variance, model.bias, model.biasVariance)
                is FullCovarianceData ->
                    FullModel(model.weights, model.covariance, model.cholesky, model.bias, model.biasVariance)
                else -> error("sealed class")
            }.let {
                @Suppress("UNCHECKED_CAST")
                it as LinearModel<L>
            }

    init {
        require(model.weights.size == problem.nbrValues) { "Weight prior parameter must be same size as problem: ${problem.nbrValues}." }
    }

    override fun predict(instance: Instance) = link.apply(model.bias + (instance dot model.weights))
    override fun train(instance: Instance, result: Float, weight: Float) = model.train(instance, result, weight)
    override fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
        if (instances.size < batchThreshold) super.trainAll(instances, results, weights)
        else model.trainAll(instances, results, weights)
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val rng = randomSequence.next()
        val sampled = model.sample(rng)
        return optimizer.optimizeOrThrow(LinearObjective(maximize, sampled), assumptions)
    }

    override fun importData(data: L) = model.importData(data)
    override fun exportData() = model.exportData()

    private abstract inner class LinearModel<L : LinearData>(val weights: Vector, var bias: Float, var biasVariance: Float) {
        abstract fun sample(rng: Random): Vector
        abstract fun train(instance: Instance, result: Float, weight: Float)
        abstract fun importData(data: L)
        abstract fun exportData(): L
        abstract fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?)
    }

    private inner class DiagonalModel(weights: Vector,
                                      val variance: Vector,
                                      bias: Float,
                                      biasVariance: Float)
        : LinearModel<DiagonalCovarianceData>(weights, bias, biasVariance) {

        override fun sample(rng: Random) = Vector(weights.size) { rng.nextNormal(weights[it], sqrt(variance[it])) }

        override fun train(instance: Instance, result: Float, weight: Float) {
            val pred = predict(instance)
            val diff = result - pred
            val varF = family.variance(pred)
            val alpha = learningRate * weight
            for (i in instance) {
                variance[i] /= (1 + varF * alpha)
                weights[i] += variance[i] * diff * alpha
            }
            biasVariance /= 1 + varF * alpha
            bias += biasVariance * diff * alpha
            learningRate = min(1f, learningRate * learningRateGrowth)
        }

        override fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
            val weightsUpdate = Vector(this.weights.size)
            val precisionUpdate = Vector(this.weights.size)
            var biasUpdate = 0.0f
            var biasPrecisionUpdate = 0.0f
            for (i in instances.indices) {
                val instance = instances[i]
                val result = results[i]
                val weight = weights?.get(i) ?: 1.0f
                val pred = predict(instance)
                val diff = result - pred
                val varF = family.variance(pred)
                val itr = instance.iterator()
                val alpha = learningRate * weight
                while (itr.hasNext()) {
                    val j = itr.nextInt()
                    precisionUpdate[j] += varF * alpha
                    weightsUpdate[j] += diff * alpha
                }
                biasPrecisionUpdate += varF * alpha
                biasUpdate += diff * alpha
            }

            bias += biasVariance * biasUpdate / instances.size
            biasVariance /= biasVariance + biasPrecisionUpdate / instances.size
            for (i in weightsUpdate.indices) {
                this.weights[i] += variance[i] * weightsUpdate[i] / instances.size
                variance[i] /= variance[i] + precisionUpdate[i] / instances.size
            }
            learningRate = min(1f, learningRate * learningRateGrowth.pow(instances.size))
        }

        override fun importData(data: DiagonalCovarianceData) {
            bias = data.bias
            biasVariance = data.biasVariance
            for (i in weights.indices) {
                weights[i] = data.weights[i]
                variance[i] = data.variance[i]
            }
        }

        override fun exportData() = DiagonalCovarianceData(weights.copyOf(), variance.copyOf(), bias, biasVariance)
    }

    private inner class FullModel(weights: Vector,
                                  val covariance: Matrix,
                                  val cholesky: Matrix,
                                  bias: Float,
                                  biasVariance: Float)
        : LinearModel<FullCovarianceData>(weights, bias, biasVariance) {

        override fun sample(rng: Random) = Vector(weights.size) { rng.nextNormal(weights[it]) } * cholesky

        override fun train(instance: Instance, result: Float, weight: Float) {
            val pred = predict(instance)
            val diff = result - pred
            val varF = family.variance(pred)
            val alpha = learningRate * weight

            val v = Vector(problem.nbrValues)
            val svarF = sqrt(varF)
            for (i in instance) v[i] = svarF
            val u = covariance * v
            val s = sqrt(1 + (u dot v) / alpha)
            u.divide(s)

            // Down date covariance matrix
            for (i in covariance.indices)
                for (j in covariance.indices)
                    covariance[i][j] -= u[i] * u[j]

            cholesky.choleskyDowndate(u)

            val x = instance.toFloatArray()
            val step = covariance * (x * diff) * alpha
            weights.add(step)

            biasVariance /= 1 + varF * alpha
            bias += biasVariance * diff * alpha
            learningRate = min(1f, learningRate * learningRateGrowth)
        }

        override fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
            for (i in instances.indices)
                train(instances[i], results[i], weights?.get(i) ?: 1.0f)
        }

        override fun importData(data: FullCovarianceData) {
            bias = data.bias
            biasVariance = data.biasVariance
            for (i in weights.indices) {
                weights[i] = data.weights[i]
                covariance[i] = data.covariance[i].copyOf()
                cholesky[i] = data.cholesky[i].copyOf()
            }
        }

        override fun exportData() = FullCovarianceData(weights.copyOf(),
                Array(covariance.size) { covariance[it].copyOf() },
                Array(cholesky.size) { cholesky[it].copyOf() }, bias, biasVariance)
    }

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
                    Vector(n), Vector(n) { regularizationFactor }, bias, 0.1f * regularizationFactor)
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
                    Vector(n), Matrix(n) { Vector(n) }, Matrix(n) { Vector(n) }, bias, 0.1f * regularizationFactor).apply {
                for (i in 0 until n) {
                    covariance[i, i] = regularizationFactor
                    cholesky[i, i] = sqrt(regularizationFactor)
                }
            }
            return LinearBandit(problem, family, model, randomSeed, maximize, link ?: family.canonicalLink(), optimizer,
                    learningRate, learningRateGrowth, batchThreshold, rewards, trainAbsError, testAbsError)
        }
    }
}