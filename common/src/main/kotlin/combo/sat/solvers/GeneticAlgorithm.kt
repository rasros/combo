@file:JvmName("GeneticAlgorithm")

package combo.sat.solvers

import combo.math.*
import combo.sat.*
import combo.util.millis
import combo.util.nanos
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.max

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
     * TODO
     */
    var candidateSize: Int = 20

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [IntSetInstanceFactory] otherwise [BitFieldInstanceFactory].
     */
    var instanceFactory: InstanceFactory = BitFieldInstanceFactory

    /**
     * This contains cached information about satisfied constraints during search. [PropSearchStateFactory] is more
     * efficient for optimizing but uses more memory than [BasicSearchStateFactory]. The default for genetic algorithms
     * is [BasicSearchStateFactory].
     */
    var stateFactory: SearchStateFactory = PropSearchStateFactory(problem)

    /**
     * Variables will be initialized according to this for each instance. The default is [RandomSelector] which
     * initializes uniform at random.
     */
    var selector: ValueSelector<O> = RandomSelector

    /**
     * The search will be restarted up to [restarts] number of time and the best value will be selected from each
     * restart. For SAT solving restarts will be set to [Int.MAX_VALUE].
     */
    var restarts: Int = 5

    /**
     * Maximum number of steps for each of the [restarts].
     */
    var maxSteps: Int = max(200, problem.nbrVariables)

    /**
     * Threshold of improvement to stop current iteration in the search.
     */
    var eps: Double = 1E-4

    /**
     * TODO
     */
    var stallSteps: Int = 20

    /**
     * TODO
     */
    var selection1: SelectionOperator = TournamentSelection(max(2, candidateSize / 10))

    var selection2: SelectionOperator = selection1

    /**
     * TODO
     */
    var elimination: SelectionOperator = TournamentElimination(max(2, candidateSize / 5))//OldestElimination()

    /**
     * TODO
     */
    var recombination: RecombinationOperator = KPointRecombination(1)

    /**
     * TODO
     */
    var mutation: MutationOperator = FastGAMutation(problem.nbrVariables)

    /**
     * TODO
     */
    var mutationProbability: Double = 0.05

    /**
     * TODO
     */
    var penalty: PenaltyFunction = SquaredPenalty()

    override fun optimizeOrThrow(function: O, assumptions: Literals): Instance {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val lowerBound = function.lowerBound()

        fun score(s: SearchState) = function.value(s).let { it + penalty.penalty(it, s.totalUnsatisfied) }

        var stalls = 0
        var population: Array<SearchState>? = null

        for (restart in 1..restarts) {
            val rng = randomSequence.next()

            population = Array(candidateSize) {
                stateFactory.build(instanceFactory.create(problem.nbrVariables), assumptions, selector, function, randomSequence.next())
            }
            val state = let {
                val ages = IntArray(candidateSize)
                val scores = DoubleArray(candidateSize) {
                    val s = score(population[it])
                    if (abs(s - lowerBound) < eps && population[it].totalUnsatisfied == 0) return population[it].instance
                    s
                }
                CandidateSolutions(population, scores, ages)
            }

            for (step in 1..maxSteps) {
                val eliminated = elimination.select(state, rng)
                val parent1: Int = selection1.select(state, rng)
                val parent2: Int = selection2.select(state, rng)
                recombination.combine(parent1, parent2, eliminated, state, rng)
                val updatedLabeling = population[eliminated]
                if (rng.nextDouble() < mutationProbability || parent1 == parent2)
                    mutation.mutate(updatedLabeling, rng)
                val score = score(updatedLabeling)
                if (abs(score - lowerBound) < eps && updatedLabeling.totalUnsatisfied == 0)
                    return updatedLabeling

                if (!state.update(eliminated, step, score)) stalls++
                else stalls = 0

                if (millis() > end || (stalls >= stallSteps && population[0].totalUnsatisfied == 0)) break
            }
        }

        for (i in 0 until candidateSize)
            if (population!![i].totalUnsatisfied == 0) return population[i].instance

        if (millis() > end) throw TimeoutException(timeout)
        else throw IterationsReachedException(restarts)
    }
}


/**
 * This class changes the default parameters to be suitable for SAT solving.
 */
class GASolver(problem: Problem) : GAOptimizer<SatObjective>(problem), Solver {
    init {
        restarts = Int.MAX_VALUE
    }

    override fun witnessOrThrow(assumptions: Literals) = optimizeOrThrow(SatObjective, assumptions)
}
