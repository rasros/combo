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
 * @param problem
 * @param maximize
 * @param randomSeed
 * @param solver
 * @param posterior
 * @param prior
 * @param historicData
 * @param delta
 * @param tau
 * @param maxNodes
 * @param maxLiveNodes
 * @param maxConsideration
 * @param updateRate
 * @param rewards
 * @param trainAbsError
 * @param testAbsError
 */
class DecisionTreeBandit @JvmOverloads constructor(val problem: Problem,
                                                   val maximize: Boolean = true,
                                                   val randomSeed: Long = nanos(),
                                                   val solver: Solver = LocalSearchSolver(
                                                           problem, randomSeed = randomSeed, timeout = 1000L),
                                                   val posterior: Posterior,
                                                   val prior: VarianceStatistic = posterior.defaultPrior(),
                                                   val historicData: Array<NodeData>? = null,
                                                   val delta: Double = 0.05,
                                                   val tau: Double = 0.1,
                                                   val maxNodes: Int = 10000,
                                                   val maxLiveNodes: Int = 500,
                                                   val maxConsideration: Int = 500,
                                                   val updateRate: Int = 5,
                                                   override val rewards: DataSample = GrowingDataSample(20),
                                                   override val trainAbsError: DataSample = GrowingDataSample(),
                                                   override val testAbsError: DataSample = GrowingDataSample()) : PredictionBandit {

    init {
        if (historicData != null) {
        }
    }

    // For info on delta and tau parameters of the VFDT algorithm check out these resources:
    // https://github.com/ulmangt/vfml/blob/master/weka/src/main/java/weka/classifiers/trees/VFDT.java
    // http://kt.ijs.si/elena_ikonomovska/00-disertation.pdf

    private val randomSequence = RandomSequence(randomSeed)
    private var root: Node = AuditNode(EMPTY_INT_ARRAY, prior.copy())
    private val liveNodes: MutableList<LeafNode> = arrayListOf(root as AuditNode)

    private var nbrNodes = 1
    private var nbrAuditNodes = 1

    /**
     * Return all leaf nodes to use for external storage. They can be used to create a new [DecisionTreeBandit] that
     * continues optimizing through the [historicData] parameter.
     */
    fun exportData() = root.asSequence().filter { it is LeafNode }.map {
        val ln = it as LeafNode
        NodeData(ln.setLiterals, ln.total)
    }.toList().toTypedArray()


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

    private fun matches(setLiterals: Literals, assumptions: Literals): Boolean {
        var j = 0
        for (i in assumptions.indices) {
            val l1 = assumptions[i]
            while (setLiterals[j] < l1) j++
            val l2 = setLiterals[j]
            if (l1.toIx() == l2.toIx() && l1 != l2) return false
        }
        return true
    }

    private interface Node {
        fun findLeaf(labeling: Labeling): LeafNode
        fun update(labeling: Labeling, result: Double, weight: Double): Node
        val setLiterals: Literals
        fun asSequence(): Sequence<Node>
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

        fun findParent(literals: Literals): SplitNode {
            for (lit in literals) {
                if (lit.toIx() == ix) {
                    val node = if (lit.toBoolean()) pos else neg
                    return if (node is LeafNode) this
                    else (node as SplitNode).findParent(literals)
                }
            }
            throw IllegalArgumentException()
        }

        override fun asSequence() = pos.asSequence() + neg.asSequence()
    }

    private abstract inner class LeafNode(override val setLiterals: Literals,
                                          val total: VarianceStatistic = prior.copy()) : Node {
        override fun findLeaf(labeling: Labeling) = this
        override fun asSequence() = sequenceOf(this)
    }

    private inner class DeadNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {
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
                if (bestI >= 0 && dataPos[bestI].nbrSamples > prior.nbrSamples && dataNeg[bestI].nbrSamples > prior.nbrSamples &&
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
        val node = if (2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes) {
            AuditNode(setLiterals, total).also {
                nbrAuditNodes++
            }
        } else DeadNode(setLiterals, total)
        if (liveNodes.size + 1 > maxLiveNodes) {
            // find target to replace worst live node
            val (ix, worstNode) = liveNodes.asSequence().mapIndexed { i, n -> i to n }.minBy { pair ->
                pair.second.total.mean.let { mean -> if (maximize) mean else -mean }
            }!!
            // if target is worse than new node
            if ((maximize && worstNode.total.mean < total.mean) || (!maximize && worstNode.total.mean > total.mean)) {
                if (worstNode is AuditNode) {
                    // audit node should be replaced with dead node
                    val parent = (root as SplitNode).findParent(worstNode.setLiterals)
                    val deadNode = DeadNode(worstNode.setLiterals, worstNode.total)
                    if (worstNode == parent.pos) parent.pos = deadNode
                    else parent.neg = deadNode
                    if (node is DeadNode)
                    // new node is turned to audit node
                        liveNodes[ix] = AuditNode(setLiterals, total)
                    else {
                        // new node replaces old audit node
                        nbrAuditNodes--
                        liveNodes[ix] = node
                    }
                } else liveNodes[ix] = node
                return liveNodes[ix]
            } // else new node is ignored
        } else liveNodes.add(node)
        return node
    }

    private fun hoeffdingBound(delta: Double, count: Double): Double {
        // R = 1 for both binary classification and with variance ratio
        return sqrt(/* R*R* */ ln(1.0 / delta) / (2.0 * count))
    }
}

class NodeData(val setLiterals: Literals, val total: VarianceStatistic)

