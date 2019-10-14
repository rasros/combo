package combo.nn

import combo.math.*
import combo.sat.Instance
import combo.sat.optimizers.ObjectiveFunction
import combo.sat.times
import combo.util.transformArray
import combo.util.transformArrayIndexed
import kotlin.math.exp
import kotlin.math.sqrt

interface Layer

interface HiddenLayer : Layer {
    fun activate(vector: Vector): Vector
}

interface LayerTransform<T> {
    fun activate(vector: Vector): T
}

class InputLayer(val weights: Matrix, val biases: Vector, val activation: Transform) : Layer {
    fun activate(instance: Instance): Vector {
        val vec = weights * instance
        vec.add(biases)
        vec.transformArray { activation.apply(it) }
        return vec
    }
}

class DenseLayer(val weights: Matrix, val biases: Vector, val activation: Transform) : HiddenLayer {
    override fun activate(vector: Vector): Vector {
        val vec = weights * vector
        vec.add(biases)
        vec.transformArray { activation.apply(it) }
        return vec
    }
}

class BatchNormalizationLayer(val mean: Vector, val variance: Vector, val offset: Vector, val scale: Vector, val eps: Float) : HiddenLayer {
    override fun activate(vector: Vector): Vector {
        vector.sub(mean)
        vector.transformArrayIndexed { i, f ->
            f / sqrt(variance[i] + eps)
        }
        vector.transformArrayIndexed { i, f -> scale[i] * f + offset[i] }
        return vector
    }
}

class BinarySoftmaxLayer : LayerTransform<Float> {
    override fun activate(vector: Vector): Float {
        val eps1 = exp(vector[0])
        val eps2 = exp(vector[1])
        return eps2 / (eps1 + eps2)
    }
}

class FeedForwardRegressionObjective(val maximize: Boolean,
                                     val input: InputLayer,
                                     val hiddenLayers: Array<HiddenLayer>,
                                     val output: LayerTransform<Float>) : ObjectiveFunction {
    override fun value(instance: Instance): Float {
        var vec = input.activate(instance)
        for (h in hiddenLayers)
            vec = h.activate(vec)
        return if (maximize) output.activate(vec) else -output.activate(vec)
    }
}
