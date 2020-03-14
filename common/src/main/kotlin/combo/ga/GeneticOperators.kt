package combo.ga

import combo.math.AliasMethodSampler
import combo.math.DiscreteSampler
import combo.math.nextGeometric
import combo.math.permutation
import combo.sat.TransitiveImplications
import combo.sat.literal
import combo.util.assert
import combo.util.transformArray
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * The recombination (or crossover) takes two parents and produces an offspring. The offspring is produced inline
 * in the [combo.sat.Instance] indicated by the index into the [Candidates.instances] array.
 */
interface RecombinationOperator<in C : Candidates> {
    fun combine(parent1: Int, parent2: Int, child: Int, candidates: C, rng: Random)
}

/**
 * Choose each variable uniformly at random from each parent.
 */
class UniformRecombination : RecombinationOperator<ValidatorCandidates> {
    override fun combine(parent1: Int, parent2: Int, child: Int, candidates: ValidatorCandidates, rng: Random) {
        val s1 = candidates.instances[parent1]
        val s2 = candidates.instances[parent2]
        val s3 = candidates.instances[child]
        for (i in 0 until candidates.nbrVariables)
            if (rng.nextBoolean()) {
                if (s1.isSet(i) != s3.isSet(i)) s3.flip(i)
            } else if (s2.isSet(i) != s3.isSet(i)) s3.flip(i)
    }
}

/**
 * [k]-points are selected randomly (plus end points) and the intervals between the points are selected from each
 * parent interwoven.
 */
class KPointRecombination(val k: Int = 1) : RecombinationOperator<ValidatorCandidates> {
    override fun combine(parent1: Int, parent2: Int, child: Int, candidates: ValidatorCandidates, rng: Random) {
        var s1 = candidates.instances[parent1]
        var s2 = candidates.instances[parent2]
        val s3 = candidates.instances[child]
        val perm = permutation(candidates.nbrVariables, rng)
        val points = IntArray(min(k, candidates.nbrVariables)) { perm.encode(it) }.apply { sort() }
        var prev = 0
        for (point in points) {
            for (i in prev until point) {
                if (s3.isSet(i) != s1.isSet(i)) s3.flip(i)
            }
            val tmp = s1
            s1 = s2
            s2 = tmp
            prev = point
        }
        for (i in prev until candidates.nbrVariables) {
            if (s3.isSet(i) != s1.isSet(i)) s3.flip(i)
        }
    }
}

/**
 * The selection is used to select a candidate index from [Candidates.instances]. When this is used for selecting
 * the maximum they are called XSelection, when they are used for selecting minimum they are callsed XElimination.
 */
interface SelectionOperator<in C : Candidates> {
    fun select(candidates: C, rng: Random): Int
}

class UniformSelection : SelectionOperator<Candidates> {
    override fun select(candidates: Candidates, rng: Random) = rng.nextInt(candidates.nbrCandidates)
}

/**
 * Stochastic acceptance is a fitness proportionate method where a random variable i is tested and accepted with
 * probability score_min / score_i. As such it does not really work well where score_min is close to 0 or when
 * there are large differences between the best score and all others. When it does work it can be the best option.
 */
class StochasticAcceptanceSelection(val acceptanceMin: Float = 0.05f) : SelectionOperator<Candidates> {
    override fun select(candidates: Candidates, rng: Random): Int {
        if (!candidates.bestScore.isFinite()) return rng.nextInt(candidates.nbrCandidates)
        for (k in 1..100) {
            val i = rng.nextInt(candidates.nbrCandidates)
            val ratio = max(acceptanceMin, candidates.bestScore / candidates.score(i, false))
            if (ratio.isNaN() || rng.nextFloat() < ratio) return i
        }
        return rng.nextInt(candidates.nbrCandidates)
    }
}

/**
 * Tournament selection selects the maximum among a random subset of the population, determined by [tournamentSize].
 */
class TournamentSelection(val tournamentSize: Int) : SelectionOperator<Candidates> {

    init {
        assert(tournamentSize > 0)
    }

