package combo.bandit

import combo.math.*
import combo.sat.*
import combo.sat.solvers.LocalSearchSolver
import combo.sat.solvers.Solver
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntSet
import combo.util.nanos
import kotlin.jvm.JvmOverloads
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * This bandit uses Thompson sampling where a Decision Tree approximates the full posterior distribution. Each leaf node
 * has an independent uni-variate distribution. The
 *
 * This is a reproduction of the following paper: https://arxiv.org/pdf/1706.04687.pdf
 *
 * For more info on delta and tau parameters of the VFDT algorithm check out these resources:
 * https://github.com/ulmangt/vfml/blob/master/weka/src/main/java/weka/classifiers/trees/VFDT.java
 * http://kt.ijs.si/elena_ikonomovska/00-disertation.pdf
 *
 *
 * @param problem the problem contains the [Constraint]s and the number of variables.
 * @param posterior the posterior family distribution to use for each labeling. Default is normal distribution.
 * @param prior the arms will start of with the value given here. Make sure that it results in valid parameters to
 * the posterior (eg. variance should be above zero for normal distribution).
 * @param historicData any historic data can be added in the map, this can be used to store and re-start the bandit.
 * @param solver the solver will be used to generate [Labeling]s that satisfy the constraints from the [Problem].
 */
