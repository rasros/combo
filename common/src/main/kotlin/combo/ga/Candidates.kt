package combo.ga

import combo.sat.MutableInstance
import combo.sat.Validator
import kotlin.math.max
import kotlin.math.min

/**
 * Candidates stores state for genetic algorithm minimization.
 */
interface Candidates {

    /**
     * The actual candidates.
     */
    val instances: Array<out MutableInstance>

    /**
     * Minimum score of all instances.
     */
    val minScore: Float

    /**
     * Maximum score of all instances.
     */
    val maxScore: Float

    /**
     * Index of the candidate which was created furthest steps back.
     */
    val oldestCandidate: Int

    val nbrCandidates: Int
        get() = instances.size


    val nbrVariables: Int
        get() = instances[0].size

    /**
     * Gives the score of a candidate. This is always cached so it is cheap to call.
     */
    fun score(ix: Int): Float
}

/**
 * Using [Validator] to wrap [MutableInstance] so that it is fast to calculate the number of violated constraints.
 * This is used for optimizing with [combo.sat.solvers.GeneticAlgorithmOptimizer].
 */
class ValidatorCandidates(override val instances: Array<Validator>, val origins: IntArray, val scores: FloatArray)
    : Candidates {

    override var minScore: Float = Float.POSITIVE_INFINITY
        private set
    override var maxScore: Float = Float.NEGATIVE_INFINITY
        private set
    var oldestOrigin: Int = Int.MAX_VALUE
        private set
    override var oldestCandidate: Int = 0
        private set
    override val nbrCandidates: Int
        get() = instances.size
    override val nbrVariables: Int
        get() = instances[0].size

    init {
        for (i in instances.indices) {
            val s = scores[i]
            minScore = min(minScore, s)
            if (s > maxScore) {
                maxScore = s
                if (origins[i] <= oldestOrigin) {
                    oldestCandidate = i
                    oldestOrigin = origins[i]
                }
            }
        }
    }

    override fun score(ix: Int) = scores[ix]

    /**
     * Update the [score] of candidate at position [ix].
     * @return true if this is a new optimal.
     */
    fun update(ix: Int, step: Long, score: Float): Boolean {
        scores[ix] = score
        origins[ix] = step.toInt()
        if (oldestCandidate == ix || step < oldestOrigin) {
            oldestOrigin = Int.MAX_VALUE
            for (i in origins.indices) {
                if (origins[i] < oldestOrigin) {
                    oldestOrigin = origins[i]
                    oldestCandidate = i
                    if (oldestOrigin == 0) break
                }
            }
        }
        val ret = score < minScore
        maxScore = max(maxScore, score)
        minScore = min(minScore, score)
        return ret
    }
}