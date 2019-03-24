@file:JvmName("GeneticOperators")

package combo.math

import combo.sat.Instance
import combo.sat.MutableInstance
import combo.util.assert
import combo.util.transformArray
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

interface CandidateSolutions {

    val minScore: Float
    val maxScore: Float
    val oldestCandidate: Int
    val nbrCandidates: Int
        get() = instances.size
    val instances: Array<out MutableInstance>
    val nbrVariables: Int
        get() = instances[0].size

    fun score(ix: Int): Float

    /**
     * @return true if the update results in a new optimal solution
     */
    fun update(ix: Int, step: Long, newScore: Float): Boolean
}

fun Array<out Instance>.diversity(): Float {
    var sum = 0.0f
    for (i in 0 until get(0).size) {
        val v = RunningVariance()
        for (j in 0 until size)
            v.accept(if (this[j][i]) 1.0f else 0.0f)
        sum += v.squaredDeviations
    }
    return sum
}

fun Array<out Instance>.diversity2(): Float {
    val n = size
    val m = get(0).size
    var sum = 0.0f
    for (i in 0 until n) {
        val i1 = get(i)
        for (j in 0 until n) {
            if (i == j) continue
            val i2 = get(j)
            var overlap = 0
            for (k in 0 until m) if (i1[k] == i2[k]) overlap++
            sum += overlap / m.toFloat()
        }
    }
    return sum
}

fun Array<out Instance>.diversity3(): Float {
    val total = RunningVariance()
    for (i in 0 until get(0).size) {
        val v = RunningVariance()
        for (j in 0 until size)
            v.accept(if (this[j][i]) 1.0f else 0.0f)
        total.accept(4 * v.mean * (1 - v.mean))
    }
    return total.sum
}

fun Array<out Instance>.singularColumns(): Int {
    var singular = 0
    for (i in 0 until get(0).size) {
        var hasZero = false
        var hasOne = false
        for (j in 0 until size) {
            if (!this[j][i]) hasZero = true
            else hasOne = true
        }
        if (!hasOne && !hasZero) singular++
    }
    return singular
}

interface RecombinationOperator {
    fun combine(parent1: Int, parent2: Int, child: Int, candidates: CandidateSolutions, rng: Random)
}

class UniformRecombination : RecombinationOperator {
    override fun combine(parent1: Int, parent2: Int, child: Int, candidates: CandidateSolutions, rng: Random) {
        val s1 = candidates.instances[parent1]
        val s2 = candidates.instances[parent2]
        val s3 = candidates.instances[child]
        for (i in 0 until candidates.nbrVariables)
            if (rng.nextBoolean()) {
                if (s1[i] != s3[i]) s3.flip(i)
            } else if (s2[i] != s3[i]) s3.flip(i)
    }
}

class KPointRecombination(val k: Int = 1) : RecombinationOperator {
    override fun combine(parent1: Int, parent2: Int, child: Int, candidates: CandidateSolutions, rng: Random) {
        var s1 = candidates.instances[parent1]
        var s2 = candidates.instances[parent2]
        val s3 = candidates.instances[child]
        val perm = IntPermutation(candidates.nbrVariables, rng)
        val points = IntArray(min(k, candidates.nbrVariables)) { perm.encode(it) }.apply { sort() }
        var prev = 0
        for (point in points) {
            for (i in prev until point) {
                if (s3[i] != s1[i]) s3.flip(i)
            }
            val tmp = s1
            s1 = s2
            s2 = tmp
            prev = point
        }
        for (i in prev until candidates.nbrVariables) {
            if (s3[i] != s1[i]) s3.flip(i)
        }
    }
}

interface SelectionOperator {
    fun select(candidates: CandidateSolutions, rng: Random): Int
}

class UniformSelection : SelectionOperator {
    override fun select(candidates: CandidateSolutions, rng: Random) = rng.nextInt(candidates.nbrCandidates)
}

/**
 * Stochastic acceptance is a fitness proportionate method where a random variable i is tested and accepted with
 * probability score_min / score_i. As such it does not really work well where score_min is close to 0 or when
 * there are large differences between the best score and all others.
 */
