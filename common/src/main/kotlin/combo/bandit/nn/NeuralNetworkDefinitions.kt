package combo.bandit.nn

import combo.math.*
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.ObjectiveFunction
import kotlin.math.exp
import kotlin.math.sqrt

interface Layer {
    fun activate(vector: VectorView): Vector
    val size: Int
}

interface VectorTransform<T> {
    fun apply(vector: VectorView): T
}

class ScalarTransform(val transform: Transform) : VectorTransform<Float> {
    override fun apply(vector: VectorView) = transform.apply(vector[0])
}

class DenseLayer(val weights: Matrix, val biases: Vector, val activation: Transform) : Layer {
    override val size: Int get() = biases.size

    override fun activate(vector: VectorView): Vector {
        val vec = weights * vector
        vec.add(biases)
        vec.transform { activation.apply(it) }
        return vec
    }
}

class BatchNormalizationLayer(val mean: Vector, val variance: Vector, val offset: Vector, val scale: Vector, val eps: Float) : Layer {
    override val size: Int get() = mean.size
    private val denom = variance.copy().apply { transform { sqrt(it + eps) } }
    override fun activate(vector: VectorView): Vector {
        val vec = vector - mean
        vec.divide(denom)
        vec.multiply(scale)
        vec.add(offset)
        return vec
    }
}

class BinarySoftmaxLayer : VectorTransform<Float> {
    override fun apply(vector: VectorView): Float {
        val eps1 = exp(vector[0])
        val eps2 = exp(vector[1])
        return eps2 / (eps1 + eps2)
    }
}

interface NeuralNetwork {
    val layers: Array<Layer>
    val output: VectorTransform<Float>

    fun train(input: VectorView, result: Float, weight: Float = 1f)
    fun trainAll(input: Array<out VectorView>, results: FloatArray, weights: FloatArray? = null)

    fun activate(input: VectorView, fromLayer: Int, toLayer: Int = fromLayer): VectorView {
        var vec: VectorView = input
        for (i in fromLayer..toLayer)
            vec = layers[i].activate(vec)
        return vec
    }

    fun predict(input: VectorView): Float {
        val vec = activate(input, 0, layers.size - 1)
        return output.apply(vec)
    }

    fun toStaticNetwork(): StaticNetwork
}

class StaticNetwork(override val layers: Array<Layer>, override val output: VectorTransform<Float>) : NeuralNetwork {
    override fun trainAll(input: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {}
    override fun train(input: VectorView, result: Float, weight: Float) {}
    override fun toStaticNetwork() = this
}

class NeuralNetworkObjective(val maximize: Boolean, val network: NeuralNetwork) : ObjectiveFunction {
    override fun value(instance: Instance): Float {
        val pred = network.predict(instance)
        return if (maximize) pred else -pred
    }
}

class NeuralLinearObjective(val maximize: Boolean, val network: NeuralNetwork, val weights: VectorView) : ObjectiveFunction {
    override fun value(instance: Instance): Float {
        val z = network.activate(instance, 0, network.layers.size - 2) // TODO cached transform???
        val y = weights dot z
        return if (maximize) y else -y
    }
}

interface NeuralNetworkBuilder {
    val problem: Problem

    val output: Transform
    val randomSeed: Int
    val regularizationFactor: Float
    val hiddenLayers: Int
    val hiddenLayerWidth: Int
    val randomNoiseStd: Float

    fun output(output: Transform): NeuralNetworkBuilder
    fun randomSeed(randomSeed: Int): NeuralNetworkBuilder
    fun regularizationFactor(regularizationFactor: Float): NeuralNetworkBuilder
    fun hiddenLayers(hiddenLayers: Int): NeuralNetworkBuilder
    fun hiddenLayerWidth(hiddenLayerWidth: Int): NeuralNetworkBuilder
    fun randomNoiseStd(randomNoiseStd: Float): NeuralNetworkBuilder
    fun build(): NeuralNetwork
}
