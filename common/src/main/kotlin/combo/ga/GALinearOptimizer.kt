package combo.ga

import combo.math.DescriptiveStatistic
import combo.math.ExponentialDecayVariance
import combo.math.Vector
import combo.model.IterationsReachedException
import combo.sat.*
import kotlin.math.absoluteValue
import kotlin.math.min

/**
 * Steady state GA ModelOptimizer.
 * See Classifier Systems
 * https://sfi-edu.s3.amazonaws.com/sfi-edu/production/uploads/sfi-com/dev/uploads/filer/2b/07/2b071152-def2-4475-8d18-3161db1bd7e3/92-07-032.pdf
 */
class GALinearOptimizer(val problem: Problem,
                        override val config: SolverConfig = SolverConfig(),
                        val init: LabelingInitializer = LookaheadInitializer(problem),
                        val popSize: Int = 50,
                        val maxIter: Int = Int.MAX_VALUE,
                        val timeout: Long = -1L,
                        val initFromPooled: Int = 10,
                        val selectionFunction: SelectionFunction = FitnessProportionalSampling(),
                        val eliminationFunction: SelectionFunction = UniformSampling(),
                        val pCrossover: Double = 0.7,
                        val crossoverSelection: CrossoverFunction = UniformCrossoverFunction(),
        //TODO val crossoverSelection: CrossoverFunction = KPointCrossoverFunction(1),
                        val pMutation: Double = 0.02,
                        val mutationFunction: MutationFunction = AdaptiveFlipMutation(problem),
                        val eps: Double = 1E-5,
                        genCacheSize: Int = 25) : LinearOptimizer {

    val poolCache = Array(genCacheSize) { init.generate(problem, config.labelingBuilder, config.nextRng()) }
    var pointer = 0
        private set


    override fun optimizeOrThrow(weights: Vector, contextLiterals: Literals): Labeling {
        val rng = config.nextRng()

        val population = Array(popSize) {
            if (it < initFromPooled) poolCache[rng.int(poolCache.size)]
            else init.generate(problem, config.labelingBuilder, config.nextRng())
        }

        val con = if (contextLiterals.isEmpty()) null else Conjunction(contextLiterals)

        val worstScore = -weights.array.sumByDouble { it.absoluteValue }

        fun score(labeling: MutableLabeling): Double {
            val infeasiblePenalty = ((if (con == null) 0 else con.flipsToSatisfy(labeling)) +
                    problem.sentences.sumBy { it.flipsToSatisfy(labeling) }).toDouble()
            return if (infeasiblePenalty > 0) worstScore - infeasiblePenalty
            else {
                val d = labeling dot weights
                if (config.maximize) d else -d
            }
        }

        var bestLabeling = population[0]
        var bestScore = Double.NEGATIVE_INFINITY

        fun updateTopScore(s: Double, lix: Int) {
            if (s > bestScore) {
                bestLabeling = population[lix].copy()
                bestScore = s
            }
        }

        val ds = DescriptiveStatistic(ExponentialDecayVariance(popSize))
        val scores = DoubleArray(popSize) {
            score(population[it]).also { s ->
                ds.accept(s)
                updateTopScore(s, it)
            }
        }
        val age = IntArray(popSize)
        val state = PopulationState(population, scores, age, 0, 0, ds)


        fun updateLabelingState(lix: Int, t: Int) {
            val s = score(population[lix])
            if (age[lix] == state.oldest) {
                state.oldest = t
                for (a in state.age) {
                    state.oldest = min(state.oldest, a)
                }
            }
            age[lix] = t
            state.youngest = age[lix]
            scores[lix] = s
            updateTopScore(s, lix)
        }

        for (t in 1..maxIter) {
            val lix1 = selectionFunction.select(popSize, scores, rng, state)
            if (rng.double() < pCrossover) {
                var lix2: Int
                do {
                    lix2 = selectionFunction.select(popSize, scores, rng, state)
                } while (lix1 == lix2)
                val l1 = population[lix1].copy()
                val l2 = population[lix2].copy()

                val eix1 = eliminationFunction.eliminate(popSize, scores, rng, state)
                var eix2: Int
                do {
                    eix2 = selectionFunction.select(popSize, scores, rng, state)
                } while (eix1 == eix2)
                population[eix1] = l1
                population[eix2] = l2
                crossoverSelection.crossover(l1, l2, rng)
                updateLabelingState(eix1, t)
                updateLabelingState(eix2, t)
                if (rng.double() < pMutation)
                    mutationFunction.mutate(population[eix1], scores[eix1], rng, state)
                if (rng.double() < pMutation)
                    mutationFunction.mutate(population[eix2], scores[eix2], rng, state)
            } else {
                mutationFunction.mutate(population[lix1], scores[lix1], rng, state)
                updateLabelingState(lix1, t)
            }

            // TODO convergence
        }
        return if (bestScore >= worstScore) bestLabeling else throw IterationsReachedException(maxIter)
    }
}
