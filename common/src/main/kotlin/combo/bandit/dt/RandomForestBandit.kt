package combo.bandit.dt

import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.bandit.univariate.BanditPolicy
import combo.math.*
import combo.model.Model
import combo.model.Root
import combo.model.Variable
import combo.sat.Instance
import combo.sat.optimizers.LinearObjective
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.sat.toBoolean
import combo.sat.toIx
import combo.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

class RandomForestBandit<E : VarianceEstimator>(val parameters: ExtendedTreeParameters<E>, val trees: Array<DecisionTreeBandit<E>>)
    : PredictionBandit<ForestData<E>>, TreeParameters<E> by parameters {

    private val randomSequence = RandomSequence(randomSeed)

    override fun predict(instance: Instance): Float {
        var score = 0.0f
        for (t in trees)
            score += t.predict(instance)
        return score / trees.size
    }

    override fun train(instance: Instance, result: Float, weight: Float) {
        val rng = randomSequence.next()
        for (t in trees) {
            val n = rng.nextPoisson(1.0f)
            if (n > 0) t.train(instance, result, weight * n)
        }
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val propagated = if (propagateAssumptions && assumptions.isNotEmpty()) {
            val set = IntHashSet()
            set.addAll(assumptions)
            model.problem.unitPropagation(set)
            set
        } else assumptions
        val votesYes = ShortArray(model.problem.nbrValues)
        val votesNo = ShortArray(model.problem.nbrValues)
        for (b in trees) {
            val n = b.chooseNode(propagated) ?: continue
            for (lit in n.literals) {
                val ix = lit.toIx()
                if (lit.toBoolean()) votesYes[ix]++
                else votesNo[ix]++
            }
        }
        val rng = randomSequence.next()
        val weights = FloatArray(model.problem.nbrValues) {
            val a = votesYes[it] + 1
            val b = votesNo[it] + 1
            if (a == b) 0.0f
            //else {
            //val p = rng.nextBeta(a.toFloat(), b.toFloat())
            //2 * p - 1
            //}
            //else if (rng.nextBeta(a.toFloat(), b.toFloat()) > 0.5f) 1.0f
            //else -1.0f
            else {
                val p = a.toFloat() / (a + b).toFloat()
                2 * p - 1
            }
        }
        @Suppress("UNCHECKED_CAST")
        optimizer as Optimizer<LinearObjective>
        return optimizer.optimizeOrThrow(LinearObjective(maximize, weights), propagated)
    }

    override fun exportData(): ForestData<E> {
        return ForestData(List(trees.size) {
            trees[it].exportData()
        })
    }

    override fun importData(data: ForestData<E>) {
        for (i in 0 until min(data.trees.size, trees.size))
            trees[i].importData(data.trees[i])
    }

    class Builder<E : VarianceEstimator>(val model: Model, val banditPolicy: BanditPolicy<E>) : PredictionBanditBuilder<ForestData<E>> {

        private var trees: Int = 10
        private var optimizer: Optimizer<LinearObjective>? = null
        private var randomSeed: Int = nanos().toInt()
        private var maximize: Boolean = true
        private var splitMetric: SplitMetric =
                if (banditPolicy.baseData() is BinaryEstimator) GiniCoefficient
                else VarianceReduction
        private var delta: Float = 0.05f
        private var deltaDecay: Float = 0.5f
        private var tau: Float = 0.01f
        private var maxNodes: Int = 100
        private var maxLiveNodes: Int = 50
        private var viewedValues: Int =
                min(if (banditPolicy.baseData() is BinaryEstimator) sqrt(model.problem.nbrValues.toDouble()).roundToInt()
                else max(1, model.problem.nbrValues / 3), 50)
        private var viewedVariables: Int =
                if (banditPolicy.baseData() is BinaryEstimator) sqrt(model.nbrVariables.toDouble()).roundToInt()
                else max(1, model.nbrVariables / 3)
        private var splitPeriod: Int = 10
        private var minSamplesSplit: Float = banditPolicy.baseData().nbrWeightedSamples + 5.0f
        private var minSamplesLeaf: Float = banditPolicy.baseData().nbrWeightedSamples + 1.0f
        private var maxDepth: Int = 50
        private var rewards: DataSample = VoidSample
        private var trainAbsError: DataSample = VoidSample
        private var testAbsError: DataSample = VoidSample
        private var propagateAssumptions: Boolean = true
        private var blockQueueSize: Int = 2
        private var splitters = HashMap<Variable<*, *>, ValueSplitter>()
        private var filterMissingData: Boolean = true
        private var importedData: ForestData<E>? = null

        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }

        /** How many trees are in the assembly. */
        fun trees(trees: Int) = apply { this.trees = trees }

        /** Used to calculate max set coverage for votes. */
        fun optimizer(optimizer: Optimizer<LinearObjective>) = apply { this.optimizer = optimizer }

        /** Which split metric to use for deciding what variable to split on. */
        fun splitMetric(splitMetric: SplitMetric) = apply { this.splitMetric = splitMetric }

        /** P-value threshold that variable to split on must overcome relative to second best. Lower value requires more data. */
        fun delta(delta: Float) = apply { this.delta = delta }

        /** [delta] will be multiplied by this once for each split. Used to limit the growth of the tree. */
        fun deltaDecay(deltaDecay: Float) = apply { this.deltaDecay = deltaDecay }

        /** Threshold with which the algorithm splits even if it is not proven best (0 is never, 1 is always) */
        fun tau(tau: Float) = apply { this.tau = tau }

        /** Total number of nodes that are permitted to build. */
        fun maxNodes(maxNodes: Int) = apply { this.maxNodes = maxNodes }

        /** Only live nodes can be selected by choose method. Affects time taken to [choose]. */
        fun maxLiveNodes(maxLiveNodes: Int) = apply { this.maxLiveNodes = maxLiveNodes }

        /** The number of randomly selected values in the variables that leaf nodes consider for splitting the tree further during update. */
        fun viewedValues(viewedValues: Int) = apply { this.viewedValues = viewedValues }

        /** The number of allowed variables per tree in the ensemble. */
        fun viewedVariables(viewedVariables: Int) = apply { this.viewedVariables = viewedVariables }

        /** How often we check whether a split can be performed during update. */
        fun splitPeriod(splitPeriod: Int) = apply { this.splitPeriod = splitPeriod }

        /** Minimum number of samples in total of both positive and negative values before a variable can be used for a split. */
        fun minSamplesSplit(minSamplesSplit: Float) = apply { this.minSamplesSplit = minSamplesSplit }

        /** Minimum number of samples of both positive and negative values before a variable can be used for a split. */
        fun minSamplesLeaf(minSamplesLeaf: Float) = apply { this.minSamplesLeaf = minSamplesLeaf }

        /** Maximum depth the tree can grow to. */
        fun maxDepth(maxDepth: Int) = apply { this.maxDepth = maxDepth }

        /**The total absolute error obtained on a prediction before update. */
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }

        /** The total absolute error obtained on a prediction after update. */
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }

        /** Whether unit propagation before search is performed when assumptions are used. */
        fun propagateAssumptions(propagateAssumptions: Boolean) = apply { this.propagateAssumptions = propagateAssumptions }

        /** When the solver fails to generate an instance with assumptions, the assumptions are added to a blocked circular queue. */
        fun blockQueueSize(blockQueueSize: Int) = apply { this.blockQueueSize = blockQueueSize }

        /** Custom value splitters to use instead of default. */
        fun addSplitter(variable: Variable<*, *>, splitter: ValueSplitter) = apply { this.splitters[variable] = splitter }

        /** Whether data should be automatically filtered for update for variables that are undefined (otherwise counted as false). */
        fun filterMissingData(filterMissingData: Boolean) = apply { this.filterMissingData = filterMissingData }

        override fun importData(data: ForestData<E>) = apply { this.importedData = data }

        override fun parallel() = ParallelPredictionBandit.Builder(this)

        private fun sampleVariables(rng: Random, observed: TreeData<E>? = null): IntArray {
            fun variableIndices(variable: Variable<*, *>): IntList {
                var v = variable
                val myList = IntArrayList()
                while (v !is Root) {
                    myList.add(model.index.indexOf(v))
                    v = v.parent.canonicalVariable
                }
                return myList
            }

            val observedVariables = IntHashSet(nullValue = -1)
            if (observed != null) {
                val allSplitValues = IntHashSet(nullValue = -1)
                observed.asSequence().flatMap { node -> node.literals.mapArray { it.toIx() }.asSequence() }.forEach { allSplitValues.add(it) }
                allSplitValues.forEach {
                    for (vi in variableIndices(model.index.variableWithValue(it)))
                        observedVariables.add(vi)
                }
            }
            val perm = IntPermutation(model.nbrVariables, rng)
            for (vi in perm) {
                if (observedVariables.size >= viewedVariables) break
                for (vj in variableIndices(model.index.variable(vi)))
                    observedVariables.add(vj)
            }
            return observedVariables.toArray()
        }

        override fun build(): RandomForestBandit<E> {
            val treeParameters = ExtendedTreeParameters(model, banditPolicy,
                    optimizer ?: LocalSearch.Builder(model.problem).randomSeed(randomSeed)
                            .cached().pNew(1.0f).maxSize(10).build(),
                    randomSeed, maximize, rewards,
                    trainAbsError, testAbsError, splitMetric, delta, deltaDecay, tau, maxNodes, maxDepth, maxLiveNodes,
                    viewedValues, splitPeriod, minSamplesSplit, minSamplesLeaf,
                    propagateAssumptions, splitters, filterMissingData, blockQueueSize, 0)
            val rng = Random(randomSeed)
            val trees = if (importedData != null) {
                Array(importedData!!.size) {
                    val tree = importedData!![it]
                    DecisionTreeBandit(treeParameters, tree.buildTree(banditPolicy), sampleVariables(rng, tree))
                }
            } else {
                Array(trees) {
                    DecisionTreeBandit(treeParameters, null, sampleVariables(rng))
                }
            }
            return RandomForestBandit(treeParameters, trees)
        }
    }
}
