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

class DecisionTreeBandit @JvmOverloads constructor(val problem: Problem,
                                                   val maximize: Boolean = true,
                                                   val randomSeed: Long = nanos(),
                                                   val solver: Solver = LocalSearchSolver(
                                                           problem, randomSeed = randomSeed, timeout = 1000L),
                                                   val posterior: Posterior,
                                                   val prior: VarianceStatistic = posterior.defaultPrior(),
                                                   override val rewards: DataSample = GrowingDataSample(20),
                                                   val nMin: Int = 5,
                                                   val delta: Double = 0.05,
                                                   val tau: Double = 0.1,
                                                   val maxDepth: Int = Int.MAX_VALUE,
                                                   val maxNodes: Int = Int.MAX_VALUE,
                                                   override val trainAbsError: DataSample = GrowingDataSample(),
                                                   override val testAbsError: DataSample = GrowingDataSample()) : PredictionBandit {


    // For info on delta and tau parameters of the VFDT algorithm check out these resources:
    // https://github.com/ulmangt/vfml/blob/master/weka/src/main/java/weka/classifiers/trees/VFDT.java
    // http://kt.ijs.si/elena_ikonomovska/00-disertation.pdf

    private val randomSequence = RandomSequence(randomSeed)
    var root: Node = AuditNode(EMPTY_INT_ARRAY, prior.copy())
        private set
    private val leaves: MutableList<LeafNode> = arrayListOf(root as AuditNode)
    var nbrNodes = 1
        private set
    var nbrAuditNodes = 1
        private set

    override fun chooseOrThrow(assumptions: IntArray): Labeling {
        val rng = randomSequence.next()
        val node = if (maximize) {
            leaves.maxBy {
                if (matches(it.setLiterals, assumptions)) posterior.sample(rng, it.total)
                else Double.NEGATIVE_INFINITY
            }
        } else {
            leaves.minBy {
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

    interface Node {
        fun findLeaf(labeling: Labeling): LeafNode
        fun update(labeling: Labeling, result: Double, weight: Double): Node
        val setLiterals: Literals
    }

    inner class SplitNode(override val setLiterals: Literals,
                          val ix: Ix, pos: Node, neg: Node) : Node {
        var pos: Node = pos
            private set
        var neg: Node = neg
            private set

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {
            if (labeling[ix])
                pos = pos.update(labeling, result, weight)
            else
                neg = neg.update(labeling, result, weight)
            return this
        }

        override fun findLeaf(labeling: Labeling) =
                if (labeling[ix]) pos.findLeaf(labeling)
                else neg.findLeaf(labeling)
    }

    abstract inner class LeafNode(override val setLiterals: Literals,
                                  val total: VarianceStatistic = prior.copy()) : Node {
        override fun findLeaf(labeling: Labeling) = this
    }

    inner class DeadNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {
        override fun update(labeling: Labeling, result: Double, weight: Double) =
                this.apply { total.accept(result, weight) }
    }

    inner class AuditNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {

        var nViewed: Int = 0

        val ids = IntSet().let { set ->
            set.addAll(0 until problem.nbrVariables)
            setLiterals.forEach { set.remove(it.toIx()) }
            set.toArray().apply { sort() }
        }
        val dataPos: Array<VarianceStatistic> = Array(ids.size) { prior.copy() }
        val dataNeg: Array<VarianceStatistic> = Array(ids.size) { prior.copy() }

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {
            total.accept(result, weight)
            nViewed++
            for ((i, ix) in ids.withIndex()) {
                if (labeling[ix]) dataPos[i].accept(result, weight)
                else dataNeg[i].accept(result, weight)
            }

            if (nViewed > nMin) {
                var ig1 = 0.0
                var ig2 = 0.0
                var bestI = -1

                for (i in ids.indices) {
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

                    val pos: LeafNode
                    val neg: LeafNode
                    val posLiterals = (setLiterals + ids[bestI].toLiteral(true)).apply { sort() }
                    val negLiterals = (setLiterals + ids[bestI].toLiteral(false)).apply { sort() }
                    if (setLiterals.size + 1 < maxDepth && 2 + nbrNodes + 2 * (nbrAuditNodes + 1) <= maxNodes) {
                        pos = AuditNode(posLiterals, dataPos[bestI])
                        neg = AuditNode(negLiterals, dataNeg[bestI])
                        nbrAuditNodes++
                    } else if (setLiterals.size + 1 < maxDepth && 2 + nbrNodes + 2 * nbrAuditNodes <= maxNodes) {
                        val posHigh = dataPos[bestI].mean > dataNeg[bestI].mean
                        if ((posHigh && maximize) || (!posHigh && !maximize)) {
                            pos = AuditNode(posLiterals, dataPos[bestI])
                            neg = DeadNode(negLiterals, dataNeg[bestI])
                        } else {
                            pos = DeadNode(posLiterals, dataPos[bestI])
                            neg = AuditNode(negLiterals, dataNeg[bestI])
                        }
                    } else {
                        pos = DeadNode(posLiterals, dataPos[bestI])
                        neg = DeadNode(negLiterals, dataNeg[bestI])
                        nbrAuditNodes--
                    }
                    nbrNodes += 2
                    leaves.remove(this)
                    leaves.add(pos)
                    leaves.add(neg)
                    return SplitNode(setLiterals, ids[bestI], pos, neg)
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

    private fun hoeffdingBound(delta: Double, count: Double): Double {
        // R = 1 for both binary classification and with variance ratio
        return sqrt(/* R*R* */ ln(1.0 / delta) / (2.0 * count))
    }
}


