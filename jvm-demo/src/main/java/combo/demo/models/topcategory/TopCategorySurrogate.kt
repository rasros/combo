package combo.demo.models.topcategory

import combo.bandit.nn.*
import combo.demo.ContextProvider
import combo.demo.SurrogateModel
import combo.math.IdentityTransform
import combo.math.RectifierTransform
import combo.math.Transform
import combo.math.vectors
import combo.sat.Instance
import combo.sat.SparseBitArray
import combo.sat.optimizers.Optimizer
import combo.sat.set
import combo.util.IntCollection
import combo.util.IntHashSet
import java.io.InputStreamReader
import kotlin.random.Random

class TopCategorySurrogate : SurrogateModel<NeuralNetworkObjective> {

    override val model = topCategoryModel()
    val network = StaticNetwork(
            arrayOf(readDenseLayerWeights(1, RectifierTransform),
                    readBatchNormLayer(2),
                    readDenseLayerWeights(3, RectifierTransform),
                    readBatchNormLayer(4),
                    readDenseLayerWeights(5, IdentityTransform)),
            BinarySoftmaxLayer())
    val o = NeuralNetworkObjective(true, network)

    fun contextProvider(k: Int) = object : ContextProvider {
        override fun context(rng: Random): IntCollection {
            val lits = IntHashSet()
            model["Top-k", k].collectLiterals(model.index, lits)
            model["domain", "D${rng.nextInt(1, 4)}"].collectLiterals(model.index, lits)
            return lits
        }
    }

    override fun reward(instance: Instance, prediction: Float, rng: Random): Float {
        return if (rng.nextFloat() < prediction) 1f else 0f
    }

    override fun predict(instance: Instance) = -o.value(instance)

    override fun optimal(optimizer: Optimizer<NeuralNetworkObjective>, assumptions: IntCollection) = optimizer.optimize(o, assumptions)

    fun datasetInstances(): Sequence<Pair<Int, Instance>> {
        return InputStreamReader(javaClass.getResourceAsStream("/combo/demo/models/tc_dataset.txt")).buffered().lineSequence().map {
            val values = it.trim().split(" ")
            val y = values[0].toInt()
            val instance = SparseBitArray(model.problem.nbrValues)
            for (i in 1 until values.size) {
                val v = values[i]
                instance.set(v.substringBeforeLast(':').toInt())
            }
            y to instance
        }
    }

    private fun readBatchNormLayer(layer: Int): Layer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("/combo/demo/models/tc_layer$layer.txt")).readLines()
        val full = lines.map { line ->
            line.trim().split(" ").map {
                it.toDouble().toFloat()
            }.toFloatArray()
        }.map { vectors.vector(it) }
        return BatchNormalizationLayer(full[0], full[1], full[2], full[3], 1e-5f)
    }

    private fun readDenseLayerWeights(layer: Int, activation: Transform): Layer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("/combo/demo/models/tc_layer$layer.txt")).readLines()
        val full = lines.map { line ->
            line.trim().split(" ").map {
                it.toDouble().toFloat()
            }.toFloatArray()
        }
        val bias = vectors.vector(full.map { it[0] }.toFloatArray())
        val matrix = vectors.matrix(full.map {
            it.sliceArray(1 until it.size)
        }.toTypedArray())
        return DenseLayer(matrix, bias, activation)
    }
}