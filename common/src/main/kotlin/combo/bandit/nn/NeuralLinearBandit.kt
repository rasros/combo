package combo.bandit.nn

import combo.bandit.BanditData
import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.bandit.glm.*
import combo.math.*
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos
import kotlin.math.sqrt

// TODO doc
class NeuralLinearBandit(val problem: Problem,
                         val network: NeuralNetwork,
                         val linearModel: LinearModel,
                         val optimizer: Optimizer<NeuralLinearObjective> = LocalSearch.Builder(problem).build(),
                         val batchSize: Int = 512,
                         val baseVariance: Float = 0.1f,
                         val weightUpdateDecay: Float = 0f,
                         val varianceUpdateDecay: Float = 0f,
                         val useStatic: Boolean = true,
                         val staticCacheSize: Int = 50,
                         override val randomSeed: Int = nanos().toInt(),
                         override val maximize: Boolean = true,
                         override val rewards: DataSample = VoidSample,
                         override val trainAbsError: DataSample = VoidSample,
                         override val testAbsError: DataSample = VoidSample) : PredictionBandit<NeuralLinearData> {

    private val randomSequence = RandomSequence(randomSeed)
    private var linear: LinearModel = linearModel
    private var trainingSteps = 0L

    private var static = if (useStatic) network.toStaticNetwork(staticCacheSize) else network

    // These array buffers hold update batch data
    private val instances = arrayOfNulls<Instance>(batchSize)
    private val results = FloatArray(batchSize)
    private val weights = FloatArray(batchSize)
    private var bufferPtr = 0

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val rng = randomSequence.next()
        val weights = linear.sample(rng)
        val objective = NeuralLinearObjective(maximize, static, weights)
        return optimizer.optimizeOrThrow(objective, assumptions)
    }

    override fun optimalOrThrow(assumptions: IntCollection): Instance {
        val objective = NeuralLinearObjective(maximize, static, linearModel.weights)
        return optimizer.optimizeOrThrow(objective, assumptions)
    }

    override fun predict(instance: Instance) = linear.predict(transfer(instance))

    // Apply the neural network layer until the output layer
    private fun transfer(instance: Instance) = static.activate(instance, 0, static.layers.size - 2)

    override fun train(instance: Instance, result: Float, weight: Float) {
        trainingSteps++
        instances[bufferPtr] = instance
        results[bufferPtr] = result
        weights[bufferPtr] = weight
        bufferPtr++
        if (bufferPtr == instances.size) {
            @Suppress("UNCHECKED_CAST")
            network.trainAll(instances as Array<Instance>, results, weights)
            static = if (useStatic) network.toStaticNetwork(staticCacheSize) else network
            val newLinear = linear.blank(baseVariance)
            for (i in instances.indices)
                newLinear.train(transfer(instances[i]), results[i], weights[i])
            val relativeWeight = batchSize / trainingSteps.toFloat()
            linear.importData(newLinear.exportData(), relativeWeight * varianceUpdateDecay, relativeWeight * weightUpdateDecay)
            for (i in instances.indices) instances[i] = null
            bufferPtr = 0
        } else {
            linear.train(transfer(instance), result, weight)
        }
    }

    override fun importData(data: NeuralLinearData) {
        // TODO does nothing so far, data has to be imported through builder
    }

    override fun exportData(): NeuralLinearData {
        return NeuralLinearData(network.toStaticNetwork(0), linear.exportData())
    }

    class Builder(val networkBuilder: NeuralNetworkBuilder) : PredictionBanditBuilder<NeuralLinearData> {

        private var randomSeed: Int = networkBuilder.randomSeed
        private var linear: LinearModel? = null
        private var rewards: DataSample = VoidSample
        private var trainAbsError: DataSample = VoidSample
        private var testAbsError: DataSample = VoidSample
        private var data: NeuralLinearData? = null
        private var maximize: Boolean = true
        private var optimizer: Optimizer<NeuralLinearObjective>? = null
        private var batchSize: Int = 512
        private var baseVariance: Float = 1f
        private var weightUpdateDecay: Float = 1.0f
        private var varianceUpdateDecay: Float = 1.0f
        private var useStatic: Boolean = true
        private var staticCacheSize = 50
        private var exploration: Float = 0.1f

        override fun importData(data: NeuralLinearData) = apply { this.data = data }

        override fun randomSeed(randomSeed: Int) = apply { networkBuilder.randomSeed(randomSeed) }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }

        override fun parallel() = ParallelPredictionBandit.Builder(this)

        fun batchSize(batchSize: Int) = apply { this.batchSize = batchSize }
        fun optimizer(optimizer: Optimizer<NeuralLinearObjective>) = apply { this.optimizer = optimizer }
        fun baseVariance(baseVariance: Float) = apply { this.baseVariance = baseVariance }
        fun weightUpdateDecay(weightUpdateDecay: Float) = apply { this.weightUpdateDecay = weightUpdateDecay }
        fun varianceUpdateDecay(varianceUpdateDecay: Float) = apply { this.varianceUpdateDecay = varianceUpdateDecay }
        fun useStatic(useStatic: Boolean) = apply { this.useStatic = useStatic }
        fun staticCacheSize(staticCacheSize: Int) = apply { this.staticCacheSize = staticCacheSize }
        fun linearModel(linearModel: LinearModel) = apply { this.linear = linearModel }

        private fun defaultLinear(network: NeuralNetwork): LinearModel {
            val n = network.layers[network.layers.size - 1].size
            val covariance = vectors.zeroMatrix(n)
            val covarianceL = vectors.zeroMatrix(n)
            for (i in 0 until n) {
                covariance[i, i] = baseVariance
                covarianceL[i, i] = sqrt(baseVariance)
            }
            val family = when (networkBuilder.output) {
                is LogitTransform -> BinomialVariance
                is LogTransform -> PoissonVariance
                else -> NormalVariance
            }
            val bias = if (networkBuilder.output is LogTransform) 1f else 0f
            return CovarianceLinearModel(
                    family, networkBuilder.output, MSELoss, MSELoss, networkBuilder.regularizationFactor,
                    ConstantRate(1f), exploration, 0L, vectors.zeroVector(n), covariance, covarianceL, bias, 1 / baseVariance)
        }

        override fun build(): NeuralLinearBandit {
            val network = networkBuilder.build()
            val linear = linear ?: defaultLinear(network)
            val optimizer = optimizer
                    ?: LocalSearch.Builder(networkBuilder.problem).randomSeed(randomSeed).fallbackCached().build()
            return NeuralLinearBandit(networkBuilder.problem, network, linear, optimizer, batchSize, baseVariance,
                    weightUpdateDecay, varianceUpdateDecay, useStatic, staticCacheSize, randomSeed, maximize, rewards, trainAbsError, testAbsError)
        }
    }
}

class NeuralLinearData(val network: NeuralNetwork, val linearModel: LinearData) : BanditData {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}


