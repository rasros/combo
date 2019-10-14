package combo.demo.models

import combo.bandit.ParallelMode
import combo.bandit.dt.RandomForestBandit
import combo.bandit.univariate.BinomialPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.demo.Simulation
import combo.demo.SurrogateModel
import combo.math.*
import combo.model.Model
import combo.model.Model.Companion.model
import combo.model.Root
import combo.nn.*
import combo.sat.Instance
import combo.sat.SparseBitArray
import combo.sat.constraints.Relation
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.sat.set
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos
import java.io.InputStreamReader
import kotlin.collections.set
import kotlin.math.pow

fun main() {
    val tcs = TopCategorySurrogate()
    val optimizer = LocalSearch.Builder(tcs.model.problem)
            .sparse(true)
            .build()
    /*val t = measureTimeMillis {
        val literals = IntHashSet()
        tcs.model["Top-k", 5].collectLiterals(tcs.model.index, literals)
        val instance = tcs.optimal(optimizer, literals) ?: error("failed")
        println(tcs.model.toAssignment(instance))
        println(tcs.o.value(instance))
    }
    println(t.toFloat() / 1000)
    println(tcs.o.value(optimizer.witnessOrThrow()))
     */

    val p = tcs.datasetInstances().maxBy { tcs.o.value(it.second) }
    p!!
    println(p.first)
    println(tcs.o.value(p.second))

    val p2 = tcs.datasetInstances().minBy { tcs.o.value(it.second) }
    p2!!
    println(p2.first)
    println(tcs.o.value(p2.second))

    return

    val bandit = RandomForestBandit.Builder(tcs.model, ThompsonSampling(BinomialPosterior))
            .trees(200)
            .maxLiveNodes(20)
            .maxDepth(6)
            .trainAbsError(RunningVariance())
            .testAbsError(RunningVariance())
            .parallel()
            .mode(ParallelMode.BLOCKING)
            .build()

    val s = Simulation(tcs, bandit, horizon = 10_000)
    s.start()
    s.awaitCompletion()

    println(bandit.testAbsError)
    println(bandit.trainAbsError)
    println(s.expectedRewards.nbrSamples)
    println(s.expectedRewards.values().asSequence().sample(RunningVariance()).toString())
    println(s.duration.mean / 1_000_000)
    println((s.duration.standardDeviation / 1_000_000).pow(2))

}

fun categoryTreeModel(): Model {
    class Node(val value: String, val children: MutableList<Node> = ArrayList())

    val lines = InputStreamReader(Node::class.java.getResourceAsStream("tc_attributes_original.txt")).readLines()
    val categories = lines.subList(0, lines.size - 3)
    val lookup = HashMap<String, Node>()
    val rootNodes = ArrayList<Node>()
    for (category in categories) {
        val ourNode = lookup[category] ?: Node(category).apply { lookup[category] = this }
        if (!category.contains("/")) rootNodes.add(ourNode)
        else {
            val parentCategory = category.substringBeforeLast("/")
            val parentNode = lookup[parentCategory]
                    ?: Node(parentCategory).apply { lookup[parentCategory] = this }
            parentNode.children.add(ourNode)
        }
    }
    val tree = Node("/", rootNodes)

    fun toModel(category: Node, depth: Int = 0): Model =
            model(category.value) {
                if (category.value != "/") bool("${category.value}/Top-level")
                for (n in category.children) {
                    if (n.children.isEmpty()) bool(n.value)
                    else addModel(toModel(n, depth + 1))
                }
                if (depth == 0) {
                    scope.scopesAsSequence().forEach {
                        impose {
                            if (it.reifiedValue is Root) disjunction(*it.variables.toTypedArray())
                            else it.reifiedValue reifiedEquivalent disjunction(*it.variables.toTypedArray())
                        }
                    }
                    val leaves = scope.asSequenceWithScope()
                            .filterNot { it.second.children.any { c -> c.reifiedValue == it.first } }
                            .map { it.first }.toList().toTypedArray()
                    val k = int("Top-k", 1, 100)
                    impose { cardinality(k, Relation.EQ, *leaves) }
                    nominal("domain", "D1", "D2", "D3")
                }
            }

    return toModel(tree)
}

class TopCategorySurrogate(randomSeed: Int = nanos().toInt()) : SurrogateModel<FeedForwardRegressionObjective> {

    private val randomSequence = RandomSequence(randomSeed)
    val model = categoryTreeModel()
    val o = FeedForwardRegressionObjective(true,
            readInputDenseLayerWeights(),
            arrayOf(readBatchNormLayer(2),
                    readDenseLayerWeights(3, ReLU),
                    readBatchNormLayer(4),
                    readDenseLayerWeights(5, IdentityTransform)),
            BinarySoftmaxLayer())

    override fun reward(instance: Instance): Float {
        val p = o.value(instance)
        return if (randomSequence.next().nextFloat() < p) 1f else 0f
    }

    override fun predict(instance: Instance) = o.value(instance)

    override fun optimal(optimizer: Optimizer<FeedForwardRegressionObjective>, assumptions: IntCollection) = optimizer.optimize(o, assumptions)

    fun datasetInstances(): Sequence<Pair<Int, Instance>> {
        return InputStreamReader(javaClass.getResourceAsStream("tc_dataset.txt")).buffered().lineSequence().map {
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

    private fun readInputDenseLayerWeights(): InputLayer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("tc_layer1.txt")).readLines()
        val full = lines.map { line ->
            line.trim().split(" ").map {
                it.toDouble().toFloat()
            }.toFloatArray()
        }
        val bias = full.map { it[0] }.toFloatArray()
        val matrix = full.map {
            it.sliceArray(1 until it.size)
        }.toTypedArray()
        return InputLayer(matrix, bias, ReLU)
    }

    private fun readBatchNormLayer(layer: Int): HiddenLayer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("tc_layer$layer.txt")).readLines()
        val full = lines.map { line ->
            line.trim().split(" ").map {
                it.toDouble().toFloat()
            }.toFloatArray()
        }
        return BatchNormalizationLayer(full[0], full[1], full[2], full[3], 1e-5f)
    }

    private fun readDenseLayerWeights(layer: Int, activation: Transform): HiddenLayer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("tc_layer$layer.txt")).readLines()
        val full = lines.map { line ->
            line.trim().split(" ").map {
                it.toDouble().toFloat()
            }.toFloatArray()
        }
        val bias = full.map { it[0] }.toFloatArray()
        val matrix = full.map {
            it.sliceArray(1 until it.size)
        }.toTypedArray()
        return DenseLayer(matrix, bias, activation)
    }
}
