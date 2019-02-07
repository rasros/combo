package combo.sat.solvers

import combo.math.*
import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.Problem
import combo.util.nanos

/*
/**
 * Steady state GA ModelOptimizer.
 * See Classifier Systems
 * https://sfi-edu.s3.amazonaws.com/sfi-edu/production/uploads/sfi-com/dev/uploads/filer/2b/07/2b071152-def2-4475-8d18-3161db1bd7e3/92-07-032.pdf
 */
class GASolver<in O : ObjectiveFunction>(val problem: Problem,
                                         override var timeout: Long = -1L,
                                         override var randomSeed: Long = nanos(),
                                         val popSize: Int = 50,
                                         val maxIter: Int = Int.MAX_VALUE,
                                         val initFromPooled: Int = 10,
                                         val selection: SelectionFunction = FitnessProportionalSampling,
                                         val elimination: SelectionFunction = UniformSampling,
                                         val crossover: CrossoverFunction = UniformCrossoverFunction,
                                         val pCrossover: Double = 0.7,
        //TODO val crossoverSelection: CrossoverFunction = KPointCrossoverFunction(1),
                                         val mutation: MutationFunction,
                                         val pMutation: Double = 0.02,
                                         val eps: Double = 1E-5,
                                         val greedyHeuristic: Boolean = true,
                                         val genCacheSize: Int = 25) : Optimizer<O> {

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        TODO("not implemented")
    }

}

/*
private fun cachedTracker(assumptions: Literals, labeling: MutableLabeling): SearchState {
    val tracker = if (propGraph != null) PropSearchState(labeling, problem, propGraph)
    else BasicSearchState(labeling, problem)
    tracker.initialize(assumptions)
    return tracker
}

private fun newTracker(function: ObjectiveFunction?, assumptions: Literals, rng: Random): SearchState {
    val selector = if (greedyHeuristic && function is LinearObjective) WeightSelector(function.weights, rng) else RandomSelector(rng)
    val tracker = if (propGraph != null) PropSearchState(config.labelingBuilder.build(problem.nbrVariables), problem, propGraph)
    else BasicSearchState(config.labelingBuilder.build(problem.nbrVariables), problem)
    tracker.initialize(assumptions, selector, rng)
    return tracker
}

val poolCache = ArrayList<MutableLabeling>()

override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
    val rng = config.nextRandom()

    val population = Array(popSize) {
        if (it < initFromPooled) cachedTracker(assumptions, poolCache[rng.nextInt(poolCache.size)].copy())
        else newTracker(function, assumptions, rng)
    }

    val con = if (assumptions.isEmpty()) null else Conjunction(IntList(assumptions))

    val lowerBound = function.lowerBound(config.maximize)
    val upperBound = function.upperBound(config.maximize)

    fun score(tracker: SearchState) =
            function.value(tracker.labeling, tracker.unsatisfied.size, lowerBound, upperBound, config.maximize)

    var bestLabeling = population[0].labeling.copy()
    var bestScore = Double.NEGATIVE_INFINITY

    fun updateTopScore(s: Double, i: Int) {
        if (s > bestScore) {
            bestLabeling = population[i].labeling.copy()
            bestScore = s
        }
    }
    /*

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
        if (rng.nextDouble() < pCrossover) {
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
            if (rng.nextDouble() < pMutation)
                mutationFunction.mutate(population[eix1], scores[eix1], rng, state)
            if (rng.nextDouble() < pMutation)
                mutationFunction.mutate(population[eix2], scores[eix2], rng, state)
        } else {
            mutationFunction.mutate(population[lix1], scores[lix1], rng, state)
            updateLabelingState(lix1, t)
        }

        // TODO convergence
    }
    return if (bestScore >= worstScore) bestLabeling else throw IterationsReachedException(maxIter)
    */
    TODO()
}
}

    */