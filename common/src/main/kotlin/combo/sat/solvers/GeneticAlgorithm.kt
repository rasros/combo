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
open class GeneticAlgorithmOptimizer<O : ObjectiveFunction>(val problem: Problem) : Optimizer<O> {

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
    var populationSize: Int = 20

    /**
     * Determines the [Labeling] that will be created for solving, for very sparse problems use
     * [IntSetLabelingFactory] otherwise [BitFieldLabelingFactory].
     */
    var labelingFactory: LabelingFactory = BitFieldLabelingFactory

    /**
     * This contains cached information about satisfied constraints during search. [PropSearchStateFactory] is more
     * efficient for optimizing but uses more memory than [BasicSearchStateFactory]. The default for genetic algorithms
     * is [BasicSearchStateFactory].
     */
    var stateFactory: SearchStateFactory = BasicSearchStateFactory(problem)

    /**
     * Variables will be initialized according to this for each labeling. The default is [RandomSelector] which
     * initializes uniform at random.
     */
    var selector: ValueSelector<O> = RandomSelector

    /**
     * Maximum number of steps for each of the [restarts].
     */
    var maxSteps: Int = max(100, problem.nbrVariables)

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
    var selection: SelectionOperator = TournamentSelection(max(2, populationSize / 10))

    /**
     * TODO
     */
    var elimination: SelectionOperator = OldestElimination()

    /**
     * TODO
     */
    var crossover: CrossoverOperator = KPointCrossover(1)

    /**
     * TODO
     */
    var mutation: MutationOperator = FastGAMutation(problem.nbrVariables)

    /**
     * TODO
     */
    var mutationProbability: Double = 0.02

    /**
     * TODO
     */
    var penaltyFunction: PenaltyFunction = SquaredPenalty()

    override fun optimizeOrThrow(function: O, assumptions: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val lowerBound = function.lowerBound()

        fun score(s: SearchState) = function.value(s).let { it + penaltyFunction.penalty(it, s.totalUnsatisfied) }

        val population = Array(populationSize) {
            stateFactory.build(labelingFactory.create(problem.nbrVariables), assumptions, selector, function, randomSequence.next())
        }
        val state = let {
            val ages = IntArray(populationSize)
            val scores = DoubleArray(populationSize) {
                val s = score(population[it])
                if (abs(s - lowerBound) < eps && population[it].totalUnsatisfied == 0) return population[it].labeling
                s
            }
            PopulationState(population, scores, ages)
        }

        val rng = randomSequence.next()

        var stalls = 0

        for (step in 1..maxSteps) {
            val eliminated = elimination.select(state, rng)
            val parent1: Int = selection.select(state, rng)
            val parent2: Int = selection.select(state, rng)
            crossover.crossover(parent1, parent2, eliminated, state, rng)
            if (rng.nextDouble() < mutationProbability)
                mutation.mutate(eliminated, state, rng)
            val score = score(population[eliminated])
            if (!state.update(eliminated, step, score)) stalls++
            else stalls = 0
            if (abs(score - lowerBound) < eps && population[eliminated].totalUnsatisfied == 0)
                return state.labelings[eliminated]
            else if (millis() > end || stalls >= stallSteps) break
        }

        for (i in 0 until populationSize)
            if (population[i].totalUnsatisfied == 0) return population[i].labeling

        when {
            millis() > end -> throw TimeoutException(timeout)
            stalls == stallSteps -> throw IterationsReachedException(stallSteps)
            else -> throw IterationsReachedException(maxSteps)
        }
    }
}


/**
 * This class changes the default parameters to be suitable for SAT solving.
 */
class GeneticAlgorithmSolver(problem: Problem) : GeneticAlgorithmOptimizer<SatObjective>(problem), Solver {
    override fun witnessOrThrow(assumptions: Literals) = optimizeOrThrow(SatObjective, assumptions)
}
