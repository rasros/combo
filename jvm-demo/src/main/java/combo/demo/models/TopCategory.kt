package combo.demo.models

import combo.bandit.BanditBuilder
import combo.bandit.RandomBandit
import combo.bandit.dt.DecisionTreeBandit
import combo.bandit.dt.RandomForestBandit
import combo.bandit.glm.BinomialVariance
import combo.bandit.glm.CovarianceLinearModel
import combo.bandit.glm.LinearBandit
import combo.bandit.glm.PrecisionLinearModel
import combo.bandit.nn.*
import combo.bandit.univariate.BinomialPosterior
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.demo.*
import combo.math.*
import combo.model.*
import combo.model.Model.Companion.model
import combo.sat.InitializerType
import combo.sat.Instance
import combo.sat.SparseBitArray
import combo.sat.constraints.Relation
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.sat.set
import combo.util.IntCollection
import combo.util.IntHashSet
import java.io.InputStreamReader
import kotlin.collections.set
import kotlin.random.Random

fun main() {
    val tcs = TopCategorySurrogate()

    val satSolver = LocalSearch.Builder(tcs.model.problem)
            .restarts(5)
            .pRandomWalk(0.01f)
            .initializer(InitializerType.NONE)
            .sparse(true)
            .maxConsideration(100)
            .fallbackCached()
            .maxSize(50)
            .build()

    val optimizer = LocalSearch.Builder(tcs.model.problem)
            .restarts(5)
            .pRandomWalk(0.01f)
            .initializer(InitializerType.NONE)
            .sparse(true)
            .maxConsideration(100)
            .cached()
            .pNew(0.1f)
            .pNewWithGuess(1.0f)
            .maxSize(50)
            .build()

    val doHyperSearch = false
    val chosen = "DT"

    if (doHyperSearch) {
        val parameters = mapOf(
                "DT" to DecisionTreeHyperParameters(DecisionTreeBandit.Builder(tcs.model, ThompsonSampling(BinomialPosterior)).optimizer(satSolver)),
                "RF" to RandomForestHyperParameters(RandomForestBandit.Builder(tcs.model, ThompsonSampling(BinomialPosterior)).optimizer(satSolver).trees(200)),
                "GLM_precision" to PrecisionLinearBanditHyperParameters(LinearBandit.Builder(tcs.model.problem).linearModel(PrecisionLinearModel.Builder(tcs.model.problem).family(BinomialVariance).build())),
                "GLM_covariance" to CovarianceLinearBanditHyperParameters(LinearBandit.Builder(tcs.model.problem).linearModel(CovarianceLinearModel.Builder(tcs.model.problem).family(BinomialVariance).build())),
                "NL" to NeuralLinearBanditHyperParameters(NeuralLinearBandit.Builder(DL4jNetwork.Builder(tcs.model.problem).output(LogitTransform))))
        println(chosen)
        hyperSearch(parameters[chosen]
                ?: error("bandit $chosen not found"), tcs, horizon = 100_000, repetitions = 10_000)
    } else {

        val bandits: Map<String, () -> BanditBuilder<*>> = mapOf(
                "Random" to { RandomBandit.Builder(tcs.model.problem).optimizer(satSolver) },
                "Oracle" to { OracleBandit.Builder(tcs).optimizer(optimizer) },
                "DT" to {
                    DecisionTreeBandit.Builder(tcs.model, ThompsonSampling(NormalPosterior))
                            .optimizer(optimizer)
                            .delta(6.3e-21f).deltaDecay(1.5e-9f).tau(.40f)
                },
                "RF" to {
                    RandomForestBandit.Builder(tcs.model, ThompsonSampling(NormalPosterior))
                            .viewedVariables(tcs.model.nbrVariables / 2)
                            .optimizer(optimizer)
                            .trees(200)
                            .delta(6.3e-21f).deltaDecay(1.5e-9f).tau(.40f)
                },
                "GLM_precision" to {
                    LinearBandit.Builder(tcs.model.problem)
                            .linearModel(PrecisionLinearModel.Builder(tcs.model.problem)
                                    .exploration(0.15f).regularizationFactor(3.2e-14f)
                                    .priorPrecision(4.0e2f)
                                    .build())
                            .optimizer(optimizer)
                },
                "GLM_covariance" to {
                    LinearBandit.Builder(tcs.model.problem)

                            .linearModel(CovarianceLinearModel.Builder(tcs.model.problem)
                                    .exploration(0.15f).regularizationFactor(2.0e-24f)
                                    .priorVariance(6.3e-4f)
                                    .build())
                            .optimizer(optimizer)
                },
                "NL" to {
                    NeuralLinearBandit.Builder(DL4jNetwork.Builder(tcs.model.problem)
                            .regularizationFactor(0.01f))
                            .baseVariance(0.1f)
                            .varianceUpdateDecay(0.9999f)
                            .weightUpdateDecay(0.999f)
                            .optimizer(LocalSearch.Builder(tcs.model.problem).fallbackCached().build())
                }
        )

        val banditLambda = bandits[chosen] ?: error("bandit not found $chosen")
        val rewards = runSimulation(banditLambda,
                tcs, horizon = 100_000,
                repetitions = 1000,
                fileName = "tc_data_$chosen.txt",
                contextProvider = object : ContextProvider {
                    override fun context(rng: Random): IntCollection {
                        val lits = IntHashSet()
                        tcs.model["Top-k", 5].collectLiterals(tcs.model.index, lits)
                        tcs.model["domain", "D${rng.nextInt(1, 4)}"].collectLiterals(tcs.model.index, lits)
                        return lits
                    }
                })
        println("$chosen $rewards")
    }
}

fun categoryTreeModel(): Model {
    class Node(val value: String, val children: MutableList<Node> = ArrayList())

    val lines = InputStreamReader(Node::class.java.getResourceAsStream("tc_attributes.txt")).readLines()
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

class TopCategorySurrogate() : SurrogateModel<NeuralNetworkObjective> {

    override val model = categoryTreeModel()
    val network = StaticNetwork(
            arrayOf(readDenseLayerWeights(1, RectifierTransform),
                    readBatchNormLayer(2),
                    readDenseLayerWeights(3, RectifierTransform),
                    readBatchNormLayer(4),
                    readDenseLayerWeights(5, IdentityTransform)),
            BinarySoftmaxLayer(), 100)
    val o = NeuralNetworkObjective(true, network)

    override fun reward(instance: Instance, prediction: Float, rng: Random): Float {
        return if (rng.nextFloat() < prediction) 1f else 0f
    }

    override fun predict(instance: Instance) = o.value(instance)

    override fun optimal(optimizer: Optimizer<NeuralNetworkObjective>, assumptions: IntCollection) = optimizer.optimize(o, assumptions)

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

    private fun readBatchNormLayer(layer: Int): Layer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("tc_layer$layer.txt")).readLines()
        val full = lines.map { line ->
            line.trim().split(" ").map {
                it.toDouble().toFloat()
            }.toFloatArray()
        }.map { vectors.vector(it) }
        return BatchNormalizationLayer(full[0], full[1], full[2], full[3], 1e-5f)
    }

    private fun readDenseLayerWeights(layer: Int, activation: Transform): Layer {
        val lines = InputStreamReader(javaClass.getResourceAsStream("tc_layer$layer.txt")).readLines()
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
