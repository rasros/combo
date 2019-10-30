package combo.bandit.nn

import combo.bandit.nn.BatchNormalizationLayer
import combo.bandit.nn.BinarySoftmaxLayer
import combo.bandit.nn.DenseLayer
import combo.math.FallbackMatrix
import combo.math.FallbackVector
import combo.math.IdentityTransform
import combo.math.RectifierTransform
import combo.sat.BitArray
import combo.test.assertContentEquals
import combo.test.assertEquals
import kotlin.math.exp
import kotlin.test.Test

class DenseLayerTest {
    @Test
    fun activate() {
        // 3 features, 2 neurons
        val layer = DenseLayer(
                FallbackMatrix(arrayOf(
                        floatArrayOf(1f, 2f, -1f),
                        floatArrayOf(-2f, 1.5f, 3f)
                )), FallbackVector(floatArrayOf(1f, 0.5f))
                , IdentityTransform)
        val vec = FallbackVector(floatArrayOf(-1f, 2f, 1f))
        val result1 = layer.activate(vec)
        assertContentEquals(floatArrayOf(3f, 8.5f), result1.toFloatArray())
        val inst = BitArray(3)
        inst[0] = true;inst[2] = true
        val result2 = layer.activate(inst)
        assertContentEquals(floatArrayOf(1f, 1.5f), result2.toFloatArray())
    }
}

class BatchNormalizationLayerTest {
    @Test
    fun activate() {
        val layer = BatchNormalizationLayer(FallbackVector(floatArrayOf(1f, -2f)), FallbackVector(floatArrayOf(4f, 1f)),
                FallbackVector(floatArrayOf(1f, -1f)), FallbackVector(floatArrayOf(2f, 1f)), 0.0f)
        val vec = FallbackVector(floatArrayOf(-1f, 2f))
        val result1 = layer.activate(vec)
        assertContentEquals(floatArrayOf(-1f, 3f), result1.toFloatArray())

        val inst = BitArray(2)
        inst[0] = true
        val result2 = layer.activate(inst)
        assertContentEquals(floatArrayOf(1f, 1f), result2.toFloatArray())
    }
}

class BinarySoftmaxLayerTest {
    @Test
    fun activate() {
        val layer = BinarySoftmaxLayer()
        val vec = FallbackVector(floatArrayOf(-1f, 2f))
        val result = layer.apply(vec)
        assertEquals(0.952574113f, result, 1e-8f)
    }
}

class StaticNetworkTest {

    @Test
    fun activate() {
        // 3 features, 2 neurons
        val input = DenseLayer(
                FallbackMatrix(arrayOf(
                        floatArrayOf(1f, 2f, -1f),
                        floatArrayOf(-2f, 1.5f, -3f)
                )), FallbackVector(floatArrayOf(0.5f, -1f))
                , RectifierTransform)
        val output = BinarySoftmaxLayer()
        val net = StaticNetwork(arrayOf(input), output)

        val vec = FallbackVector(floatArrayOf(-1f, 2f, 1.5f))
        val result = net.predict(vec)
        assertEquals(1f / (1 + exp(2f)), result, 1e-8f)
    }
}