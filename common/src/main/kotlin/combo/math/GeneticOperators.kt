@file:JvmName("GeneticOperators")

package combo.math

import combo.sat.Instance
import combo.sat.MutableInstance
import combo.util.transformArray
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class CandidateSolutions(
        val instances: Array<out MutableInstance>,
        val scores: DoubleArray,
        val ages: IntArray) {

    val bestInstance: Instance
        get() = instances[0]
    val minScore: Double
        get() = scores[0]
    val maxScore: Double
        get() = scores.last()
    val oldest: Int
        get() {
            var oldestI = ages.size - 1
            var oldest = ages[oldestI]

            // We iterate backwards to break tie by worst score
            for (i in oldestI downTo 0) {
                if (ages[i] < oldest) {
                    oldest = ages[i]
                    oldestI = i
                }
            }
            return oldestI
        }
    val populationSize: Int
        get() = instances.size
    val nbrVariables: Int
        get() = instances[0].size

    init {
        // Sort all arrays according to score
        val index = ArrayList<Int>()
        index.addAll(0 until instances.size)
        index.sortBy {
            scores[it]
        }

        for (i in 0 until instances.size) {
            while (index[i] != i) {
                swap(index[index[i]], index[i])
                val oldI = index[index[i]]
                index[index[i]] = index[i]
                index[i] = oldI
            }
        }
    }

    private fun swap(ix1: Int, ix2: Int) {
        val oldPop = instances[ix1]
        val oldScore = scores[ix1]
        val oldAge = ages[ix1]

        @Suppress("UNCHECKED_CAST")
        (instances as Array<MutableInstance>)[ix1] = instances[ix2]
        scores[ix1] = scores[ix2]
        ages[ix1] = ages[ix2]

        instances[ix2] = oldPop
        scores[ix2] = oldScore
        ages[ix2] = oldAge
    }

    fun update(ix: Int, time: Int, newScore: Double): Boolean {
        scores[ix] = newScore
        ages[ix] = time
        var i = ix
        while (i + 1 < scores.size && scores[i] > scores[i + 1]) {
            swap(i + 1, i)
            i++
        }
        while (i - 1 >= 0 && scores[i] < scores[i - 1]) {
            swap(i - 1, i)
            i--
        }
        return i == 0
    }
}

interface RecombinationOperator {
    fun combine(parent1: Int, parent2: Int, child: Int, cl: CandidateSolutions, rng: Random)
}

class UniformRecombination : RecombinationOperator {
    override fun combine(parent1: Int, parent2: Int, child: Int, cl: CandidateSolutions, rng: Random) {
        val s1 = cl.instances[parent1]
        val s2 = cl.instances[parent2]
        val s3 = cl.instances[child]
        for (i in 0 until cl.nbrVariables)
            if (rng.nextBoolean()) {
                if (s1[i] != s3[i]) s3.flip(i)
            } else if (s2[i] != s3[i]) s3.flip(i)
    }
}

class KPointRecombination(val k: Int = 1) : RecombinationOperator {
    override fun combine(parent1: Int, parent2: Int, child: Int, cl: CandidateSolutions, rng: Random) {
        var s1 = cl.instances[parent1]
        var s2 = cl.instances[parent2]
        val s3 = cl.instances[child]
        val perm = IntPermutation(cl.nbrVariables, rng)
        val points = IntArray(min(k, cl.nbrVariables)) { perm.encode(it) }.apply { sort() }
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
        for (i in prev until cl.nbrVariables) {
            if (s3[i] != s1[i]) s3.flip(i)
        }
    }
}

interface SelectionOperator {
    fun select(state: CandidateSolutions, rng: Random): Int
}

class UniformSelection : SelectionOperator {
    override fun select(state: CandidateSolutions, rng: Random) = rng.nextInt(state.scores.size)
}

class StochasticAcceptanceSelection(val acceptancePenalty: Double = 0.0) : SelectionOperator {
    override fun select(state: CandidateSolutions, rng: Random): Int {
        if (!state.minScore.isFinite() || state.minScore == state.maxScore) return rng.nextInt(state.scores.size)
        TODO()
        while (true) {
            val i = rng.nextInt(state.scores.size)
            val norm = (state.scores[i] - state.minScore) / (state.maxScore - state.minScore)
            if (rng.nextDouble() < 1 - norm) return i
        }
    }
}

class TournamentSelection(val tournamentSize: Int) : SelectionOperator {

    init {
        require(tournamentSize > 0)
    }

    override fun select(state: CandidateSolutions, rng: Random): Int {
        var score = Double.POSITIVE_INFINITY
        var best = 0
        val perm = IntPermutation(state.populationSize, rng)
        for (i in 0 until min(state.populationSize, tournamentSize)) {
            val ix = perm.encode(i)
            if (state.scores[ix] < score) {
                best = ix
                score = state.scores[ix]
            }
        }
        return best
    }
}

class OldestElimination : SelectionOperator {
    override fun select(state: CandidateSolutions, rng: Random) = state.oldest
}

class TournamentElimination(val tournamentSize: Int) : SelectionOperator {

    init {
        require(tournamentSize > 0)
    }

    override fun select(state: CandidateSolutions, rng: Random): Int {
        var score = Double.NEGATIVE_INFINITY
        var worst = 0
        val perm = IntPermutation(state.populationSize, rng)
        for (i in 0 until min(state.populationSize, tournamentSize)) {
            val ix = perm.encode(i)
            if (state.scores[ix] > score) {
                worst = ix
                score = state.scores[ix]
            }
        }
        return worst
    }
}

interface MutationOperator {
    fun mutate(target: MutableInstance, rng: Random)
}

interface PointMutationOperator : MutationOperator {

    fun mutationRate(nbrVariables: Int, rng: Random): Double

    override fun mutate(target: MutableInstance, rng: Random) {
        val rate = mutationRate(target.size, rng)
        var index = rng.nextGeometric(rate) - 1
        while (index < target.size) {
            target.flip(index)
            index += rng.nextGeometric(rate)
        }
    }
}

class FixedMutation(val nbrFlips: Int = 1) : MutationOperator {
    override fun mutate(target: MutableInstance, rng: Random) {
        val permutation = IntPermutation(target.size, rng)
        for (i in 0 until nbrFlips) {
            target.flip(permutation.encode(i))
        }
    }
}

/**
 * Otherwise known as OnePlusOne when [nbrFlips] = 1.
 */
class FixedRateMutation(val nbrFlips: Int = 1) : PointMutationOperator {
    override fun mutationRate(nbrVariables: Int, rng: Random) = min(1.0, nbrFlips / nbrVariables.toDouble())
}

/**
 * TODO reference
 */
class FastGAMutation(val nbrVariables: Int, val beta: Double = 1.5) : PointMutationOperator {

    private val pdfSampler: DiscretePdfSampler

    init {
        require(beta > 1)
        val probs = DoubleArray(max(1, nbrVariables / 2)) { (1 + it).toDouble().pow(-beta) }
        val sum = probs.sum()
        probs.transformArray { it / sum }
        pdfSampler = AliasMethodSampler(probs)
    }

    override fun mutationRate(nbrVariables: Int, rng: Random): Double {
        val r = pdfSampler.sample(rng) + 1
        return r / nbrVariables.toDouble()
    }
}
