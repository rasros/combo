package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.*
import combo.sat.*
import combo.sat.solvers.LocalSearchSolver
import combo.sat.solvers.Solver
import combo.util.EMPTY_INT_ARRAY
import combo.util.IntSet
import combo.util.nanos
import kotlin.jvm.JvmOverloads
import kotlin.math.ln
import kotlin.math.max
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
 * @param solver the solver will be used to generate [Labeling]s that satisfy the constraints from the [Problem].
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
    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
            this.solver.randomSeed = value
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
     * The number of randomly selected variables that leaf nodes consider for splitting the tree further during [update].
     */
    var maxConsideration: Int = 500

    /**
     * How often we check whether a split can be performed during [update].
     */
    var updateRate: Int = 5

    private var randomSequence = RandomSequence(nanos())

    private val liveNodes = ArrayList<LeafNode>()

    private var root: Node = AuditNode(EMPTY_INT_ARRAY, banditPolicy.baseData(), Random(0)).also {
        // We use a fixed random seed here because this node is created before randomSeed can be set.
        liveNodes.add(it)
        banditPolicy.addArm(it.data)
    }

    private var nbrNodes = 1
    private var nbrAuditNodes = 1

    private val minSamples = banditPolicy.baseData().nbrWeightedSamples

    override fun importData(historicData: Array<LiteralData<E>>) {
        val sorted = historicData.sortedBy { if (maximize) -it.data.mean else it.data.mean }

        for (node in liveNodes) banditPolicy.removeArm(node.data)

        liveNodes.clear()
        val prior = banditPolicy.baseData()
        root = sorted[0].setLiterals[0].toIx().let { ix ->
            require(ix < problem.nbrVariables)
            SplitNode(ix, BlockNode(intArrayOf(ix.toLiteral(true)), prior),
                    BlockNode(intArrayOf(ix.toLiteral(false)), prior))
        }
        nbrNodes = 3
        nbrAuditNodes = 0

        fun createHistoricNode(setLiterals: Literals, total: E): LeafNode {
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
                    r.pos = SplitNode(ix, BlockNode((setLiterals + ix.toLiteral(true)), prior),
                            BlockNode((setLiterals + ix.toLiteral(false)), prior))
                    r = r.pos as SplitNode
                } else if (r.ix.toLiteral(false) == node.setLiterals[stopIx]) {
                    if (r.neg is LeafNode && (r.neg as LeafNode).data !== prior)
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

            // 3) add leaf node
            if (node.setLiterals[stopIx] == r.ix.toLiteral(true)) {
                if ((r.pos as? LeafNode)?.data === prior)
                    r.pos = createHistoricNode(node.setLiterals.sortedArray(), node.data.copy() as E)
                // else there is junk in the historicData and the current node is ignored
            } else if (node.setLiterals[stopIx] == r.ix.toLiteral(false)) {
                if ((r.neg as? LeafNode)?.data === prior)
                    r.neg = createHistoricNode(node.setLiterals.sortedArray(), node.data.copy() as E)
                // else there is junk in the historicData and the current node is ignored
            }
        }

        // Replace all BlockNodes with data == prior with real node
        val queue = ArrayList<SplitNode>()
        queue.add(root as SplitNode)
        while (queue.isNotEmpty()) {
            val r = queue.removeAt(queue.lastIndex)
            if (r.pos is SplitNode) queue.add(r.pos as SplitNode)
            else if ((r.pos is BlockNode) && (r.pos as BlockNode).data === prior)
                r.pos = createHistoricNode((r.pos as BlockNode).setLiterals, banditPolicy.baseData())
            if (r.neg is SplitNode) queue.add(r.neg as SplitNode)
            else if ((r.neg is BlockNode) && (r.neg as BlockNode).data === prior)
                r.neg = createHistoricNode((r.neg as BlockNode).setLiterals, banditPolicy.baseData())
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

    override fun chooseOrThrow(assumptions: IntArray): Labeling {
        val rng = randomSequence.next()
        banditPolicy.beginRound(rng)
        val node = liveNodes.maxBy {
            val s = if (matches(it.setLiterals, assumptions)) banditPolicy.evaluate(it.data, rng)
            else Double.NEGATIVE_INFINITY
            if (maximize) s else -s
        }
        return if (node == null) solver.witnessOrThrow(assumptions)
        else {
            return try {
                solver.witnessOrThrow(assumptions + node.setLiterals)
            } catch (e: ValidationException) {
                // Assumptions did not match node setLiterals
                solver.witnessOrThrow(assumptions)
            }
        }
    }

    override fun predict(labeling: Labeling) = root.findLeaf(labeling).data.mean

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

    private abstract inner class Node {
        abstract fun findLeaf(labeling: Labeling): LeafNode
        abstract fun update(labeling: Labeling, result: Double, weight: Double): Node
    }

    private inner class SplitNode(val ix: Ix, var pos: Node, var neg: Node) : Node() {

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
                                          val data: E = banditPolicy.baseData()) : Node() {
        override fun findLeaf(labeling: Labeling) = this
    }

    private inner class BlockNode(setLiterals: Literals, data: E) : LeafNode(setLiterals, data) {
        override fun update(labeling: Labeling, result: Double, weight: Double) =
                this.apply { banditPolicy.completeRound(data, result, weight) }
    }

    private inner class AuditNode(setLiterals: Literals, total: E,
                                  rng: Random = randomSequence.next()) : LeafNode(setLiterals, total) {

        var nViewed: Int = 0

        val ixs = IntSet().let { set ->
            val itr = IntPermutation(problem.nbrVariables, rng).iterator()
            while (set.size < maxConsideration && itr.hasNext()) {
                val ix = itr.nextInt()
                if (ix.toLiteral(true) in setLiterals || ix.toLiteral(false) in setLiterals) continue
                set.add(ix)
            }
            set.toArray().apply { sort() }
        }
        val dataPos: Array<VarianceEstimator> = Array(ixs.size) { banditPolicy.baseData() }
        val dataNeg: Array<VarianceEstimator> = Array(ixs.size) { banditPolicy.baseData() }

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {
            banditPolicy.completeRound(data, result, weight)
            nViewed++
            for ((i, ix) in ixs.withIndex()) {
                if (labeling[ix]) banditPolicy.updateData(dataPos[i] as E, result, weight)
                else banditPolicy.updateData(dataNeg[i] as E, result, weight)
            }

            if (nViewed > updateRate) {
                var ig1 = 0.0
                var ig2 = 0.0
                var bestI = -1

                for (i in ixs.indices) {
                    val ig = data.variance - variancePurity(i)
                    if (ig > ig1) {
                        bestI = i
                        ig2 = ig1
                        ig1 = ig
                    } else if (ig > ig2)
                        ig2 = ig
                }

                val eps = hoeffdingBound(delta, data.nbrWeightedSamples)
                if (bestI >= 0 && dataPos[bestI].nbrWeightedSamples > max(4.0, minSamples) &&
                        dataNeg[bestI].nbrWeightedSamples > max(4.0, minSamples) &&
                        (ig2 / ig1 < 1 - eps || eps < tau)) {

                    val totalPos = dataPos[bestI] as E
                    val totalNeg = dataNeg[bestI] as E
                    val literalsPos: Literals = (setLiterals + ixs[bestI].toLiteral(true))
                    val literalsNeg: Literals = (setLiterals + ixs[bestI].toLiteral(false))

                    val posHigh = dataPos[bestI].mean > dataNeg[bestI].mean
                    val inOrder = (posHigh && maximize) || (!posHigh && !maximize)

                    liveNodes.remove(this)
                    banditPolicy.removeArm(data)

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

                    banditPolicy.addArm(pos.data)
                    banditPolicy.addArm(neg.data)

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

    private fun createNode(setLiterals: Literals, total: E): LeafNode {
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
                    val deadNode = BlockNode(worstNode.setLiterals, worstNode.data)
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


