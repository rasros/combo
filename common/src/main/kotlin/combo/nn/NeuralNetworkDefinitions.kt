package combo.nn

import combo.math.*
import combo.sat.Instance
import combo.sat.optimizers.ObjectiveFunction
import kotlin.math.exp
import kotlin.math.sqrt

interface Layer {
    fun activate(vector: VectorView): Vector
}

interface VectorTransform<T> {
    fun apply(vector: VectorView): T
}

class DenseLayer(val weights: Matrix, val biases: Vector, val activation: Transform) : Layer {
    override fun activate(vector: VectorView): Vector {
        val vec = weights * vector
        vec.add(biases)
        vec.transform { activation.apply(it) }
        return vec
    }
}

class BatchNormalizationLayer(val mean: Vector, val variance: Vector, val offset: Vector, val scale: Vector, val eps: Float) : Layer {
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

class RegressionNetwork(val maximize: Boolean,
                        val layers: Array<Layer>,
                        val output: VectorTransform<Float>) : ObjectiveFunction {
    fun predict(input: VectorView): Float {
        var vec: VectorView = input
        for (h in layers)
            vec = h.activate(vec)
        return if (maximize) output.apply(vec) else -output.apply(vec)
    }

    override fun value(instance: Instance) = predict(instance)
}
