package combo.bandit.nn

import combo.math.*
import combo.sat.Problem
import combo.util.nanos
import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.layers.DenseLayer
import org.deeplearning4j.nn.conf.layers.OutputLayer
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.activations.IActivation
import org.nd4j.linalg.activations.impl.ActivationIdentity
import org.nd4j.linalg.activations.impl.ActivationReLU
import org.nd4j.linalg.activations.impl.ActivationSigmoid
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.learning.config.RmsProp
import org.nd4j.linalg.lossfunctions.LossFunctions
import kotlin.math.sqrt
import kotlin.random.Random

class DL4jNetwork(val network: MultiLayerNetwork) : NeuralNetwork {

    override val layers: Array<Layer> = Array(network.layers.size - 1) { DL4jLayer(it) }
    override val output: VectorTransform = DL4jOutput()

    override fun train(input: VectorView, result: Float, weight: Float) {
        trainAll(arrayOf(input), floatArrayOf(result), floatArrayOf(weight))
    }

    override fun trainAll(input: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {
        val mat = Nd4j.create(input.size, input[0].size)
        for (i in input.indices)
            mat.putRow(i.toLong(), input[i].toNd4j().array)
        val y = Nd4j.create(results, intArrayOf(input.size, 1), 'c')
        network.fit(mat, y)
        network.clear()
    }

    override fun activate(input: VectorView, fromLayer: Int, toLayer: Int): Vector {
        val mat = Nd4j.create(1, input.size)
        mat.putRow(0L, input.toNd4j().array)
        return Nd4jVector(network.activateSelectedLayers(fromLayer, toLayer + 1, mat).transposei())
    }

    override fun predict(input: VectorView): Float {
        val mat = Nd4j.create(1, input.size)
        mat.putRow(0L, input.toNd4j().array)
        return network.output(mat).getFloat(0L)
    }

    inner class DL4jLayer(val layerIx: Int) : Layer {
        override val size: Int get() = network.layerSize(layerIx)
        override fun activate(vector: VectorView): Vector {
            val mat = Nd4j.create(1, vector.size)
            mat.putRow(0L, vector.toNd4j().array)
            val r = network.layers[layerIx].activate(mat, false, LayerWorkspaceMgr.noWorkspaces()).transposei()
            return Nd4jVector(r)
        }
    }

    inner class DL4jOutput : VectorTransform {
        override fun apply(vector: VectorView) = activate(vector, layers.size - 1, layers.size)[0]
    }

    override fun toStaticNetwork(cacheSize: Int): StaticNetwork {
        var output: Transform? = null

        fun IActivation.toTransform() = when (this) {
            is ActivationReLU -> RectifierTransform
            is ActivationIdentity -> IdentityTransform
            is ActivationSigmoid -> LogitTransform
            else -> object : Transform {
                override fun apply(value: Float) = this@toTransform.getActivation(Nd4j.scalar(value), false).getFloat(0L)
            }
        }

        val layers = Array<Layer>(network.layers.size) {
            when {
                network.layers[it] is org.deeplearning4j.nn.layers.feedforward.dense.DenseLayer -> {
                    val layer = network.layers[it] as org.deeplearning4j.nn.layers.feedforward.dense.DenseLayer
                    layer.conf()
                    val activation = (layer.config as DenseLayer).activationFn
                    val biases = network.layers[it].getParam("b")
                    val weights = network.layers[it].getParam("W").transpose()
                    val staticWeights = FloatMatrix(Nd4jMatrix(weights).toArray())
                    val staticBiases = FloatVector(Nd4jVector(biases).toFloatArray())
                    DenseLayer(staticWeights, staticBiases, activation.toTransform())
                }
                network.layers[it] is org.deeplearning4j.nn.layers.OutputLayer -> {
                    val layer = network.layers[it]
                    layer.conf()
                    output = (layer.config as OutputLayer).activationFn.toTransform()
                    val biases = network.layers[it].getParam("b")
                    val weights = network.layers[it].getParam("W").transpose()
                    val staticWeights = FloatMatrix(Nd4jMatrix(weights).toArray())
                    val staticBiases = FloatVector(Nd4jVector(biases).toFloatArray())
                    DenseLayer(staticWeights, staticBiases, IdentityTransform)
                }
                else -> throw UnsupportedOperationException("Unsupported network layer type, at ix: $it")
            }
        }
        return StaticNetwork(layers, ScalarTransform(output!!), cacheSize)
    }

    class Builder(override val problem: Problem) : NeuralNetworkBuilder {

        override var output: VectorTransform = ScalarTransform(IdentityTransform)
            private set
        override var randomSeed: Int = nanos().toInt()
            private set
        var regularizationFactor: Float = 0.1f
            private set
        override var hiddenLayers: Int = 2
            private set
        override var hiddenLayerWidth: Int = 10
            private set
        var initWeightVariance: Float = 0.001f
            private set
        var learningRate: Float = 0.01f
            private set

        override fun output(output: VectorTransform) = apply { this.output = output }
        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        fun regularizationFactor(regularizationFactor: Float) = apply { this.regularizationFactor = regularizationFactor }
        override fun hiddenLayers(hiddenLayers: Int) = apply { this.hiddenLayers = hiddenLayers }
        override fun hiddenLayerWidth(hiddenLayerWidth: Int) = apply { this.hiddenLayerWidth = hiddenLayerWidth }
        fun initWeightVariance(initWeightVariance: Float) = apply { this.initWeightVariance = initWeightVariance }
        fun learningRate(learningRate: Float) = apply { this.learningRate = learningRate }

        fun buildOutputLayer(nIn: Int, output: VectorTransform): OutputLayer {
            val conf = when (output) {
                is ScalarTransform -> when (output.transform) {
                    is LogTransform -> OutputLayer.Builder(LossFunctions.LossFunction.POISSON).activation(Activation.IDENTITY)
                    is LogitTransform -> OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.SIGMOID)
                    is RectifierTransform -> OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.RELU)
                    else -> OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY)
                }
                is BinarySoftmaxLayer -> OutputLayer.Builder(LossFunctions.LossFunction.XENT).activation(Activation.SOFTMAX)
                else -> OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY)
            }
            return conf.nIn(nIn).nOut(1).build()
        }

        override fun build(): DL4jNetwork {
            val conf = NeuralNetConfiguration.Builder()
                    .weightDecay(regularizationFactor.toDouble())
                    .miniBatch(true)
                    .weightInit(WeightInit.NORMAL)
                    .seed(randomSeed.toLong())
                    .activation(Activation.RELU)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(RmsProp(learningRate.toDouble()))
                    .list()
                    .layer(DenseLayer.Builder().nIn(problem.nbrValues).nOut(hiddenLayerWidth).build())
                    .apply {
                        repeat(hiddenLayers) {
                            layer(DenseLayer.Builder().nIn(hiddenLayerWidth).nOut(hiddenLayerWidth).build())
                        }
                    }
                    .layer(buildOutputLayer(hiddenLayerWidth, output))
                    .build()
            val network = MultiLayerNetwork(conf)

            // Randomize weights
            val hn = hiddenLayerWidth
            val weights = FloatArray((problem.nbrValues + 1) * hn + hiddenLayers * hn * (hn + 1) + hn + 1)
            val rng = Random(randomSeed)
            val sd = sqrt(initWeightVariance)
            for (i in 0 until hn * problem.nbrValues)
                weights[i] = rng.nextNormal(0f, sd)
            for (r in 0 until hiddenLayers)
                for (i in 0 until hn * hn)
                    weights[(problem.nbrValues + 1) * hn + r * hn * (hn + 1) + i] = rng.nextNormal(0f, sd)
            for (i in 0 until hn)
                weights[(problem.nbrValues + 1) * hn + hiddenLayers * hn * (hn + 1) + i] = rng.nextNormal(0f, sd)

            // Default bias for output layer with poisson data will be 1
            if (output is ScalarTransform && (output as ScalarTransform).transform is LogTransform)
                weights[weights.lastIndex] = 1f

            val params = Nd4j.create(weights).reshape(intArrayOf(1, weights.size))
            network.init(params, true)
            return DL4jNetwork(network)
        }
    }
}