    override fun select(candidates: Candidates, rng: Random): Int {
        var bestScore = Float.POSITIVE_INFINITY
        var best = 0
        val perm = permutation(candidates.nbrCandidates, rng)
        for (i in 0 until min(candidates.nbrCandidates, tournamentSize)) {
            val ix = perm.encode(i)
            val s = candidates.score(ix, false)
            if (s < bestScore) {
                best = ix
                bestScore = s
            }
        }
        return best
    }
}

/**
 * Always eliminate oldest candidate. For non-stationary ([combo.sat.optimizers.ObjectiveFunction] changes over time)
 * problems this can be a good strategy.
 */
class OldestElimination : SelectionOperator<Candidates> {
    override fun select(candidates: Candidates, rng: Random) = candidates.oldestCandidate
}

/**
 * This works like [TournamentSelection] but selects the minimum score instead. May fail by returning -1.
 */
class TournamentElimination(val tournamentSize: Int) : SelectionOperator<Candidates> {

    init {
        assert(tournamentSize > 0)
    }

    override fun select(candidates: Candidates, rng: Random): Int {
        var worstScore = Float.NEGATIVE_INFINITY
        var worst = -1
        val perm = permutation(candidates.nbrCandidates, rng)
        for (i in 0 until min(candidates.nbrCandidates, tournamentSize)) {
            val ix = perm.encode(i)
            val s = candidates.score(ix, true)
            if (s > worstScore) {
                worst = ix
                worstScore = s
            }
        }
        return worst
    }
}

/**
 * The mutation operator performs random flips on a target instance.
 */
interface MutationOperator<in C : Candidates> {
    fun mutate(target: Int, candidates: C, rng: Random)
}

interface MutationRate {
    fun rate(nbrVariables: Int, rng: Random): Float
}

/**
 * This flips each variable with a uniform probability rate.
 */
class RateMutationOperator(val mutationRate: MutationRate) : MutationOperator<ValidatorCandidates> {

    override fun mutate(target: Int, candidates: ValidatorCandidates, rng: Random) {
        val instance = candidates.instances[target]
        val rate = mutationRate.rate(instance.size, rng)
        var index = rng.nextGeometric(rate) - 1
        while (index < instance.size) {
            instance.flip(index)
            index += rng.nextGeometric(rate)
        }
    }
}

class PropagatingMutator(val mutationRate: MutationRate, val transitiveImplications: TransitiveImplications) : MutationOperator<ValidatorCandidates> {
    override fun mutate(target: Int, candidates: ValidatorCandidates, rng: Random) {
        val instance = candidates.instances[target]
        val rate = mutationRate.rate(instance.size, rng)
        var index = rng.nextGeometric(rate) - 1
        while (index < instance.size) {
            instance.flip(index)
            transitiveImplications.propagate(instance.literal(index), instance)
            index += rng.nextGeometric(rate)
        }
    }
}

/**
 * This flips exactly [nbrFlips].
 */
class FixedMutation(val nbrFlips: Int = 1) : MutationOperator<ValidatorCandidates> {
    init {
        assert(nbrFlips > 0)
    }

    override fun mutate(target: Int, candidates: ValidatorCandidates, rng: Random) {
        val instance = candidates.instances[target]
        val permutation = permutation(instance.size, rng)
        for (i in 0 until nbrFlips) {
            instance.flip(permutation.encode(i))
        }
    }
}

/**
 * This flips with rate [nbrFlips] / N.
 */
class FixedRateMutation(val nbrFlips: Int = 1) : MutationRate {
    override fun rate(nbrVariables: Int, rng: Random) = min(1.0f, nbrFlips / nbrVariables.toFloat())
}

/**
 * See this paper for explanation https://arxiv.org/abs/1703.03334
 */
class FastGAMutation(val nbrVariables: Int, val beta: Float = 1.5f) : MutationRate {

    private val sampler: DiscreteSampler

    init {
        assert(beta > 1)
        val probs = FloatArray(max(1, nbrVariables / 2)) { (1 + it).toFloat().pow(-beta) }
        val sum = probs.sum()
        probs.transformArray { it / sum }
        sampler = AliasMethodSampler(probs)
    }

    override fun rate(nbrVariables: Int, rng: Random): Float {
        val r = sampler.sample(rng) + 1
        return r / nbrVariables.toFloat()
    }
}
