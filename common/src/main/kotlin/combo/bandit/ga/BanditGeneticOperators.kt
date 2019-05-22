package combo.bandit.ga

import combo.ga.SelectionOperator
import combo.math.VarianceEstimator
import kotlin.random.Random

/**
 * TODO
 */
class SignificanceTestElimination<E : VarianceEstimator>(val estimators: Array<out VarianceEstimator>, val alpha: Float = 0.05f) : SelectionOperator<BanditCandidates<E>> {
    override fun select(candidates: BanditCandidates<E>, rng: Random): Int {
        // Should use overlapping confidence intervals or t-test.
        TODO("not implemented")
    }
}

/**
 * This eliminates the candidate/bandit with smallest number of plays.
 * @param minWeightedCount the minimum number of feedback that must be collected before it can be eliminated.
 */
class SmallestCountElimination<E : VarianceEstimator>(val minWeightedCount: Float = 0.0f) : SelectionOperator<BanditCandidates<E>> {
    override fun select(candidates: BanditCandidates<E>, rng: Random): Int {
        var min = Float.POSITIVE_INFINITY
        var minIx = -1
        for (i in candidates.instances.indices) {
            val n = candidates.estimators[candidates.instances[i]]!!.nbrWeightedSamples
            if (n >= minWeightedCount && n < min) {
                min = n
                minIx = i
            }
        }
        return minIx
    }
}