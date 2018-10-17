package combo.bandit

import combo.math.DataSample
import combo.math.GrowingDataSample
import combo.math.Posterior
import combo.math.VarianceStatistic
import combo.sat.*
import combo.sat.WalkSat
import combo.util.IndexSet
import kotlin.jvm.JvmOverloads
import kotlin.math.ln
import kotlin.math.sqrt

// TODO improvement idea compare with something other 2nd best. Perhaps middle or top-k
class DecisionTreeBandit @JvmOverloads constructor(val problem: Problem,
                                                   override val config: SolverConfig = SolverConfig(),
                                                   val solver: Solver = WalkSat(problem, config),
                                                   val posterior: Posterior,
                                                   val prior: VarianceStatistic = posterior.defaultPrior(),
                                                   override val rewards: DataSample = GrowingDataSample(20),
                                                   val maxDepth: Int = 10,
                                                   val nMin: Int = 5,
                                                   val delta: Double = 0.05,
                                                   val tau: Double = 0.1) : Bandit {

    // For info on delta and tau parameters of the VFDT algorithm check out these resources:
    // https://github.com/ulmangt/vfml/blob/master/weka/src/main/java/weka/classifiers/trees/VFDT.java
    // http://kt.ijs.si/elena_ikonomovska/00-disertation.pdf

    private val leaves: MutableList<LeafNode> = ArrayList()
    private var root: Node = AuditNode(IntArray(0), prior)

    override fun chooseOrThrow(contextLiterals: IntArray): Labeling {
        val rng = config.nextRng()
        val node = if (config.maximize) {
            leaves.maxBy {
                if (matches(it.setLiterals, contextLiterals)) posterior.sample(rng, it.total)
                else Double.NEGATIVE_INFINITY
            }
        } else {
            leaves.minBy {
                if (matches(it.setLiterals, contextLiterals)) posterior.sample(rng, it.total)
                else Double.POSITIVE_INFINITY
            }
        }
        return if (node == null) solver.witnessOrThrow(contextLiterals)
        else solver.witnessOrThrow(contextLiterals + node.setLiterals)
    }

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        root = root.update(labeling, result, weight)
    }

    /**
     * Fully build the decision tree down to [maxDepth] depth.
     */
    fun explode() {
        val rng = config.nextRng()
        TODO()
    }

    private fun matches(contextLiterals: Literals, setLiterals: Literals): Boolean {
        var j = 0
        for (i in contextLiterals.indices) {
            val l1 = contextLiterals[i]
            while (setLiterals[j] < l1) j++
            val l2 = setLiterals[j]
            if (l1.asIx() == l2.asIx() && l1 != l2) return false
        }
        return true
    }


    private interface Node {
        fun findLeaf(labeling: Labeling): LeafNode
        fun update(labeling: Labeling, result: Double, weight: Double): Node
        val setLiterals: Literals
    }

    private inner class SplitNode(override val setLiterals: Literals,
                                  val ix: Ix,
                                  var pos: Node,
                                  var neg: Node) : Node {

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

    private abstract inner class LeafNode(override val setLiterals: Literals,
                                          val total: VarianceStatistic = prior.copy()) : Node {
        override fun findLeaf(labeling: Labeling) = this
    }

    private inner class DeadNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {
        override fun update(labeling: Labeling, result: Double, weight: Double) =
                this.apply { total.accept(result, weight) }
    }

    private inner class AuditNode(setLiterals: Literals, total: VarianceStatistic) : LeafNode(setLiterals, total) {

        var nViewed: Int = 0

        val ids = IndexSet().apply { addAll((0 until problem.nbrVariables) - setLiterals.map { it.asIx() }) }
                .toArray().apply { sort() }
        val dataPos: Array<VarianceStatistic> = Array(problem.nbrVariables - setLiterals.size) { prior.copy() }
        val dataNeg: Array<VarianceStatistic> = Array(problem.nbrVariables - setLiterals.size) { prior.copy() }

        override fun update(labeling: Labeling, result: Double, weight: Double): Node {

            nViewed++
            for (i in ids)
                if (labeling[i]) dataPos[i].accept(result, weight)
                else dataNeg[i].accept(result, weight)

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
                if (bestI >= 0 && (ig2 / ig1 < 1 - eps || eps < tau)) {

                    val pos: Node
                    val neg: Node
                    val posLiterals = (setLiterals + ids[bestI].asLiteral(true)).apply { sort() }
                    val negLiterals = (setLiterals + ids[bestI].asLiteral(false)).apply { sort() }
                    if (setLiterals.size + 1 >= maxDepth) {
                        pos = DeadNode(posLiterals, dataPos[bestI])
                        neg = DeadNode(negLiterals, dataNeg[bestI])
                    } else {
                        pos = AuditNode(posLiterals, dataPos[bestI])
                        neg = AuditNode(negLiterals, dataNeg[bestI])
                    }
                    nViewed = 0
                    return SplitNode(setLiterals, ids[bestI], pos, neg)
                }
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
        return sqrt(/* R*R* */ ln(1.0 / delta) / (2.0 * count));
    }
}


