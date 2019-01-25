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
 * @param problem
 * @param maximize
 * @param randomSeed
 * @param solver
 * @param posterior
 * @param prior
 * @param historicData
 * @param delta VFDT parameter, this is the p-value threshold by which the best variable to split on must be better
 * than the second best (default 0.05).
 * @param tau VFDT parameter, this is the threshold with which the algorithm splits even if it is not proven best.
 * Set to 1.0 for never and close to 0.0 for always (default is 0.1).
 * @param maxNodes total number of nodes that are permitted to build.
 * @param maxLiveNodes only live nodes can be selected by [choose] method. By limiting the number of them we reduce
 * computation cost of [choose] but potentially limits optimal value.
 * @param maxConsideration the number of randomly selected variables that leaf nodes consider for splitting the
 * posterior distribution further during [update].
 * @param updateRate how often we check whether a split can be performed during [update].
 * @param rewards sample of the obtained rewards for analysis convenience.
 * @param trainAbsError the total absolute error obtained on a prediction before update.
 * @param testAbsError the total absolute error obtained on a prediction after update.
 */
class DecisionTreeBandit @JvmOverloads constructor(val problem: Problem,
                                                   val maximize: Boolean = true,
                                                   val randomSeed: Long = nanos(),
                                                   val solver: Solver = LocalSearchSolver(
                                                           problem, randomSeed = randomSeed, timeout = 1000L),
                                                   val posterior: Posterior,
                                                   val prior: VarianceStatistic = posterior.defaultPrior(),
                                                   historicData: Array<NodeData>? = null,
                                                   val delta: Double = 0.05,
                                                   val tau: Double = 0.1,
                                                   val maxNodes: Int = 10000,
                                                   val maxLiveNodes: Int = 500,
                                                   val maxConsideration: Int = 500,
                                                   val updateRate: Int = 5,
                                                   override val rewards: DataSample = GrowingDataSample(20),
                                                   override val trainAbsError: DataSample = GrowingDataSample(),
                                                   override val testAbsError: DataSample = GrowingDataSample()) : PredictionBandit {

    private var root: Node

    private val liveNodes: MutableList<LeafNode>
    private val randomSequence = RandomSequence(randomSeed)

    private var nbrNodes = 1
    private var nbrAuditNodes = 1

    init {
        if (historicData != null && historicData.isNotEmpty()) {
            historicData.sortBy { if (maximize) -it.total.mean else it.total.mean }

            liveNodes = ArrayList()
            root = historicData[0].setLiterals[0].toIx().let { ix ->
                SplitNode(EMPTY_INT_ARRAY, ix,
                        BlockNode(intArrayOf(ix.toLiteral(true)), prior.copy()),
                        BlockNode(intArrayOf(ix.toLiteral(false)), prior.copy()))
            }
            nbrNodes = 3
            nbrAuditNodes = 0

            fun createHistoricNode(setLiterals: Literals, total: VarianceStatistic): LeafNode {
                val node = if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes) {
                    AuditNode(setLiterals, total).also {
                        nbrAuditNodes++
                    }
                } else BlockNode(setLiterals, total)
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
                    if (r.ix.toLiteral(true) in node.setLiterals) {
                        val setLiterals = r.pos.setLiterals
                        r.pos = SplitNode(setLiterals, ix,
                                BlockNode((setLiterals + ix.toLiteral(true)).apply { sort() }, prior.copy()),
                                BlockNode((setLiterals + ix.toLiteral(false)).apply { sort() }, prior.copy()))
                        r = r.pos as SplitNode
                    } else {
                        val setLiterals = r.neg.setLiterals
                        r.neg = SplitNode(setLiterals, ix,
                                BlockNode((setLiterals + ix.toLiteral(true)).apply { sort() }, prior.copy()),
                                BlockNode((setLiterals + ix.toLiteral(false)).apply { sort() }, prior.copy()))
                        r = r.neg as SplitNode
                    }
                    nbrNodes += 2
                    stopIx++
                }
                if (node.setLiterals[stopIx].toBoolean()) r.pos = createHistoricNode(node.setLiterals.sortedArray(), node.total.copy())
                else r.neg = createHistoricNode(node.setLiterals.sortedArray(), node.total.copy())
            }
        } else {
            root = AuditNode(EMPTY_INT_ARRAY, prior.copy())
            liveNodes = arrayListOf(root as AuditNode)
        }
    }

    /**
     * Return all leaf nodes to use for external storage. They can be used to create a new [DecisionTreeBandit] that
     * continues optimizing through the historicData constructor parameter. Note that the order of the literals in
     * [NodeData] should not be changed.
     */
    fun exportData() = root.asSequence().map {
        // We change the order from sorted in ascending numeric value to split order, so that the tree can be
        // re-constructed exactly as is.
        val splitOrdered = IntArray(it.setLiterals.size)
        var r: Node = root
        var i = 0
        while (r is SplitNode) {
            if (r.ix.toLiteral(true) in it.setLiterals) {
                splitOrdered[i++] = r.ix.toLiteral(true)
                r = r.pos
            } else {
                splitOrdered[i++] = r.ix.toLiteral(false)
                r = r.neg
            }
        }
        NodeData(splitOrdered, it.total)
    }.toList().toTypedArray().apply {
        sortBy { if (maximize) -it.total.mean else it.total.mean }
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
        else solver.witnessOrThrow(assumptions + node.setLiterals)
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
            while (setLiterals[i].toIx() < assumptions[j].toIx() && i < setLiterals.size) i++
            while (assumptions[j].toIx() < setLiterals[i].toIx() && j < assumptions.size) {
                require(assumptions[j].toIx() < assumptions[j + 1].toIx()) { "Literals in assumption must be sorted." }
                j++
            }
            val l1 = setLiterals[i]
            val l2 = assumptions[j]
            if (l1.toIx() == l2.toIx()) {
                if (l1 != l2) return false
                i++
                j++
            }
        }
        return true
    }

    private interface Node {
        fun findLeaf(labeling: Labeling): LeafNode
        fun update(labeling: Labeling, result: Double, weight: Double): Node
        val setLiterals: Literals
        fun asSequence(): Sequence<LeafNode>
    }

    private inner class SplitNode(override val setLiterals: Literals,
                                  val ix: Ix, var pos: Node, var neg: Node) : Node {

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {
            if (labeling[ix]) pos = pos.update(labeling, result, weight)
            else neg = neg.update(labeling, result, weight)
            return this
        }

        override fun findLeaf(labeling: Labeling) =
                if (labeling[ix]) pos.findLeaf(labeling)
                else neg.findLeaf(labeling)

        fun findParent(literals: Literals): SplitNode? {
            for (lit in literals) {
                if (lit.toIx() == ix) {
                    val node = if (lit.toBoolean()) pos else neg
                    return if (node is LeafNode) this
                    else (node as SplitNode).findParent(literals)
                }
            }
            return null
        }

        override fun asSequence() = pos.asSequence() + neg.asSequence()
    }

    private abstract inner class LeafNode(override val setLiterals: Literals,
                                          val total: VarianceStatistic = prior.copy()) : Node {
        override fun findLeaf(labeling: Labeling) = this
        override fun asSequence() = sequenceOf(this)
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
                    val literalsPos: Literals = (setLiterals + ixs[bestI].toLiteral(true)).apply { sort() }
                    val literalsNeg: Literals = (setLiterals + ixs[bestI].toLiteral(false)).apply { sort() }

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

                    return SplitNode(setLiterals, ixs[bestI], pos, neg)
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
        return if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes && liveNodes.size < maxLiveNodes) {
            AuditNode(setLiterals, total).also {
                nbrAuditNodes++
                liveNodes.add(it)
            }
        } else if (liveNodes.size < maxLiveNodes) {
            BlockNode(setLiterals, total).also {
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
                    val parent = (root as SplitNode).findParent(worstNode.setLiterals)!!
                    val deadNode = BlockNode(worstNode.setLiterals, worstNode.total)
                    if (worstNode == parent.pos) parent.pos = deadNode
                    else parent.neg = deadNode
                    liveNodes[ix] = AuditNode(setLiterals, total)
                } else {
                    liveNodes[ix] = BlockNode(setLiterals, total)
                }
                liveNodes[ix]
            } else BlockNode(setLiterals, total)
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