class StochasticAcceptanceSelection(val acceptanceMin: Float = 0.05f) : SelectionOperator {
    override fun select(candidates: CandidateSolutions, rng: Random): Int {
        if (!candidates.minScore.isFinite()) return rng.nextInt(candidates.nbrCandidates)
        for (k in 1..100) {
            val i = rng.nextInt(candidates.nbrCandidates)
            val ratio = max(acceptanceMin, candidates.minScore / candidates.score(i))
            if (ratio.isNaN() || rng.nextFloat() < ratio) return i
        }
        return rng.nextInt(candidates.nbrCandidates)
    }
}

/**
 * Tournament selection selects the best among a random subset of the population, determined by [tournamentSize].
 */
class TournamentSelection(val tournamentSize: Int) : SelectionOperator {

    init {
        assert(tournamentSize > 0)
    }

    override fun select(candidates: CandidateSolutions, rng: Random): Int {
        var bestScore = Float.POSITIVE_INFINITY
        var best = 0
        val perm = IntPermutation(candidates.nbrCandidates, rng)
        for (i in 0 until min(candidates.nbrCandidates, tournamentSize)) {
            val ix = perm.encode(i)
            val s = candidates.score(ix)
            if (s < bestScore) {
                best = ix
                bestScore = s
            }
        }
        return best
    }
}

/**
 * Always eliminate oldest candidate. For non-stationary ([combo.sat.solvers.ObjectiveFunction] changes over time)
 * problems this can be a good strategy.
 */
class OldestElimination : SelectionOperator {
    override fun select(candidates: CandidateSolutions, rng: Random) = candidates.oldestCandidate
}

/**
 * This works like [TournamentSelection] but selects the worst instead.
 */
class TournamentElimination(val tournamentSize: Int) : SelectionOperator {

    init {
        assert(tournamentSize > 0)
    }

    override fun select(candidates: CandidateSolutions, rng: Random): Int {
        var worstScore = Float.NEGATIVE_INFINITY
        var worst = 0
        val perm = IntPermutation(candidates.nbrCandidates, rng)
        for (i in 0 until min(candidates.nbrCandidates, tournamentSize)) {
            val ix = perm.encode(i)
            val s = candidates.score(ix)
            if (s > worstScore) {
                worst = ix
                worstScore = s
            }
        }
        return worst
    }
}

interface MutationOperator {
    fun mutate(target: Int, candidates: CandidateSolutions, rng: Random)
}

interface RateMutationOperator : MutationOperator {

    fun mutationRate(nbrVariables: Int, rng: Random): Float

    override fun mutate(target: Int, candidates: CandidateSolutions, rng: Random) {
        val instance = candidates.instances[target]
        val rate = mutationRate(instance.size, rng)
        var index = rng.nextGeometric(rate) - 1
        while (index < instance.size) {
            instance.flip(index)
            index += rng.nextGeometric(rate)
        }
    }
}

class FixedMutation(val nbrFlips: Int = 1) : MutationOperator {
    override fun mutate(target: Int, candidates: CandidateSolutions, rng: Random) {
        val instance = candidates.instances[target]
        val permutation = IntPermutation(instance.size, rng)
        for (i in 0 until nbrFlips) {
            instance.flip(permutation.encode(i))
        }
    }
}

/**
 * This flips exactly [nbrFlips]
 */
class FixedRateMutation(val nbrFlips: Int = 1) : RateMutationOperator {
    override fun mutationRate(nbrVariables: Int, rng: Random) = min(1.0f, nbrFlips / nbrVariables.toFloat())
}

/**
 * See this paper for explanation https://arxiv.org/abs/1703.03334
 */
class FastGAMutation(val nbrVariables: Int, val beta: Float = 1.5f) : RateMutationOperator {

    private val pdfSampler: DiscretePdfSampler

    init {
        assert(beta > 1)
        val probs = FloatArray(max(1, nbrVariables / 2)) { (1 + it).toFloat().pow(-beta) }
        val sum = probs.sum()
        probs.transformArray { it / sum }
        pdfSampler = AliasMethodSampler(probs)
    }

    override fun mutationRate(nbrVariables: Int, rng: Random): Float {
        val r = pdfSampler.sample(rng) + 1
        return r / nbrVariables.toFloat()
    }
}
