@file:JvmName("GeneticOperators")

package combo.ga

import combo.math.IntPermutation
import combo.math.Rng
import combo.sat.MutableLabeling
import combo.sat.Problem
import kotlin.jvm.JvmName
import kotlin.math.roundToInt

interface CrossoverFunction {
    fun crossover(l1: MutableLabeling, l2: MutableLabeling, rng: Rng)
}

class UniformCrossoverFunction(private val mixingRate: Double = 0.5) : CrossoverFunction {
    override fun crossover(l1: MutableLabeling, l2: MutableLabeling, rng: Rng) {
        for (i in 0 until l1.size) {
            if (rng.double() < mixingRate) {
                val tmp = l1[i]
                l1[i] = l2[i]
                l2[i] = tmp
            }
        }
    }
}

class KPointCrossoverFunction(private val k: Int = 1) : CrossoverFunction {
    override fun crossover(l1: MutableLabeling, l2: MutableLabeling, rng: Rng) {
        TODO()
        /*
        val perm = IntPermutation(k)
        for (i in 0 until k) {
            val swap = rng.boolean()
            if (swap) {
                for (j in 0 until perm.encode(k)) {
                    val tmp = l1[i]
                    l1[i] = l2[i]
                    l2[i] = tmp
                }
            }
        }
        */
    }
}

interface SelectionFunction {
    fun select(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState): Int
    fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState): Int
}

/**
 * Implemented using stochastic acceptance
 */
class FitnessProportionalSampling : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState): Int {
        if (state.scoreStatistic.max.isInfinite() || state.scoreStatistic.max.isNaN()) return rng.int(nbrParents)
        while (true) {
            val ix = rng.int(nbrParents)
            if (rng.double() < scores[ix] / state.scoreStatistic.max) return ix
        }
    }

    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState): Int {
        if (state.scoreStatistic.max.isInfinite() || state.scoreStatistic.max.isNaN()) return rng.int(nbrParents)
        while (true) {
            val ix = rng.int(nbrParents)
            if (rng.double() > scores[ix] / state.scoreStatistic.max) return ix
        }
    }
}

class UniformSampling : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState) = rng.int(nbrParents)
    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState) = rng.int(nbrParents)
}

/**
 * Deterministic tournament selection
 */
class TournamentSelection(val tournamentSize: Int) : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState): Int {
        val perm = IntPermutation(nbrParents, rng)
        var score = Double.NEGATIVE_INFINITY
        var best = 0
        for (i in 0 until tournamentSize) {
            val ix = perm.encode(i)
            if (scores[ix] > score) {
                best = ix
                score = scores[ix]
            }
        }
        return best
    }

    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState): Int {
        val perm = IntPermutation(nbrParents, rng)
        var score = Double.POSITIVE_INFINITY
        var worst = 0
        for (i in 0 until tournamentSize) {
            val ix = perm.encode(i)
            if (scores[ix] < score) {
                worst = ix
                score = scores[ix]
            }
        }
        return worst
    }
}

class AgeSelection : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState) = state.youngest
    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Rng, state: PopulationState) = state.oldest
}

interface MutationFunction {
    fun mutate(labeling: MutableLabeling, score: Double, rng: Rng, state: PopulationState)
}

class FlipMutation(val problem: Problem, val flips: Int = 1, val setImplications: Boolean = true) : MutationFunction {
    override fun mutate(labeling: MutableLabeling, score: Double, rng: Rng, state: PopulationState) {
        for (i in 1..flips) {
            val ix = rng.int(labeling.size)
            labeling.flip(ix)
            if (setImplications)
                labeling.setAll(problem.implicationGraph[labeling.asLiteral(ix)])
        }
    }
}

class AdaptiveFlipMutation(val problem: Problem, val setImplications: Boolean = true) : MutationFunction {
    override fun mutate(labeling: MutableLabeling, score: Double, rng: Rng, state: PopulationState) {
        val flips = (state.scoreStatistic.max - score) / (state.scoreStatistic.max - state.scoreStatistic.mean) * 0.5
        for (i in 1..flips.roundToInt()) {
            val ix = rng.int(labeling.size)
            labeling.flip(ix)
            if (setImplications)
                labeling.setAll(problem.implicationGraph[labeling.asLiteral(ix)])
        }
    }
}
