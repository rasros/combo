package combo.bandit.dt

import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.bandit.univariate.BanditPolicy
import combo.bandit.univariate.Greedy
import combo.math.*
import combo.model.Model
import combo.model.Variable
import combo.sat.*
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.sat.optimizers.SatObjective
import combo.util.*
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

/**
 * This bandit uses a univariate bandit algorithm, such as [combo.bandit.univariate.ThompsonSampling]. Each leaf
 * node is a bandit arm.
 *
 * This is a generalization of the following paper: https://arxiv.org/pdf/1706.04687.pdf
 *
 * For more info on delta and tau parameters of the VFDT algorithm check out these resources:
 * https://github.com/ulmangt/vfml/blob/master/weka/src/main/java/weka/classifiers/trees/VFDT.java
 * http://kt.ijs.si/elena_ikonomovska/00-disertation.pdf
 */
class DecisionTreeBandit(val parameters: ExtendedTreeParameters, root: Node?, val allowedVariables: IntArray?)
    : PredictionBandit<TreeData>, TreeParameters by parameters {

    private val randomSequence = RandomSequence(randomSeed)
    private val step = AtomicLong()

    private val liveNodes = ArrayList<LeafNode>()
    private var nbrNodes = 0
    private var nbrAuditNodes = 0

    private var root: Node = if (root != null) {
        // Convert TerminalNodes to AuditNodes as required
        if (root is LeafNode) {
            createLeafNode(EmptyCollection, root.data)
        } else {
            val queue = ArrayQueue<SplitNode>()
            queue.add(root as SplitNode)
            while (queue.size > 0) {
                val r = queue.remove()
                if (r.pos is LeafNode) r.pos = createLeafNode((r.pos as LeafNode).literals, (r.pos as LeafNode).data)
                else queue.add(r.pos as SplitNode)
                if (r.neg is LeafNode) r.neg = createLeafNode((r.neg as LeafNode).literals, (r.neg as LeafNode).data)
                else queue.add(r.neg as SplitNode)
            }
            root
        }
    } else {
        createLeafNode(EmptyCollection, banditPolicy.baseData())
    }

    private fun optNode(assumptions: IntCollection, policy: BanditPolicy, t: Long): LeafNode? {
        val rng = randomSequence.next()
        val n = liveNodes.maxBy {
            when {
                it.blocks(assumptions) -> Float.NEGATIVE_INFINITY
                it.matches(assumptions) -> policy.evaluate(it.data, t, maximize, rng)
                else -> Float.NEGATIVE_INFINITY
            }
        }
        return if (n?.blocks(assumptions) != false || !n.matches(assumptions)) null
        else n
    }

    fun optimalNode(assumptions: IntCollection = EmptyCollection): LeafNode? = optNode(assumptions, Greedy, step.get())
    fun chooseNode(assumptions: IntCollection = EmptyCollection): LeafNode? = optNode(assumptions, banditPolicy, step.getAndIncrement())

    private fun opt(optimal: Boolean, assumptions: IntCollection): Instance {
        var propagated: IntHashSet? = null
        for (restart in 0 until parameters.maxRestarts) {
            if (propagateAssumptions && assumptions.isNotEmpty()) {
                propagated = IntHashSet()
                propagated.addAll(assumptions)
                model.problem.unitPropagation(propagated)
            }
            val node = if (optimal) optimalNode(propagated ?: assumptions)
            else chooseNode(propagated ?: assumptions)
            val instance = when {
                node == null -> optimizer.witness(propagated ?: assumptions)
                assumptions.isEmpty() -> optimizer.witness(node.literals)
                else -> optimizer.witness((propagated
                        ?: assumptions).mutableCopy(nullValue = 0).apply { addAll(node.literals) })
            }
            if (instance != null)
                return instance
            if (node != null && assumptions.isNotEmpty() && node.blocked != null) {
                node.blocked.add(randomSequence.next(), assumptions)
            }
        }
        throw IterationsReachedException(parameters.maxRestarts)
    }

    override fun chooseOrThrow(assumptions: IntCollection) = opt(false, assumptions)
    override fun optimalOrThrow(assumptions: IntCollection) = opt(true, assumptions)

    override fun predict(instance: Instance) = root.findLeaf(instance).data.mean

    override fun train(instance: Instance, result: Float, weight: Float) {
        root = root.update(instance, result, weight)
    }

    private inner class AuditNode(setLiterals: IntCollection, total: VarianceEstimator)
        : LeafNode(setLiterals, total, if (blockQueueSize > 0) RandomCache(blockQueueSize) else null) {

        var nViewed: Int = 0

        val auditedValues = IntHashSet(nullValue = -1).let { set ->

            val propagatedLiterals = IntHashSet(nullValue = 0)
            propagatedLiterals.addAll(setLiterals)
            model.problem.unitPropagation(propagatedLiterals)

            // Both cases work the same
            // Step 1) loop through variables and add one value per variable
            // Step 2) add values (from allowed variables) until viewedValues is met
            val rng = randomSequence.next()
            if (allowedVariables != null) {
                val myVariables = IntHashSet(nullValue = -1)
                myVariables.addAll(allowedVariables)
                while (myVariables.isNotEmpty() && set.size < viewedValues) {
                    val itr = myVariables.permutation(rng)
                    while (itr.hasNext() && set.size < viewedValues) {
                        val variableIndex = itr.nextInt()
                        val variable = model.index.variable(variableIndex)
                        val splitter = splitters[variable] ?: defaultValueSplitter(model, variable)
                        val valueToSplit = splitter.nextSplit(propagatedLiterals, set, rng)
                        if (valueToSplit >= 0) set.add(valueToSplit)
                        else {
                            myVariables.remove(variableIndex)
                            break
                        }
                    }
                }
            } else {
                val itr = IntPermutation(model.index.nbrVariables, rng).iterator()
                while (itr.hasNext() && set.size < viewedValues) {
                    val variable = model.index.variable(itr.nextInt())
                    val splitter = splitters[variable] ?: defaultValueSplitter(model, variable)
                    val valueToSplit = splitter.nextSplit(propagatedLiterals, set, rng)
                    if (valueToSplit >= 0) set.add(valueToSplit)
                }
                val valueIterator = IntPermutation(model.problem.nbrValues, rng).iterator()
                while (set.size < viewedValues && valueIterator.hasNext()) {
                    val value = valueIterator.nextInt()
                    if (value.toLiteral(true) !in propagatedLiterals && value.toLiteral(false) !in propagatedLiterals)
                        set.add(value)
                }
            }
            set.toArray()
        }

        val dataPos = Array(auditedValues.size) { banditPolicy.baseData() }
        val dataNeg = Array(auditedValues.size) { banditPolicy.baseData() }

        override fun update(instance: Instance, result: Float, weight: Float): Node {
            banditPolicy.update(data, result, weight)
            nViewed++
            for ((i, ix) in auditedValues.withIndex()) {
                if (instance.isSet(ix)) banditPolicy.accept(dataPos[i], result, weight)
                else {
                    val reifiedLiteral = if (parameters.reifiedLiterals == null) 0 else parameters.reifiedLiterals[ix]
                    if (reifiedLiteral == 0 || instance.literal(reifiedLiteral.toIx()) == reifiedLiteral)
                        banditPolicy.accept(dataNeg[i], result, weight)
                }
            }

            if (nViewed % splitPeriod == 0) {
                val total = dataPos[0].combine(dataNeg[0])
                val (sm1, sm2, bestI) = splitMetric.split(total, dataPos, dataNeg, minSamplesSplit, minSamplesLeaf)
                val eps = hoeffdingBound(delta, data.nbrWeightedSamples, literals.size)
                if (bestI >= 0 && (sm2 / sm1 < 1 - eps || eps < tau)) {

                    val totalPos = dataPos[bestI]
                    val totalNeg = dataNeg[bestI]

                    val posHigh = dataPos[bestI].mean > dataNeg[bestI].mean
                    val inOrder = (posHigh && maximize) || (!posHigh && !maximize)

                    liveNodes.remove(this)
                    banditPolicy.removeArm(data)
                    nbrAuditNodes--
                    nbrNodes--

                    val pos: LeafNode
                    val neg: LeafNode
                    val posLiterals = literals.mutableCopy(nullValue = 0).apply { add(auditedValues[bestI].toLiteral(true)) }
                    val negLiterals = literals.mutableCopy(nullValue = 0).apply { add(auditedValues[bestI].toLiteral(false)) }

                    // The order in which the nodes are created give priority to nodes with best score
                    if (inOrder) {
                        pos = createLeafNode(posLiterals, totalPos)
                        neg = createLeafNode(negLiterals, totalNeg)
                    } else {
                        neg = createLeafNode(negLiterals, totalNeg)
                        pos = createLeafNode(posLiterals, totalPos)
                    }
                    return SplitNode(auditedValues[bestI], pos, neg)
                }
            }

            return this
        }

        override fun findLeaf(instance: Instance) = this

        private fun hoeffdingBound(delta: Float, count: Float, depth: Int): Float {
            // R = 1 for both binary classification and with variance ratio
            //sqrt(/* R*R* */ ln(1.0f / delta) / (2.0f * count))
            return sqrt((-ln(delta) - depth * ln(deltaDecay)) / 2.0f / count)
        }
    }

    /**
     * Add and index new leaf node. It needs to be added to the correct split node by the caller.
     */
    private tailrec fun createLeafNode(setLiterals: IntCollection, total: VarianceEstimator): LeafNode {
        nbrNodes++
        return if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes
                && setLiterals.size < model.problem.nbrValues && setLiterals.size < maxDepth) {
            val node: AuditNode
            try {
                node = AuditNode(setLiterals, total)
            } catch (e: UnsatisfiableException) {
                throw e
            }
            val leafNode = if (node.auditedValues.isNotEmpty()) {
                nbrAuditNodes++
                node
            } else TerminalNode(banditPolicy, setLiterals, total, blockQueueSize)
            banditPolicy.addArm(total)
            liveNodes.add(leafNode)
            leafNode
        } else if (liveNodes.size < maxLiveNodes) {
            TerminalNode(banditPolicy, setLiterals, total, blockQueueSize).also {
                banditPolicy.addArm(total)
                liveNodes.add(it)
            }
        } else {
            // find target to replace worst live node
            val (ix, worstNode) = liveNodes.asSequence().mapIndexed { i, n -> i to n }.minBy { pair ->
                pair.second.data.mean.let { mean -> if (maximize) mean else -mean }
            }!!
            if ((maximize && worstNode.data.mean < total.mean) || (!maximize && worstNode.data.mean > total.mean)) {
                // remove worstNode and try again
                banditPolicy.removeArm(worstNode.data)
                if (worstNode is AuditNode) nbrAuditNodes--
                var parent = root as SplitNode
                while (true) {
                    parent = if (parent.ix.toLiteral(true) in worstNode.literals) {
                        if (parent.pos is SplitNode) parent.pos as SplitNode
                        else break
                    } else {
                        if (parent.neg is SplitNode) parent.neg as SplitNode
                        else break
                    }
                }
                val deadNode = TerminalNode(banditPolicy, worstNode.literals, worstNode.data, 0)
                if (worstNode === parent.pos) parent.pos = deadNode
                else parent.neg = deadNode
                liveNodes.removeAt(ix)
                createLeafNode(setLiterals, total)
            } else TerminalNode(banditPolicy, setLiterals, total, 0)
        }
    }

    override fun importData(data: TreeData) {
        for (d in data.nodes) {
            root.findLeaves(d.literals).forEach {
                banditPolicy.removeArm(it.data)
                val combined = it.data.combine(d.data)
                it.data = combined
                banditPolicy.addArm(combined)
            }
        }
    }

    override fun exportData(): TreeData {
        val data = ArrayList<NodeData>()
        val queue = ArrayList<Pair<IntArray, SplitNode>>()
        if (root is SplitNode) queue.add(EMPTY_INT_ARRAY to root as SplitNode)
        while (queue.isNotEmpty()) {
            val (lits, r) = queue.removeAt(queue.lastIndex)
            val posLits = lits + r.ix.toLiteral(true)
            val negLits = lits + r.ix.toLiteral(false)
            if (r.pos is SplitNode) queue.add(posLits to r.pos as SplitNode)
            else data += NodeData(posLits, (r.pos as LeafNode).data)
            if (r.neg is SplitNode) queue.add(negLits to r.neg as SplitNode)
            else data += NodeData(negLits, (r.neg as LeafNode).data)
        }
        return TreeData(data)
    }

    /**
     * Problem and bandit policy are mandatory
     */
    class Builder(val model: Model, val banditPolicy: BanditPolicy) : PredictionBanditBuilder<TreeData> {

        private var optimizer: Optimizer<SatObjective>? = null
        private var randomSeed: Int = nanos().toInt()
        private var maximize: Boolean = true
        private var splitMetric: SplitMetric =
                if (banditPolicy.baseData() is BinaryEstimator) GiniCoefficient
                else VarianceReduction
        private var delta: Float = 0.5f
        private var deltaDecay: Float = 0.5f
        private var tau: Float = 0.1f
        private var maxNodes: Int = 500
        private var maxLiveNodes: Int = 100
        private var viewedValues: Int = min(model.nbrVariables * 2, 100)
        private var splitPeriod: Int = 10
        private var minSamplesSplit: Float = banditPolicy.baseData().nbrWeightedSamples + 5.0f
        private var minSamplesLeaf: Float = banditPolicy.baseData().nbrWeightedSamples + 1.0f
        private var maxDepth: Int = 50
        private var rewards: DataSample = VoidSample
        private var trainAbsError: DataSample = VoidSample
        private var testAbsError: DataSample = VoidSample
        private var propagateAssumptions: Boolean = true
        private var blockQueueSize: Int = 2
        private var maxRestarts: Int = 5
        private var splitters = HashMap<Variable<*, *>, ValueSplitter>()
        private var filterMissingData: Boolean = true

        private var root: Node? = null

        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }

        /** Used to generate complete [Instance] from partial literals at leaf nodes. */
        fun optimizer(optimizer: Optimizer<SatObjective>) = apply { this.optimizer = optimizer }

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

        /** How often we check whether a split can be performed during update. */
        fun splitPeriod(splitPeriod: Int) = apply { this.splitPeriod = splitPeriod }

        /** Minimum number of samples in total of both positive and negative values before a variable can be used for a split. */
        fun minSamplesSplit(minSamplesSplit: Float) = apply { this.minSamplesSplit = minSamplesSplit }

        /** Minimum number of samples of both positive and negative values before a variable can be used for a split. */
        fun minSamplesLeaf(minSamplesLeaf: Float) = apply { this.minSamplesLeaf = minSamplesLeaf }

        /** Maximum depth the tree can grow to. */
        fun maxDepth(maxDepth: Int) = apply { this.maxDepth = maxDepth }

        /** Whether unit propagation before search is performed when assumptions are used. */
        fun propagateAssumptions(propagateAssumptions: Boolean) = apply { this.propagateAssumptions = propagateAssumptions }

        /** When the solver fails to generate an instance with assumptions, the assumptions are added to a blocked circular queue. */
        fun blockQueueSize(blockQueueSize: Int) = apply { this.blockQueueSize = blockQueueSize }

        /** Max restart attempts to [choose] (only relevant with assumptions). */
        fun maxRestarts(maxRestarts: Int) = apply { this.maxRestarts = maxRestarts }

        /** Custom value splitters to use instead of default. */
        fun addSplitter(variable: Variable<*, *>, splitter: ValueSplitter) = apply { this.splitters[variable] = splitter }

        /** Whether data should be automatically filtered for update for variables that are undefined (otherwise counted as false). */
        fun filterMissingData(filterMissingData: Boolean) = apply { this.filterMissingData = filterMissingData }

        override fun importData(data: TreeData) = apply { this.root = data.buildTree(banditPolicy) }

        override fun parallel() = ParallelPredictionBandit.Builder(this)

        override fun build(): DecisionTreeBandit {
            val parameters = ExtendedTreeParameters(model, banditPolicy,
                    optimizer ?: LocalSearch.Builder(model.problem).randomSeed(randomSeed).fallbackCached().build(),
                    randomSeed, maximize, rewards, trainAbsError, testAbsError, splitMetric, delta, deltaDecay,
                    tau, maxNodes, maxDepth, maxLiveNodes, viewedValues, splitPeriod, minSamplesSplit, minSamplesLeaf,
                    propagateAssumptions, splitters, filterMissingData, blockQueueSize, maxRestarts)
            return DecisionTreeBandit(parameters, root, null)
        }
    }
}
