@file:JvmName("GeneticOperators")

package combo.math

import combo.sat.UnitPropagationTable
import combo.sat.MutableLabeling
import kotlin.jvm.JvmName
import kotlin.math.roundToInt
import kotlin.random.Random

class PopulationState(
        val population: Array<MutableLabeling>,
        val scores: DoubleArray,
        val age: IntArray,
        var oldest: Int,
        var youngest: Int,
        var scoreStatistic: DescriptiveStatistic)

interface CrossoverFunction {
    fun crossover(l1: MutableLabeling, l2: MutableLabeling, rng: Random)
}

class UniformCrossoverFunction(private val mixingRate: Double = 0.5) : CrossoverFunction {
    override fun crossover(l1: MutableLabeling, l2: MutableLabeling, rng: Random) {
        for (i in 0 until l1.size) {
            if (rng.nextDouble() < mixingRate) {
                val tmp = l1[i]
                l1[i] = l2[i]
                l2[i] = tmp
            }
        }
    }
}

class KPointCrossoverFunction(private val k: Int = 1) : CrossoverFunction {
    override fun crossover(l1: MutableLabeling, l2: MutableLabeling, rng: Random) {
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
    fun select(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState): Int
    fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState): Int
}

/**
 * Implemented using stochastic acceptance
 */
class FitnessProportionalSampling : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState): Int {
        if (state.scoreStatistic.max.isInfinite() || state.scoreStatistic.max.isNaN()) return rng.nextInt(nbrParents)
        while (true) {
            val ix = rng.nextInt(nbrParents)
            if (rng.nextDouble() < scores[ix] / state.scoreStatistic.max) return ix
        }
    }

    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState): Int {
        if (state.scoreStatistic.max.isInfinite() || state.scoreStatistic.max.isNaN()) return rng.nextInt(nbrParents)
        while (true) {
            val ix = rng.nextInt(nbrParents)
            if (rng.nextDouble() > scores[ix] / state.scoreStatistic.max) return ix
        }
    }
}

object UniformSampling : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState) = rng.nextInt(nbrParents)
    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState) = rng.nextInt(nbrParents)
}

/**
 * Deterministic tournament selection
 */
class TournamentSelection(val tournamentSize: Int) : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState): Int {
        var score = Double.NEGATIVE_INFINITY
        var best = 0
        for (ix in IntPermutation(nbrParents, rng).iterator()) {
            if (scores[ix] > score) {
                best = ix
                score = scores[ix]
            }
        }
        return best
    }

    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState): Int {
        var score = Double.POSITIVE_INFINITY
        var worst = 0
        for (ix in IntPermutation(nbrParents, rng).iterator()) {
            if (scores[ix] < score) {
                worst = ix
                score = scores[ix]
            }
        }
        return worst
    }
}

object AgeSelection : SelectionFunction {
    override fun select(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState) = state.youngest
    override fun eliminate(nbrParents: Int, scores: DoubleArray, rng: Random, state: PopulationState) = state.oldest
}

interface MutationFunction {
    fun mutate(labeling: MutableLabeling, score: Double, rng: Random, state: PopulationState)
}

class FlipMutation(val flips: Int = 1) : MutationFunction {
    override fun mutate(labeling: MutableLabeling, score: Double, rng: Random, state: PopulationState) {
        for (i in 1..flips) {
            val ix = rng.nextInt(labeling.size)
            labeling.flip(ix)
        }
    }
}

class PropagatedFlipMutation(val problem: UnitPropagationTable, val flips: Int = 1) : MutationFunction {
    override fun mutate(labeling: MutableLabeling, score: Double, rng: Random, state: PopulationState) {
        for (i in 1..flips) {
            val ix = rng.nextInt(labeling.size)
            labeling.flip(ix)
            labeling.setAll(problem.literalPropagations[labeling.asLiteral(ix)])
        }
    }
}

class PropagatedAdaptiveFlipMutation(val problem: UnitPropagationTable) : MutationFunction {
    override fun mutate(labeling: MutableLabeling, score: Double, rng: Random, state: PopulationState) {
        val flips = (state.scoreStatistic.max - score) / (state.scoreStatistic.max - state.scoreStatistic.mean) * 0.5
        for (i in 1..flips.roundToInt()) {
            val ix = rng.nextInt(labeling.size)
            labeling.flip(ix)
            labeling.setAll(problem.literalPropagations[labeling.asLiteral(ix)])
        }
    }
}
