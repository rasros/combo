package combo.bandit.nn

import combo.math.*
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.ObjectiveFunction
import combo.util.RandomMapCache
import kotlin.math.exp
import kotlin.math.sqrt

interface Layer {
    fun activate(vector: VectorView): Vector
    val size: Int
}

interface VectorTransform {
    fun apply(vector: VectorView): Float
}

class ScalarTransform(val transform: Transform) : VectorTransform {
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

class BinarySoftmaxLayer : VectorTransform {
    override fun apply(vector: VectorView): Float {
        val eps1 = exp(vector[0])
        val eps2 = exp(vector[1])
        return eps2 / (eps1 + eps2)
    }
}

interface NeuralNetwork {
    val layers: Array<Layer>
    val output: VectorTransform

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

    fun toStaticNetwork(cacheSize: Int): StaticNetwork
}

class StaticNetwork(override val layers: Array<Layer>, override val output: VectorTransform, cacheSize: Int = 0) : NeuralNetwork {

    private data class ActivationKey(val fromLayer: Int, val toLayer: Int, val input: Instance)

    private val cache: RandomMapCache<ActivationKey, VectorView>? =
            if (cacheSize > 0) RandomMapCache(cacheSize)
            else null

    override fun trainAll(input: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {}
    override fun train(input: VectorView, result: Float, weight: Float) {}
    override fun toStaticNetwork(cacheSize: Int) = this

    override fun activate(input: VectorView, fromLayer: Int, toLayer: Int): VectorView {
        return if (input is Instance && cache != null) {
            val activationKey = ActivationKey(fromLayer, toLayer, input.copy())
            cache.getOrPut(activationKey) { super.activate(input, fromLayer, toLayer) }
        } else super.activate(input, fromLayer, toLayer)
    }
}

open class NeuralNetworkObjective(val maximize: Boolean, val network: NeuralNetwork) : ObjectiveFunction {
    override fun value(vector: VectorView): Float {
        val pred = network.predict(vector)
        return if (maximize) -pred else pred
    }
}

class NeuralLinearObjective(maximize: Boolean, network: NeuralNetwork, val weights: VectorView, val bias: Float) : NeuralNetworkObjective(maximize, network) {
    override fun value(vector: VectorView): Float {
        val z = network.activate(vector, 0, network.layers.size - 2)
        val y = weights * z
        y.add(bias)
        val f = network.output.apply(y)
        return if (maximize) -f else f
    }
}

interface NeuralNetworkBuilder {
    val problem: Problem

    val output: VectorTransform
    val randomSeed: Int
    val hiddenLayers: Int
    val hiddenLayerWidth: Int

    fun output(output: VectorTransform): NeuralNetworkBuilder
    fun randomSeed(randomSeed: Int): NeuralNetworkBuilder
    fun hiddenLayers(hiddenLayers: Int): NeuralNetworkBuilder
    fun hiddenLayerWidth(hiddenLayerWidth: Int): NeuralNetworkBuilder
    fun build(): NeuralNetwork
}