class DecisionTreeBandit @JvmOverloads constructor(val problem: Problem,
                                                   val posterior: Posterior = GaussianPosterior,
                                                   val prior: VarianceStatistic = posterior.defaultPrior(),
                                                   historicData: Array<NodeData>? = null,
                                                   val solver: Solver = LocalSearchSolver(problem).apply {
                                                       this.restarts = 10
                                                       this.randomSeed = randomSeed
                                                       this.timeout = 1000L
                                                   }) : PredictionBandit {

    override var maximize: Boolean = true
    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed

    override var rewards: DataSample = GrowingDataSample(20)
    override var trainAbsError: DataSample = GrowingDataSample(10)
    override var testAbsError: DataSample = GrowingDataSample(10)

    /**
     * VFDT parameter, this is the p-value threshold by which the best variable to split on must be better
     * than the second best (default 0.05).
     */
    var delta: Double = 0.05

    /**
     * VFDT parameter, this is the threshold with which the algorithm splits even if it is not proven best.
     * Set to 1.0 for never and close to 0.0 for always (default is 0.1).
     */
    var tau: Double = 0.1

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
     * The number of randomly selected variables that leaf nodes consider for splitting the
     * posterior distribution further during [update].
     */
    var maxConsideration: Int = 500

    /**
     * How often we check whether a split can be performed during [update].
     */
    var updateRate: Int = 5

    private var root: Node

    private val liveNodes: MutableList<LeafNode>
    private var randomSequence = RandomSequence(nanos())

    private var nbrNodes = 1
    private var nbrAuditNodes = 1

    init {
        if (historicData != null && historicData.isNotEmpty()) {
            historicData.sortBy { if (maximize) -it.total.mean else it.total.mean }

            liveNodes = ArrayList()
            root = historicData[0].setLiterals[0].toIx().let { ix ->
                SplitNode(ix, BlockNode(intArrayOf(ix.toLiteral(true)), prior),
                        BlockNode(intArrayOf(ix.toLiteral(false)), prior))
            }
            nbrNodes = 3
            nbrAuditNodes = 0

            fun createHistoricNode(setLiterals: Literals, total: VarianceStatistic): LeafNode {
                val propagatedLiterals = IntSet().also {
                    it.addAll(setLiterals)
                    problem.unitPropagation(it)
                }.toArray().apply { sort() }

                val node = if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes &&
                        propagatedLiterals.size < problem.nbrVariables) {
                    AuditNode(propagatedLiterals, total).also {
                        nbrAuditNodes++
                    }
                } else BlockNode(propagatedLiterals, total)
                if (liveNodes.size + 1 <= maxLiveNodes) liveNodes.add(node)
                return node
            }

            for (node in historicData) {
                var r: SplitNode = root as SplitNode
                var stopIx = 0
                while (true) {
                    r = if (r.pos is SplitNode && r.ix.toLiteral(true) in node.setLiterals) r.pos as SplitNode
                    else if (r.neg is SplitNode && r.ix.toLiteral(false) in node.setLiterals) r.neg as SplitNode
                    else break
                    stopIx++
                }

                while (stopIx + 1 < node.setLiterals.size) {
                    val ix = node.setLiterals[stopIx + 1].toIx()
                    if (r.ix.toLiteral(true) == node.setLiterals[stopIx]) {
                        if (r.pos is LeafNode && (r.pos as LeafNode).total !== prior)
                            break
                        val setLiterals = node.setLiterals.sliceArray(0 until stopIx) + r.ix.toLiteral(true)
                        r.pos = SplitNode(ix, BlockNode((setLiterals + ix.toLiteral(true)), prior),
                                BlockNode((setLiterals + ix.toLiteral(false)), prior))
                        r = r.pos as SplitNode
                    } else if (r.ix.toLiteral(false) == node.setLiterals[stopIx]) {
                        if (r.neg is LeafNode && (r.neg as LeafNode).total !== prior)
                            break
                        val setLiterals = node.setLiterals.sliceArray(0 until stopIx) + r.ix.toLiteral(false)
                        r.neg = SplitNode(ix, BlockNode((setLiterals + ix.toLiteral(true)), prior),
                                BlockNode((setLiterals + ix.toLiteral(false)), prior))
                        r = r.neg as SplitNode
                    } else
                        break
                    nbrNodes += 2
                    stopIx++
                }

                if (node.setLiterals[stopIx] == r.ix.toLiteral(true)) {
                    if ((r.pos as? LeafNode)?.total === prior)
                        r.pos = createHistoricNode(node.setLiterals.sortedArray(), node.total.copy())
                    // else there is junk in the historicData and the current node is ignored
                } else if (node.setLiterals[stopIx] == r.ix.toLiteral(false)) {
                    if ((r.neg as? LeafNode)?.total === prior)
                        r.neg = createHistoricNode(node.setLiterals.sortedArray(), node.total.copy())
                    // else there is junk in the historicData and the current node is ignored
                }
            }

            // Replace all BlockNodes with total == prior with real node
            val queue = ArrayList<SplitNode>()
            queue.add(root as SplitNode)
            while (queue.isNotEmpty()) {
                val r = queue.removeAt(queue.lastIndex)
                if (r.pos is SplitNode) queue.add(r.pos as SplitNode)
                else if ((r.pos is BlockNode) && (r.pos as BlockNode).total === prior)
                    r.pos = createHistoricNode((r.pos as BlockNode).setLiterals, prior.copy())
                if (r.neg is SplitNode) queue.add(r.neg as SplitNode)
                else if ((r.neg is BlockNode) && (r.neg as BlockNode).total === prior)
                    r.neg = createHistoricNode((r.neg as BlockNode).setLiterals, prior.copy())
            }

        } else {
            root = AuditNode(EMPTY_INT_ARRAY, prior.copy())
            liveNodes = arrayListOf(root as AuditNode)
        }
    }

    /**
     * Return all leaf nodes to use for external storage. They can be used to create a new [DecisionTreeBandit] that
     * continues optimizing through the historicData constructor parameter. Note that the order of the literals in
     * [NodeData] should not be changed. The order of the returned array does not matter.
     */
    fun exportData(): Array<NodeData> {
        val data = ArrayList<NodeData>()
        val queue = ArrayList<Pair<IntArray, SplitNode>>()
        if (root is SplitNode) queue.add(EMPTY_INT_ARRAY to root as SplitNode)
        while (queue.isNotEmpty()) {
            val (lits, r) = queue.removeAt(queue.lastIndex)
            val posLits = lits + r.ix.toLiteral(true)
            val negLits = lits + r.ix.toLiteral(false)
            if (r.pos is SplitNode) queue.add(posLits to r.pos as SplitNode)
            else data += NodeData(posLits, (r.pos as LeafNode).total)
            if (r.neg is SplitNode) queue.add(negLits to r.neg as SplitNode)
            else data += NodeData(negLits, (r.neg as LeafNode).total)
        }
        return data.toTypedArray()
    }

    override fun chooseOrThrow(assumptions: IntArray): Labeling {
        val rng = randomSequence.next()
        val node = if (maximize) {
            liveNodes.maxBy {
                if (matches(it.setLiterals, assumptions)) posterior.sample(rng, it.total)
                else Double.NEGATIVE_INFINITY
            }
        } else {
            liveNodes.minBy {
                if (matches(it.setLiterals, assumptions)) posterior.sample(rng, it.total)
                else Double.POSITIVE_INFINITY
            }
        }
        return if (node == null) solver.witnessOrThrow(assumptions)
        else {
            return try {
                // Assumptions did not match node setLiterals
                solver.witnessOrThrow(assumptions + node.setLiterals)
            } catch (e: ValidationException) {
                solver.witnessOrThrow(assumptions)
            }
        }
    }

    override fun predict(labeling: Labeling) = root.findLeaf(labeling).total.mean

    override fun train(labeling: Labeling, result: Double, weight: Double) {
        root = root.update(labeling, result, weight)
    }

    /**
     * Checks whether the assumptions can be satisfied by the conjunction formed by setLiterals.
     * Both arrays are assumed sorted.
     */
    private fun matches(setLiterals: Literals, assumptions: Literals): Boolean {
        var i = 0
        var j = 0
        while (i < setLiterals.size && j < assumptions.size) {
            while (setLiterals[i].toIx() < assumptions[j].toIx() && i + 1 < setLiterals.size) i++
            while (assumptions[j].toIx() < setLiterals[i].toIx() && j + 1 < assumptions.size) {
                j++
                require(assumptions[j - 1].toIx() < assumptions[j].toIx()) { "Literals in assumption must be sorted." }
            }
            val l1 = setLiterals[i]
            val l2 = assumptions[j]
            when {
                l1.toIx() == l2.toIx() -> {
                    if (l1 != l2) return false
                    i++
                    j++
                }
                l1 < l2 -> i++
                else -> j++
            }
        }
        return true
    }

    private interface Node {
        fun findLeaf(labeling: Labeling): LeafNode
        fun update(labeling: Labeling, result: Double, weight: Double): Node
    }

    private inner class SplitNode(val ix: Ix, var pos: Node, var neg: Node) : Node {

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {
            if (labeling[ix]) pos = pos.update(labeling, result, weight)
            else neg = neg.update(labeling, result, weight)
            return this
        }

        override fun findLeaf(labeling: Labeling) =
                if (labeling[ix]) pos.findLeaf(labeling)
                else neg.findLeaf(labeling)
    }

    private abstract inner class LeafNode(val setLiterals: Literals,
                                          val total: VarianceStatistic = prior.copy()) : Node {
        override fun findLeaf(labeling: Labeling) = this
    }

    private inner class BlockNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {
        override fun update(labeling: Labeling, result: Double, weight: Double) =
                this.apply { posterior.update(total, result, weight) }
    }

    private inner class AuditNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {

        var nViewed: Int = 0

        val ixs = IntSet().let { set ->
            val itr = IntPermutation(problem.nbrVariables, randomSequence.next()).iterator()
            while (set.size < maxConsideration && itr.hasNext()) {
                val ix = itr.nextInt()
                if (ix.toLiteral(true) in setLiterals || ix.toLiteral(false) in setLiterals) continue
                set.add(ix)
            }
            set.toArray().apply { sort() }
        }
        val dataPos: Array<VarianceStatistic> = Array(ixs.size) { prior.copy() }
        val dataNeg: Array<VarianceStatistic> = Array(ixs.size) { prior.copy() }

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {
            posterior.update(total, result, weight)
            nViewed++
            for ((i, ix) in ixs.withIndex()) {
                if (labeling[ix]) posterior.update(dataPos[i], result, weight)
                else posterior.update(dataNeg[i], result, weight)
            }

            if (nViewed > updateRate) {
                var ig1 = 0.0
                var ig2 = 0.0
                var bestI = -1

                for (i in ixs.indices) {
                    val ig = total.variance - variancePurity(i)
                    if (ig > ig1) {
                        bestI = i
                        ig2 = ig1
                        ig1 = ig
                    } else if (ig > ig2)
                        ig2 = ig
                }

                val eps = hoeffdingBound(delta, total.nbrWeightedSamples)
                if (bestI >= 0 && dataPos[bestI].nbrWeightedSamples > prior.nbrWeightedSamples &&
                        dataNeg[bestI].nbrWeightedSamples > prior.nbrWeightedSamples &&
                        (ig2 / ig1 < 1 - eps || eps < tau)) {

                    val totalPos: VarianceStatistic = dataPos[bestI]
                    val totalNeg: VarianceStatistic = dataNeg[bestI]
                    val literalsPos: Literals = (setLiterals + ixs[bestI].toLiteral(true))
                    val literalsNeg: Literals = (setLiterals + ixs[bestI].toLiteral(false))

                    val posHigh = dataPos[bestI].mean > dataNeg[bestI].mean
                    val inOrder = (posHigh && maximize) || (!posHigh && !maximize)

                    liveNodes.remove(this)

                    nbrAuditNodes--
                    nbrNodes += 2

                    val pos: LeafNode
                    val neg: LeafNode
                    if (inOrder) {
                        pos = createNode(literalsPos, totalPos)
                        neg = createNode(literalsNeg, totalNeg)
                    } else {
                        neg = createNode(literalsNeg, totalNeg)
                        pos = createNode(literalsPos, totalPos)
                    }

                    return SplitNode(ixs[bestI], pos, neg)
                }
                nViewed = 0
            }
            return this
        }

        override fun findLeaf(labeling: Labeling) = this

        fun variancePurity(index: Int): Double {
            val pos = dataPos[index]
            val neg = dataNeg[index]
            if (pos.nbrWeightedSamples < 2 || neg.nbrWeightedSamples < 2)
                return Double.POSITIVE_INFINITY
            val nPos = pos.nbrWeightedSamples
            val nNeg = neg.nbrWeightedSamples
            val n = nPos + nNeg
            return (nNeg / n) * neg.variance + (nPos / n) * pos.variance
        }
    }

    private fun createNode(setLiterals: Literals, total: VarianceStatistic): LeafNode {
        val propagatedLiterals = IntSet().also {
            it.addAll(setLiterals)
            problem.unitPropagation(it)
        }.toArray().apply { sort() }

        return if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes
                && propagatedLiterals.size < problem.nbrVariables) {
            AuditNode(propagatedLiterals, total).also {
                nbrAuditNodes++
                liveNodes.add(it)
            }
        } else if (liveNodes.size < maxLiveNodes) {
            BlockNode(propagatedLiterals, total).also {
                liveNodes.add(it)
            }
        } else {
            // find target to replace worst live node
            val (ix, worstNode) = liveNodes.asSequence().mapIndexed { i, n -> i to n }.minBy { pair ->
                pair.second.total.mean.let { mean -> if (maximize) mean else -mean }
            }!!
            // if new node improves on worst node
            if ((maximize && worstNode.total.mean < total.mean) || (!maximize && worstNode.total.mean > total.mean)) {
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
                    val deadNode = BlockNode(worstNode.setLiterals, worstNode.total)
                    if (worstNode == parent.pos) parent.pos = deadNode
                    else parent.neg = deadNode
                    liveNodes[ix] = AuditNode(propagatedLiterals, total)
                } else {
                    liveNodes[ix] = BlockNode(propagatedLiterals, total)
                }
                liveNodes[ix]
            } else BlockNode(propagatedLiterals, total)
        }
    }

    private fun hoeffdingBound(delta: Double, count: Double): Double {
        // R = 1 for both binary classification and with variance ratio
        return sqrt(/* R*R* */ ln(1.0 / delta) / (2.0 * count))
    }
}

/**
 * This class holds the data in the leaf nodes. The order of the literals in [setLiterals] is significant and cannot
 * be changed.
 */
class NodeData(val setLiterals: Literals, val total: VarianceStatistic)

