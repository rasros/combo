package combo.bandit.dt

import combo.bandit.glm.LearningRateSchedule
import combo.math.VectorView
import combo.math.nextGamma
import combo.math.vectors
import kotlin.random.Random

interface VoteStrategy {
    fun weights(votesYes: ShortArray, votesNo: ShortArray, rng: Random, trees: Int, t: Long): VectorView
}

class BetaRandomizedVotes(val priorVotes: Float = 1f, val explorationRate: LearningRateSchedule) : VoteStrategy {
    override fun weights(votesYes: ShortArray, votesNo: ShortArray, rng: Random, trees: Int, t: Long): VectorView {
        val weights = FloatArray(votesYes.size) {
            if (votesYes[it] == votesNo[it]) 0f
            else {
                val a = votesYes[it] + priorVotes
                val b = votesNo[it] + priorVotes
                val lr = explorationRate.rate(t)
                val ahat = rng.nextGamma(a / trees / lr)
                val bhat = rng.nextGamma(b / trees / lr)
                val p = ahat / (ahat + bhat)
                2 * p - 1
            }
        }
        return vectors.vector(weights)
    }
}

class SumVotes(val priorVotes: Float = 1f) : VoteStrategy {
    override fun weights(votesYes: ShortArray, votesNo: ShortArray, rng: Random, trees: Int, t: Long): VectorView {
        val weights = FloatArray(votesYes.size) {
            val a = votesYes[it] + priorVotes
            val b = votesNo[it] + priorVotes
            (a - b) / trees
        }
        return vectors.vector(weights)
    }
}
