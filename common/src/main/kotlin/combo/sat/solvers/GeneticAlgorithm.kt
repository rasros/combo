@file:JvmName("GeneticAlgorithm")

package combo.sat.solvers

import combo.math.*
import combo.sat.*
import combo.util.IntSet
import combo.util.millis
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Steady state Genetic Algorithm Optimizer.
 * See Classifier Systems
 * https://sfi-edu.s3.amazonaws.com/sfi-edu/production/uploads/sfi-com/dev/uploads/filer/2b/07/2b071152-def2-4475-8d18-3161db1bd7e3/92-07-032.pdf
 */
open class GAOptimizer<O : ObjectiveFunction>(val problem: Problem) : Optimizer<O> {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
    override var timeout: Long = -1L
    private var randomSequence = RandomSequence(nanos())

    /**
     * The number of solution candidates that is generated by the search. This is the most important parameter to tweak.
     */
    var candidateSize: Int = max(20, min(problem.nbrVariables * 5, 300))

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [IntSetInstanceFactory] otherwise [BitFieldInstanceFactory].
     */
    var instanceFactory: InstanceFactory = BitFieldInstanceFactory

    /**
     * This contains cached information about satisfied constraints during search. [PropTrackingInstanceFactory] is more
     * efficient for optimizing but uses more memory than [BasicTrackingInstanceFactory]. The default for genetic algorithms
     * is [BasicTrackingInstanceFactory].
     */
    var trackingInstanceFactory: TrackingInstanceFactory = PropTrackingInstanceFactory(problem)

    /**
     * Variables will be initialized according to this for each instance. The default is [RandomInitializer] which
     * initializes uniform at random, consider switching to [combo.sat.WeightInitializer] for [LinearObjective].
     */
    var initializer: ValueInitializer<O> = RandomInitializer()

    /**
     * The search will be restarted up to [restarts] number of time and the best value will be selected from each
     * restart. For SAT solving restarts will be set to [Int.MAX_VALUE].
     */
    var restarts: Int = 1

    /**
     * Percentage of candidate solutions that will be kept using [selection] method in the case of a restart. The
     * other will be randomly generated new candidates.
     */
    var restartKeeps: Double = 0.2

    /**
     * Maximum number of steps for each of the [restarts].
     */
    var maxSteps: Int = max(500, problem.nbrVariables)

    /**
     * Threshold of improvement to stop current iteration in the search.
     */
    var eps: Double = 1E-4

    /**
     * This is the maximum number of steps that can be performed with no improvement on the [O] objective function.
     * It is only applied if a feasible solution is found.
     */
    var stallSteps: Int = max(50, problem.nbrVariables / 4)

    /**
     * The method by which candidate solutions are selected to form the next candidate that is added to the pool of
     * candidates (see also [elimination]).
     */
    var selection: SelectionOperator = TournamentSelection(max(2, candidateSize / 10))

    /**
     * The method by which candidate solution are selected to eliminate from the pool of candidates
     * (see also [selection]).
     */
    var elimination: SelectionOperator = TournamentElimination(max(2, candidateSize / 5))

    /**
     * The recombinaton operator controls the way that new candidates are generated after the initial population.
     */
    var recombination: RecombinationOperator = KPointRecombination(1)

    /**
     * The [mutation] operator in conjunction with the [mutationProbability] adds additional diversity to the candidate
     * solutions. The default flips one random variable with probability 1.
     */
    var mutation: MutationOperator = FixedMutation()

    /**
     * The [mutation] operator in conjunction with the [mutationProbability] adds additional diversity to the candidate
     * solutions. The default flips one random variable with probability 1.
     */
    var mutationProbability: Double = 1.0

    /**
     *
     */
    var penalty: PenaltyFunction = DisjunctPenalty()

    override fun optimizeOrThrow(function: O, assumptions: Literals): Instance {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val lowerBound = function.lowerBound()
        val upperBound = function.upperBound()

        fun score(s: TrackingInstance) = function.value(s).let { it + penalty.penalty(it, s.totalUnsatisfied, lowerBound, upperBound) }

        val instances: Array<TrackingInstance> = Array(candidateSize) {
            trackingInstanceFactory.build(instanceFactory.create(problem.nbrVariables), assumptions, initializer, function, randomSequence.next())
        }
        val candidates = let {
            val ages = IntArray(candidateSize)
            val scores = DoubleArray(candidateSize) {
                val s = score(instances[it])
                if (abs(s - lowerBound) < eps && instances[it].totalUnsatisfied == 0)
                    return instances[it].instance
                s
            }
            CandidateSolutions(instances, scores, ages)
        }

        for (restart in 1..restarts) {
            var stalls = 0
            val rng = randomSequence.next()

            for (step in 1..maxSteps) {
                val eliminated = elimination.select(candidates, rng)
                val parent1: Int = selection.select(candidates, rng)
                val parent2: Int = selection.select(candidates, rng)
                recombination.combine(parent1, parent2, eliminated, candidates, rng)
                val updatedLabeling = instances[eliminated]
                if (rng.nextDouble() < mutationProbability || parent1 == parent2)
                    mutation.mutate(updatedLabeling, rng)
                val score = score(updatedLabeling)
                if (abs(score - lowerBound) < eps && updatedLabeling.totalUnsatisfied == 0)
                    return updatedLabeling

                if (!candidates.update(eliminated, step, score)) stalls++
                else stalls = 0

                if (millis() > end || (stalls >= stallSteps && restart < restarts) || candidates.minScore == candidates.maxScore) break
            }

            if (restart == restarts || millis() > end) break

            val keep = IntSet().apply { add(0) }
            var tries = 0
            while (keep.size < max(0.2, restartKeeps) * candidateSize || tries++ < candidateSize)
                keep.add(selection.select(candidates, rng))
            tries = 1
            while (keep.size < restartKeeps * candidateSize) keep.add(tries)

            for (i in 0 until candidateSize) {
                if (i in keep) {
                    candidates.ages[i] = 0
                } else {
                    instances[i] = trackingInstanceFactory.build(instanceFactory.create(problem.nbrVariables), assumptions, initializer, function, randomSequence.next())
                    @Suppress("UNCHECKED_CAST")
                    (candidates.instances as Array<MutableInstance>)[i] = instances[i]
                    candidates.update(i, 0, score(instances[i]))
                }
            }
        }

        for (i in 0 until candidateSize)
            if (instances[i].totalUnsatisfied == 0)
                return instances[i].instance

        if (millis() > end)
            throw TimeoutException(timeout)
        else throw IterationsReachedException(restarts)
    }
}


/**
 * This class changes the default parameters to be suitable for SAT solving.
 */
class GASolver(problem: Problem) : GAOptimizer<SatObjective>(problem), Solver {
    init {
        restarts = Int.MAX_VALUE
        penalty = LinearPenalty()
    }

    override fun witnessOrThrow(assumptions: Literals) = optimizeOrThrow(SatObjective, assumptions)
}
