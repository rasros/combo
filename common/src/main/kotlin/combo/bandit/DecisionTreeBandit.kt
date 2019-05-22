package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.DataSample
import combo.math.GrowingDataSample
import combo.math.IntPermutation
import combo.math.VarianceEstimator
import combo.sat.*
import combo.sat.constraints.NumericConstraint
import combo.sat.solvers.LocalSearchSolver
import combo.sat.solvers.Solver
import combo.util.*
import kotlin.jvm.JvmOverloads
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * This bandit uses a univariate bandit algorithm, such as [combo.bandit.univariate.ThompsonSampling]. Each leaf
 * node is a bandit arm.
 *
 * This is a reproduction of the following paper: https://arxiv.org/pdf/1706.04687.pdf
 *
 * For more info on delta and tau parameters of the VFDT algorithm check out these resources:
 * https://github.com/ulmangt/vfml/blob/master/weka/src/main/java/weka/classifiers/trees/VFDT.java
 * http://kt.ijs.si/elena_ikonomovska/00-disertation.pdf
 *
 *
 * @param problem the problem contains the [Constraint]s and the number of variables.
 * @param banditPolicy the policy that the next bandit arm is selected with.
 * @param solver the solver will be used to generate [Instance]s that satisfy the constraints from the [Problem].
 */
