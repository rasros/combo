package combo.bandit.ga

import combo.ga.SelectionOperator
import combo.math.normInvCdf
import combo.math.permutation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class EliminationChain(vararg val eliminators: SelectionOperator<BanditCandidates>) : SelectionOperator<BanditCandidates> {

    init {
        require(eliminators.isNotEmpty())
    }

    override fun select(candidates: BanditCandidates, rng: Random): Int {
        for (e in eliminators) {
            val el = e.select(candidates, rng)
            if (el >= 0) return el
        }
        return -1
    }
}

/**
 * This eliminates the first candidate/bandit that is proven to be significantly worse than another.
 * @param alpha significance level to used to calculate z-value.
 */
class SignificanceTestElimination(alpha: Float = 0.05f) : SelectionOperator<BanditCandidates> {

    init {
        require(alpha > 0.0f && alpha < 1.0f)
    }

    val z = normInvCdf(1 - alpha / 2)

    override fun select(candidates: BanditCandidates, rng: Random): Int {
        var maxCI = Float.NEGATIVE_INFINITY
        var minCI = Float.POSITIVE_INFINITY
        val perm = permutation(candidates.nbrCandidates, rng)
        for (i in perm) {
            val e = candidates.estimators[candidates.instances[i]]!!
            if (e.nbrWeightedSamples < candidates.minSamples) continue
            val ci = z * e.standardDeviation / sqrt(e.nbrWeightedSamples)
            val lower = e.mean - ci
            val upper = e.mean + ci
            maxCI = max(lower, maxCI)
            minCI = min(upper, minCI)
            if ((candidates.maximize && upper < maxCI) || (!candidates.maximize && lower > minCI))
                return i // This candidate is eliminated
            if ((candidates.maximize && maxCI > minCI) || (!candidates.maximize && minCI < maxCI))
                break // Previous candidate is eliminated

        }
        if (minCI.isNaN() || maxCI.isNaN() || minCI >= maxCI) return -1
        for (i in perm) {
            val instance = candidates.instances[i]
            val e = candidates.estimators[instance]
            if (e == null || e.nbrWeightedSamples < candidates.minSamples) continue
            if (candidates.maximize) {
                val upper = e.mean + z * e.standardDeviation / sqrt(e.nbrWeightedSamples)
                if (upper < maxCI)
                    return i

            } else {
                val lower = e.mean - z * e.standardDeviation / sqrt(e.nbrWeightedSamples)
                if (lower > minCI)
                    return i
            }
        }
        throw IllegalArgumentException()
    }
}

/**
 * This eliminates the candidate/bandit with smallest number of plays.
 */
class SmallestCountElimination : SelectionOperator<BanditCandidates> {
    override fun select(candidates: BanditCandidates, rng: Random): Int {
        var min = Float.POSITIVE_INFINITY
        var minIx = -1
        for (i in candidates.instances.indices) {
            val n = candidates.estimators[candidates.instances[i]]!!.nbrWeightedSamples
            if (n >= candidates.minSamples && n < min) {
                min = n
                minIx = i
            }
        }
        return minIx
    }
}