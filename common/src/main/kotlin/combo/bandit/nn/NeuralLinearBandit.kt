package combo.bandit.nn

import combo.bandit.*
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
                         val optimizer: Optimizer<NeuralNetworkObjective> = LocalSearch.Builder(problem).build(),
                         val batchSize: Int,
                         val useStatic: Boolean,
                         val staticCacheSize: Int,
                         val epochs: Int,
                         override val randomSeed: Int = nanos().toInt(),
                         override val maximize: Boolean,
                         override val rewards: DataSample,
                         override val trainAbsError: DataSample,
                         override val testAbsError: DataSample) : PredictionBandit<NeuralLinearData> {

    private val randomSequence = RandomSequence(randomSeed)
    private var linear: LinearModel = linearModel
    private var trainingSteps = 0L

    private var static = if (useStatic) network.toStaticNetwork(staticCacheSize) else network

    private val oldBatches: MutableList<BatchUpdate>? = if (epochs > 1) ArrayList() else null

    // These array buffers hold partial update batch data
    private val instances = arrayOfNulls<Instance>(batchSize)
    private val results = FloatArray(batchSize)
    private val weights = FloatArray(batchSize)
    private var bufferPtr = 0

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val rng = randomSequence.next()
        val bias: Float
        val weights: VectorView
        if (useStatic) {
            // Can use the preferred sampling technique
            val l = static.layers.last() as DenseLayer
            bias = l.biases[0]
            weights = l.weights[0]
        } else {
            bias = linearModel.bias
            weights = linearModel.weights
        }
        val sampled = linearModel.sample(rng, weights)
        val objective = NeuralLinearObjective(maximize, static, sampled, bias)
        //val objective = NeuralNetworkObjective(maximize, static)
        return optimizer.optimizeOrThrow(objective, assumptions)
    }

    override fun optimalOrThrow(assumptions: IntCollection): Instance {
        val objective = NeuralNetworkObjective(maximize, static)
        return optimizer.optimizeOrThrow(objective, assumptions)
    }

    override fun predict(instance: Instance) = static.predict(instance)

    // Apply the neural network layer until the output layer
    private fun transfer(instance: Instance) = static.activate(instance, 0, static.layers.size - 2)

    override fun train(instance: Instance, result: Float, weight: Float) {
        trainingSteps++
        instances[bufferPtr] = instance
        results[bufferPtr] = result
        weights[bufferPtr] = weight
        bufferPtr++
        val z = transfer(instance)
        linear.train(z, result, weight)
        if (bufferPtr == instances.size) {
            @Suppress("UNCHECKED_CAST")
            network.trainAll(instances as Array<Instance>, results, weights)
            static = if (useStatic) network.toStaticNetwork(staticCacheSize) else network
            bufferPtr = 0

            if (oldBatches != null) {
                for (o in oldBatches)
                    network.trainAll(o.instances, o.results, o.weights)
                val rng = randomSequence.next()
                val n = rng.nextBinomial(1 - 1f / (epochs + 1), oldBatches.size)
                repeat(n) {
                    oldBatches.removeAt(rng.nextInt(oldBatches.size))
                }
                oldBatches.add(BatchUpdate(instances, results, weights))
            }
            if (useStatic) {
                val last = static.layers.last() as DenseLayer
                for (i in 0 until linear.weights.size)
                    linear.weights[i] = last.weights[0][i]
                linear.bias = last.biases[0]
            }
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
        private var optimizer: Optimizer<NeuralNetworkObjective>? = null
        private var batchSize: Int = 64
        private var useStatic: Boolean = true
        private var staticCacheSize: Int = 50
        private var epochs: Int = 5

        override fun importData(data: NeuralLinearData) = apply { this.data = data }

        override fun randomSeed(randomSeed: Int) = apply { networkBuilder.randomSeed(randomSeed) }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }

        override fun parallel() = ParallelPredictionBandit.Builder(this)

        fun batchSize(batchSize: Int) = apply { this.batchSize = batchSize }
        fun optimizer(optimizer: Optimizer<NeuralNetworkObjective>) = apply { this.optimizer = optimizer }
        fun useStatic(useStatic: Boolean) = apply { this.useStatic = useStatic }
        fun staticCacheSize(staticCacheSize: Int) = apply { this.staticCacheSize = staticCacheSize }
        fun epochs(epochs: Int) = apply { this.epochs = epochs }

        fun linearModel(linearModel: LinearModel) = apply {
            require(linearModel.weights.size == networkBuilder.hiddenLayerWidth) { "Size missmatch between hidden layers and linear model." }
            this.linear = linearModel
        }

        fun defaultLinear(): CovarianceLinearModel.Builder {
            //val n = network.layers[network.layers.size - 1].size
            val n = networkBuilder.hiddenLayerWidth
            val covariance = vectors.zeroMatrix(n)
            val covarianceL = vectors.zeroMatrix(n)
            for (i in 0 until n) {
                covariance[i, i] = 1f
                covarianceL[i, i] = sqrt(1f)
            }
            val family = when (networkBuilder.output) {
                is LogitTransform -> BinomialVariance
                is LogTransform -> PoissonVariance
                else -> NormalVariance
            }
            val bias = if (networkBuilder.output is LogTransform) 1f else 0f
            return CovarianceLinearModel.Builder(Problem(n))
                    .family(family)
                    .bias(bias)
                    .regularizationFactor(networkBuilder.regularizationFactor)
        }

        override fun build(): NeuralLinearBandit {
            val network = networkBuilder.build()
            val linear = linear ?: defaultLinear().build()
            val optimizer = optimizer
                    ?: LocalSearch.Builder(networkBuilder.problem).randomSeed(randomSeed).fallbackCached().build()
            return NeuralLinearBandit(networkBuilder.problem, network, linear, optimizer, batchSize, useStatic,
                    staticCacheSize, epochs, randomSeed, maximize, rewards, trainAbsError, testAbsError)
        }
    }
}

class NeuralLinearData(val network: NeuralNetwork, val linearModel: LinearData) : BanditData {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}