@Suppress("UNCHECKED_CAST")
class DecisionTreeBandit<E : VarianceEstimator> @JvmOverloads constructor(
        val problem: Problem,
        val banditPolicy: BanditPolicy<E>,
        val solver: Solver = LocalSearchSolver(problem).apply {
            this.restarts = 10
            this.randomSeed = randomSeed
            this.timeout = 1000L
        }) : PredictionBandit<Array<LiteralData<E>>> {

    override var maximize: Boolean = true
    override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            solver.randomSeed = value
            field = value
        }
    private var rng = Random(randomSeed)

    override var rewards: DataSample = GrowingDataSample(20)
    override var trainAbsError: DataSample = GrowingDataSample(10)
    override var testAbsError: DataSample = GrowingDataSample(10)

    /**
     * VFDT parameter, this is the p-value threshold by which the best variable to split on must be better
     * than the second best. The lower the value the more data is required before a split is performed.
     */
    var delta: Float = 0.01f

    /**
     * [delta] will be multiplied by this once for each split. This can be used to limit the growth of the tree.
     */
    var deltaDecay: Float = 0.9f

    /**
     * VFDT parameter, this is the threshold with which the algorithm splits even if it is not proven best.
     * Set to 0.0 for never and to 1.0 for always.
     */
    var tau: Float = 0.05f

    /**
     * Total number of nodes that are permitted to build.
     */
    var maxNodes: Int = 10000

    /**
     * Only live nodes can be selected by [choose] method. By limiting the number of them we reduce
     * computation cost of [choose] but potentially limits optimal value.
     */
    var maxLiveNodes: Int = 500

    /**
     * The number of randomly selected variables that leaf nodes consider for splitting the tree further during [update].
     */
    var maxConsideration: Int = 500

    /**
     * How often we check whether a split can be performed during [update].
     */
    var updatePeriod: Int = 5

    /**
     * Minimum number of samples of both positive and negative values before a variable can be used for a split.
     */
    var minSamples = 10.0f

    /**
     * Blocked assumptions size. When the solver fails to generate an instance with assumptions, the assumptions are
     * added to a blocked circular queue. In this way the solver does not immediately try again with the same node.
     * This setting limits how big the circular buffer is.
     */
    var blockQueueSize = 2

    /**
     * TODO
     */
    var maxRestarts = 10

    private val liveNodes = ArrayList<LeafNode>()

    private val numericConstraints: Array<NumericConstraint> = problem.constraints
            .mapNotNull { it as? NumericConstraint }.toTypedArray()

    private var root: Node = AuditNode(EmptyCollection, banditPolicy.baseData(), Random(0)).also {
        // We use a fixed random seed here because this node is created before randomSeed can be set.
        liveNodes.add(it)
        banditPolicy.addArm(it.data)
    }

    private var nbrNodes = 1
    private var nbrAuditNodes = 1


    override fun importData(historicData: Array<LiteralData<E>>) {
        if (historicData.isEmpty()) return

        val sorted = historicData.sortedBy { if (maximize) -it.data.mean else it.data.mean }

        for (node in liveNodes) banditPolicy.removeArm(node.data)

        liveNodes.clear()
        val prior = banditPolicy.baseData()
        root = sorted[0].setLiterals[0].toIx().let { ix ->
            require(ix < problem.nbrVariables)
            SplitNode(ix, TerminalNode(collectionOf(ix.toLiteral(true)), prior),
                    TerminalNode(collectionOf(ix.toLiteral(false)), prior))
        }
        nbrNodes = 3
        nbrAuditNodes = 0

        fun createHistoricNode(setLiterals: IntCollection, total: E): LeafNode {
            val propagatedLiterals = IntHashSet(nullValue = 0)
            propagatedLiterals.addAll(setLiterals)
            problem.unitPropagation(propagatedLiterals)
            val nodeLiterals = collectionOf(*propagatedLiterals.toArray())

            val node = if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes &&
                    setLiterals.size < problem.nbrVariables) {
                AuditNode(nodeLiterals, total).also {
                    nbrAuditNodes++
                }
            } else TerminalNode(nodeLiterals, total)
            if (liveNodes.size + 1 <= maxLiveNodes) liveNodes.add(node)
            return node
        }

        for (node in sorted) {
            // This is done in three steps, 1) traverse the tree to the closest parent that the node should be added to,
            // 2) if needed, add more splits until the path is rebuilt, finally 3) add the leaf node to the parent.

            // 1) traverse the tree
            var r: SplitNode = root as SplitNode
            var stopIx = 0
            while (true) {
                r = if (r.pos is SplitNode && r.ix.toLiteral(true) in node.setLiterals) r.pos as SplitNode
                else if (r.neg is SplitNode && r.ix.toLiteral(false) in node.setLiterals) r.neg as SplitNode
                else break
                stopIx++
            }

            // 2) create more parents with temporary data
            while (stopIx + 1 < node.setLiterals.size) {
                val ix = node.setLiterals[stopIx + 1].toIx()
                require(ix < problem.nbrVariables)
                if (r.ix.toLiteral(true) == node.setLiterals[stopIx]) {
                    if (r.pos is LeafNode && (r.pos as LeafNode).data !== prior)
                        break
                    val setLiterals = node.setLiterals.sliceArray(0 until stopIx) + r.ix.toLiteral(true)
                    r.pos = SplitNode(ix, TerminalNode(collectionOf(*(setLiterals + ix.toLiteral(true))), prior),
                            TerminalNode(collectionOf(*(setLiterals + ix.toLiteral(false))), prior))
                    r = r.pos as SplitNode
                } else if (r.ix.toLiteral(false) == node.setLiterals[stopIx]) {
                    if (r.neg is LeafNode && (r.neg as LeafNode).data !== prior)
                        break
                    val setLiterals = node.setLiterals.sliceArray(0 until stopIx) + r.ix.toLiteral(false)
                    r.neg = SplitNode(ix, TerminalNode(collectionOf(*(setLiterals + ix.toLiteral(true))), prior),
                            TerminalNode(collectionOf(*(setLiterals + ix.toLiteral(false))), prior))
                    r = r.neg as SplitNode
                } else
                    break
                nbrNodes += 2
                stopIx++
            }

            // 3) add leaf node
            if (node.setLiterals[stopIx] == r.ix.toLiteral(true)) {
                if ((r.pos as? LeafNode)?.data === prior)
                    r.pos = createHistoricNode(collectionOf(*node.setLiterals), node.data.copy() as E)
                // else there is junk in the historicData and the current node is ignored
            } else if (node.setLiterals[stopIx] == r.ix.toLiteral(false)) {
                if ((r.neg as? LeafNode)?.data === prior)
                    r.neg = createHistoricNode(collectionOf(*node.setLiterals), node.data.copy() as E)
                // else there is junk in the historicData and the current node is ignored
            }
        }

        // Replace all TerminalNodes with data == prior with real node
        val queue = ArrayQueue<SplitNode>()
        queue.add(root as SplitNode)
        while (queue.size > 0) {
            val r = queue.remove()
            if (r.pos is SplitNode) queue.add(r.pos as SplitNode)
            else if ((r.pos is TerminalNode) && (r.pos as TerminalNode).data === prior)
                r.pos = createHistoricNode((r.pos as TerminalNode).setLiterals, banditPolicy.baseData())
            if (r.neg is SplitNode) queue.add(r.neg as SplitNode)
            else if ((r.neg is TerminalNode) && (r.neg as TerminalNode).data === prior)
                r.neg = createHistoricNode((r.neg as TerminalNode).setLiterals, banditPolicy.baseData())
        }

        for (node in liveNodes) banditPolicy.addArm(node.data)
    }

    override fun exportData(): Array<LiteralData<E>> {
        val data = ArrayList<LiteralData<E>>()
        val queue = ArrayList<Pair<IntArray, SplitNode>>()
        if (root is SplitNode) queue.add(EMPTY_INT_ARRAY to root as SplitNode)
        while (queue.isNotEmpty()) {
            val (lits, r) = queue.removeAt(queue.lastIndex)
            val posLits = lits + r.ix.toLiteral(true)
            val negLits = lits + r.ix.toLiteral(false)
            if (r.pos is SplitNode) queue.add(posLits to r.pos as SplitNode)
            else data += LiteralData(posLits, (r.pos as LeafNode).data)
            if (r.neg is SplitNode) queue.add(negLits to r.neg as SplitNode)
            else data += LiteralData(negLits, (r.neg as LeafNode).data)
        }
        return data.toTypedArray()
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        for (restart in 0 until maxRestarts) {
            banditPolicy.round(rng)
            val node = liveNodes.maxBy {
                when {
                    blocks(assumptions, it.blocked) -> Float.NEGATIVE_INFINITY
                    matches(it.setLiterals, assumptions) -> banditPolicy.evaluate(it.data, maximize, rng)
                    else -> Float.NEGATIVE_INFINITY
                }
            }
            val instance = when {
                node == null -> solver.witness(assumptions)
                assumptions.isEmpty() -> solver.witness(node.setLiterals)
                else -> solver.witness(assumptions.mutableCopy().apply { addAll(node.setLiterals) })
            }
            if (instance != null) return instance
            if (node != null && assumptions.isNotEmpty()) {
                node.blocked = node.blocked ?: CircleBuffer(blockQueueSize)
                node.blocked!!.add(assumptions)
            }
        }
        throw IterationsReachedException(maxRestarts)
    }

    override fun predict(instance: Instance) = root.findLeaf(instance).data.mean

    override fun train(instance: Instance, result: Float, weight: Float) {
        root = root.update(instance, result, weight)
    }

    private fun blocks(assumptions: IntCollection, blocker: CircleBuffer<IntCollection>?): Boolean {
        if (assumptions.isEmpty() || blocker == null) return false
        for (b in blocker) {
            if (b === assumptions) return true
            for (lit in b)
                if (lit !in assumptions) return false
        }
        return true
    }

    /**
     * Checks whether the assumptions can be satisfied by the conjunction formed by setLiterals.
     * Both arrays are assumed sorted.
     */
    private fun matches(setLiterals: IntCollection, assumptions: IntCollection): Boolean {
        if (assumptions.isEmpty()) return true
        for (lit in setLiterals)
            if (!lit in assumptions) return false
        return true
    }

    private abstract inner class Node {
        abstract fun findLeaf(instance: Instance): LeafNode
        abstract fun update(instance: Instance, result: Float, weight: Float): Node
    }

    private inner class SplitNode(val ix: Int, var pos: Node, var neg: Node) : Node() {

        override fun update(instance: Instance, result: Float, weight: Float): Node {
            if (instance[ix]) pos = pos.update(instance, result, weight)
            else neg = neg.update(instance, result, weight)
            return this
        }

        override fun findLeaf(instance: Instance) =
                if (instance[ix]) pos.findLeaf(instance)
                else neg.findLeaf(instance)
    }

    private abstract inner class LeafNode(val setLiterals: IntCollection, val data: E = banditPolicy.baseData())
        : Node() {
        override fun findLeaf(instance: Instance) = this
        var blocked: CircleBuffer<IntCollection>? = null
    }

    private inner class TerminalNode(setLiterals: IntCollection, data: E) : LeafNode(setLiterals, data) {
        override fun update(instance: Instance, result: Float, weight: Float) =
                this.apply { banditPolicy.update(data, result, weight) }
    }

    private inner class AuditNode(setLiterals: IntCollection, total: E, rng: Random = this.rng)
        : LeafNode(setLiterals, total) {

        var nViewed: Int = 0

        val auditedVariables = IntHashSet(nullValue = -1).let { set ->
            val itr = IntPermutation(problem.nbrVariables, rng).iterator()
            while (set.size < maxConsideration && itr.hasNext()) {
                val ix = itr.nextInt()
                if (ix.toLiteral(true) in setLiterals || ix.toLiteral(false) in setLiterals) continue
                set.add(ix)
            }

            // Make sure that the next MSB of int/floats are included
            for (c in numericConstraints) {
                for (lit in c.literals.max downTo c.literals.min) {
                    if (lit !in setLiterals && !lit !in setLiterals) {
                        set.add(lit.toIx())
                        break
                    }
                }
            }
            set.toArray()
        }
        val dataPos: Array<VarianceEstimator> = Array(auditedVariables.size) { banditPolicy.baseData() }
        val dataNeg: Array<VarianceEstimator> = Array(auditedVariables.size) { banditPolicy.baseData() }

        override fun update(instance: Instance, result: Float, weight: Float): Node {
            banditPolicy.update(data, result, weight)
            nViewed++
            for ((i, ix) in auditedVariables.withIndex()) {
                if (instance[ix]) banditPolicy.accept(dataPos[i] as E, result, weight)
                else banditPolicy.accept(dataNeg[i] as E, result, weight)
            }

            if (nViewed > updatePeriod) {
                var ig1 = 0.0f
                var ig2 = 0.0f
                var bestI = -1

                for (i in auditedVariables.indices) {
                    val ig = data.variance - variancePurity(i)
                    if (ig > ig1) {
                        bestI = i
                        ig2 = ig1
                        ig1 = ig
                    } else if (ig > ig2)
                        ig2 = ig
                }

                val eps = hoeffdingBound(delta, data.nbrWeightedSamples, setLiterals.size)
                if (bestI >= 0 && (ig2 / ig1 < 1 - eps || eps < tau)) {

                    val totalPos = dataPos[bestI] as E
                    val totalNeg = dataNeg[bestI] as E

                    val posHigh = dataPos[bestI].mean > dataNeg[bestI].mean
                    val inOrder = (posHigh && maximize) || (!posHigh && !maximize)

                    liveNodes.remove(this)
                    banditPolicy.removeArm(data)

                    nbrAuditNodes--
                    nbrNodes += 2

                    val pos: LeafNode
                    val neg: LeafNode

                    // The order in which the nodes are created give priority to nodes with best score
                    if (inOrder) {
                        pos = createLeafNode(setLiterals, auditedVariables[bestI].toLiteral(true), totalPos)
                        neg = createLeafNode(setLiterals, auditedVariables[bestI].toLiteral(false), totalNeg)
                    } else {
                        neg = createLeafNode(setLiterals, auditedVariables[bestI].toLiteral(false), totalNeg)
                        pos = createLeafNode(setLiterals, auditedVariables[bestI].toLiteral(true), totalPos)
                    }

                    banditPolicy.addArm(pos.data)
                    banditPolicy.addArm(neg.data)

                    return SplitNode(auditedVariables[bestI], pos, neg)
                }
                nViewed = 0
            }
            return this
        }

        override fun findLeaf(instance: Instance) = this

        fun variancePurity(index: Int): Float {
            val pos = dataPos[index]
            val neg = dataNeg[index]
            if (pos.nbrWeightedSamples < minSamples || neg.nbrWeightedSamples < minSamples)
                return Float.POSITIVE_INFINITY
            val nPos = pos.nbrWeightedSamples
            val nNeg = neg.nbrWeightedSamples
            val n = nPos + nNeg
            return (nNeg / n) * neg.variance + (nPos / n) * pos.variance
        }
    }

    private fun createLeafNode(setLiterals: IntCollection, splitLit: Int, total: E): LeafNode {
        val propagatedLiterals = IntHashSet(nullValue = 0)
        propagatedLiterals.addAll(setLiterals)
        propagatedLiterals.add(splitLit)
        problem.unitPropagation(propagatedLiterals)

        val nodeLiterals = collectionOf(*propagatedLiterals.toArray())

        return if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes
                && nodeLiterals.size < problem.nbrVariables) {
            AuditNode(nodeLiterals, total).also {
                nbrAuditNodes++
                liveNodes.add(it)
            }
        } else if (liveNodes.size < maxLiveNodes) {
            TerminalNode(nodeLiterals, total).also {
                liveNodes.add(it)
            }
        } else {
            // find target to replace worst live node
            val (ix, worstNode) = liveNodes.asSequence().mapIndexed { i, n -> i to n }.minBy { pair ->
                pair.second.data.mean.let { mean -> if (maximize) mean else -mean }
            }!!
            // if new node improves on worst node
            if ((maximize && worstNode.data.mean < total.mean) || (!maximize && worstNode.data.mean > total.mean)) {
                if (worstNode is AuditNode) {
                    // audit node should be replaced with dead node
                    var parent = root as SplitNode
                    while (true) {
                        parent = if (parent.ix.toLiteral(true) in worstNode.setLiterals) {
                            if (parent.pos is SplitNode) parent.pos as SplitNode
                            else break
                        } else {
                            if (parent.neg is SplitNode) parent.neg as SplitNode
                            else break
                        }
                    }
                    val deadNode = TerminalNode(worstNode.setLiterals, worstNode.data)
                    if (worstNode == parent.pos) parent.pos = deadNode
                    else parent.neg = deadNode
                    liveNodes[ix] = AuditNode(nodeLiterals, total)
                } else {
                    liveNodes[ix] = TerminalNode(nodeLiterals, total)
                }
                liveNodes[ix]
            } else TerminalNode(nodeLiterals, total)
        }
    }

    private fun hoeffdingBound(delta: Float, count: Float, depth: Int): Float {
        // R = 1 for both binary classification and with variance ratio
        //sqrt(/* R*R* */ ln(1.0f / delta) / (2.0f * count))
        return sqrt((-ln(delta) - depth * ln(deltaDecay)) / 2.0f / count)
    }
}