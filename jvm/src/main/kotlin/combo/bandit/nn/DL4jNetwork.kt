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
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.learning.config.RmsProp
import org.nd4j.linalg.lossfunctions.LossFunctions

class DL4jNetwork(val network: MultiLayerNetwork) : NeuralNetwork {

    override val layers: Array<Layer> = Array(network.layers.size - 1) { DL4jLayer(it) }
    override val output: VectorTransform<Float> = DL4jOutput()

    override fun train(input: VectorView, result: Float, weight: Float) {
        trainAll(arrayOf(input), floatArrayOf(result), floatArrayOf(weight))
    }

    override fun trainAll(input: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {
        val mat = Nd4j.create(input.size, input[0].size)
        for (i in input.indices)
            mat.putRow(i.toLong(), input[i].toNd4j().array)
        val y = Nd4j.create(results, intArrayOf(input.size, 1), 'c')
        network.fit(mat, y)
    }

    override fun activate(input: VectorView, fromLayer: Int, toLayer: Int): Vector {
        val mat = Nd4j.create(1, input.size)
        mat.putRow(0L, input.toNd4j().array)
        return Nd4jVector(network.activateSelectedLayers(fromLayer, toLayer, mat).transposei())
    }

    override fun predict(input: VectorView): Float {
        val mat = Nd4j.create(1, input.size)
        mat.putRow(0L, input.toNd4j().array)
        return network.output(mat).getFloat(0L)
    }

    inner class DL4jLayer(val layerIx: Int) : Layer {
        override val size: Int get() = network.layerSize(layerIx)
        override fun activate(vector: VectorView) = activate(vector, layerIx, layerIx + 1)
    }

    inner class DL4jOutput : VectorTransform<Float> {
        override fun apply(vector: VectorView) = activate(vector, layers.size - 1, layers.size)[0]
    }

    class Builder(override val problem: Problem) : NeuralNetworkBuilder {

        override var output: Transform = IdentityTransform
            private set
        private var randomSeed: Int = nanos().toInt()
        override var regularizationFactor: Float = 0.1f
            private set
        private var hiddenLayers: Int = 2
        private var hiddenLayerWidth: Int = 100

        override fun output(output: Transform) = apply { this.output = output }
        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun regularizationFactor(regularizationFactor: Float) = apply { this.regularizationFactor = regularizationFactor }
        override fun hiddenLayers(hiddenLayers: Int) = apply { this.hiddenLayers = hiddenLayers }
        override fun hiddenLayerWidth(hiddenLayerWidth: Int) = apply { this.hiddenLayerWidth = hiddenLayerWidth }

        fun defaultOutputLayer(nIn: Int, output: Transform): OutputLayer {
            val conf = when (output) {
                is LogTransform -> OutputLayer.Builder(LossFunctions.LossFunction.POISSON).activation(Activation.IDENTITY)
                is LogitTransform -> OutputLayer.Builder(LossFunctions.LossFunction.XENT).activation(Activation.SIGMOID)
                else -> OutputLayer.Builder(LossFunctions.LossFunction.MSE).activation(Activation.IDENTITY)
            }
            return conf.nIn(nIn).nOut(1).build()
        }

        override fun build(): DL4jNetwork {
            val conf = NeuralNetConfiguration.Builder()
                    .l2(1.0)
                    .miniBatch(true)
                    .weightInit(WeightInit.NORMAL)
                    .seed(randomSeed.toLong())
                    .activation(Activation.RELU)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(RmsProp())
                    .list()
                    .layer(DenseLayer.Builder().nIn(problem.nbrValues).nOut(hiddenLayerWidth).build())
                    .apply {
                        repeat(hiddenLayers) {
                            layer(DenseLayer.Builder().nIn(hiddenLayerWidth).nOut(hiddenLayerWidth).build())
                        }
                    }
                    .layer(defaultOutputLayer(hiddenLayerWidth, output))
                    .build()
            val network = MultiLayerNetwork(conf)
            network.init()
            return DL4jNetwork(network)
        }
    }
}

