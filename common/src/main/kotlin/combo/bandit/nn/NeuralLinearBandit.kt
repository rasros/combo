package combo.bandit.nn

import combo.bandit.BanditData
import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.bandit.glm.*
import combo.math.*
import combo.model.EffectCodedVector
import combo.model.Model
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.EffectCodedObjective
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.ObjectiveFunction
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos
import kotlin.math.sqrt

// TODO doc
class NeuralLinearBandit(val model: Model,
                         val network: NeuralNetwork,
                         val linearModel: LinearModel,
                         val optimizer: Optimizer<ObjectiveFunction> = LocalSearch.Builder(model.problem).build(),
                         val batchSize: Int,
                         val useStatic: Boolean,
                         val staticCacheSize: Int,
                         val epochs: Int,
                         val effectCoding: Boolean,
                         override val randomSeed: Int = nanos().toInt(),
                         override val maximize: Boolean,
                         override val rewards: DataSample,
                         override val trainAbsError: DataSample,
                         override val testAbsError: DataSample) : PredictionBandit<NeuralLinearData> {

    private val randomSequence = RandomSequence(randomSeed)
    private var linear: LinearModel = linearModel
    private var trainingSteps = 0L

    private var static = if (useStatic) network.toStaticNetwork(staticCacheSize) else network

    private inner class BatchUpdate(val instances: Array<Instance>, val results: FloatArray, val weights: FloatArray) {
        @Suppress("UNCHECKED_CAST")
        fun toVectors(): Array<out VectorView> = if (effectCoding) Array(instances.size) { EffectCodedVector(model, instances[it]) }
        else instances as Array<VectorView>
    }

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
        val objective = NeuralLinearObjective(maximize, static, sampled, bias).let {
            if (effectCoding) EffectCodedObjective(it, model)
            else it
        }
        return optimizer.optimizeOrThrow(objective, assumptions)
    }

    override fun optimalOrThrow(assumptions: IntCollection): Instance {
        val objective = NeuralNetworkObjective(maximize, static).let {
            if (effectCoding) EffectCodedObjective(it, model)
            else it
        }
        return optimizer.optimizeOrThrow(objective, assumptions)
    }

    override fun predict(instance: Instance) = if (effectCoding) static.predict(EffectCodedVector(model, instance))
    else static.predict(instance)

    // Apply the neural network layer until the output layer
    private fun transfer(instance: Instance) = if (effectCoding) static.activate(EffectCodedVector(model, instance), 0, static.layers.size - 2)
    else static.activate(instance, 0, static.layers.size - 2)

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
            val bu = BatchUpdate(instances.copyOf() as Array<Instance>, results.copyOf(), weights.copyOf())
            network.trainAll(bu.toVectors(), bu.results, bu.weights)
            static = if (useStatic) network.toStaticNetwork(staticCacheSize) else network
            bufferPtr = 0

            if (oldBatches != null) {
                for (o in oldBatches)
                    network.trainAll(o.toVectors(), o.results, o.weights)
                val rng = randomSequence.next()
                val dropped = rng.nextBinomial(1f / epochs, oldBatches.size)
                repeat(dropped) {
                    oldBatches.removeAt(rng.nextInt(oldBatches.size))
                }
                oldBatches.add(bu)
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

    class Builder(val model: Model, val network: NeuralNetwork) : PredictionBanditBuilder<NeuralLinearData> {

        constructor(model: Model, networkBuilder: NeuralNetworkBuilder) : this(model, networkBuilder.build()) {
            randomSeed = networkBuilder.randomSeed
        }

        private var randomSeed: Int = nanos().toInt()
        private var linear: LinearModel? = null
        private var rewards: DataSample = VoidSample
        private var trainAbsError: DataSample = VoidSample
        private var testAbsError: DataSample = VoidSample
        private var data: NeuralLinearData? = null
        private var maximize: Boolean = true
        private var optimizer: Optimizer<ObjectiveFunction>? = null
        private var batchSize: Int = 64
        private var useStatic: Boolean = true
        private var staticCacheSize: Int = 50
        private var epochs: Int = 5
        private var effectCoding: Boolean = true

        override fun importData(data: NeuralLinearData) = apply { this.data = data }

        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }

        override fun parallel() = ParallelPredictionBandit.Builder(this)

        fun batchSize(batchSize: Int) = apply { this.batchSize = batchSize }
        fun optimizer(optimizer: Optimizer<ObjectiveFunction>) = apply { this.optimizer = optimizer }
        @Suppress("UNCHECKED_CAST")
        override fun suggestOptimizer(optimizer: Optimizer<*>) = optimizer(optimizer as Optimizer<ObjectiveFunction>)

        fun useStatic(useStatic: Boolean) = apply { this.useStatic = useStatic }
        fun staticCacheSize(staticCacheSize: Int) = apply { this.staticCacheSize = staticCacheSize }
        fun epochs(epochs: Int) = apply { this.epochs = epochs }

        /** Whether variables should be -1,1 coded (true) or 0,1 coded. */
        fun effectCoding(effectCoding: Boolean) = apply { this.effectCoding = effectCoding }

        private fun hiddenLayerWidth() = network.layers[network.layers.size - 2].size

        fun linearModel(linearModel: LinearModel) = apply {
            require(linearModel.weights.size == hiddenLayerWidth()) { "Size missmatch between hidden layers and linear model." }
            this.linear = linearModel
        }

        fun defaultLinear(): CovarianceLinearModel.Builder {
            //val n = network.layers[network.layers.size - 1].size
            val n = hiddenLayerWidth()
            val covariance = vectors.zeroMatrix(n)
            val covarianceL = vectors.zeroMatrix(n)
            for (i in 0 until n) {
                covariance[i, i] = 1f
                covarianceL[i, i] = sqrt(1f)
            }
            val out = network.output
            val family = when (out) {
                is ScalarTransform -> {
                    when (out.transform) {
                        is LogitTransform -> BinomialVariance
                        is LogTransform -> PoissonVariance
                        else -> NormalVariance
                    }
                }
                is BinarySoftmaxLayer -> BinomialVariance
                else -> NormalVariance
            }
            return CovarianceLinearModel.Builder(Problem(n)).family(family)
        }

        override fun build(): NeuralLinearBandit {
            val linear = linear ?: defaultLinear().build()
            val optimizer = optimizer
                    ?: LocalSearch.Builder(model.problem).randomSeed(randomSeed).fallbackCached().build()
            return NeuralLinearBandit(model, network, linear, optimizer, batchSize, useStatic,
                    staticCacheSize, epochs, effectCoding, randomSeed, maximize, rewards, trainAbsError, testAbsError)
        }
    }
}

class NeuralLinearData(val network: NeuralNetwork, val linearModel: LinearData) : BanditData {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}


