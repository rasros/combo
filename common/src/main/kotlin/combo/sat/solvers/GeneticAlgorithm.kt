package combo.sat.solvers

import combo.ga.*
import combo.math.DataSample
import combo.math.VoidSample
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Steady state Genetic Algorithm Optimizer. This keeps a list of [candidateSize]] candidate solutions that are
 * continuously updated through the genetic operators [MutationOperator] and [RecombinationOperator].
 *
 * This optimizer has a lot of parameters to tweak and should probably not be used with default parameters. To inspect
 * the performance during development use the [scoreSample] and [minScoreSample].
 *
 * During each step of the algorithm an eliminated candidate is chosen through the [elimination] [SelectionOperator].
 * If [recombination] should occur according to the [recombinationProbability] then two parents are selected with
 * [selection] and subject to recombination. If [mutation] should occur according to the [mutationProbability] then the
 * new candidate (or a copied one if no recombination happens) are mutated. This is repeated [maxSteps] number of times
 * or if no improvement happens for [stallSteps] steps. . In addition, there is  an outer loop of [restarts] times that
 * restart the whole procedure with new candidates, but keeping [restartKeeps] number of candidates.
 *
 * @param problem the problem contains the [Constraint]s and the number of variables.
 */
open class GeneticAlgorithmOptimizer<O : ObjectiveFunction>(val problem: Problem) : Optimizer<O> {

    final override var randomSeed: Int
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.randomSeed
    private var randomSequence = RandomSequence(nanos().toInt())

    override var timeout: Long = -1L

    /**
     * The number of solution candidates that is generated by the search. This is the most important parameter to tweak.
     */
    var candidateSize: Int = max(20, min(problem.nbrBinaryVariables * 5, 300))

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [BitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceBuilder: InstanceBuilder = BitArrayBuilder

    /**
     * This determines how instances are given their starting values. This has a huge effect on the quality and
     * efficiency of solutions. Use [ConstraintCoercer] or [ImplicationConstraintCoercer] for a heuristic initial
     * solution or either [WordRandomSet] or [RandomSet] for a totally random initial guess.
     */
    var initializer: InstanceInitializer<O> = ConstraintCoercer(problem, WordRandomSet())

    /**
     * The search will be restarted up to [restarts] number of time and the best value will be selected from each
     * restart. For SAT solving restarts will be set to [Int.MAX_VALUE].
     */
    var restarts: Int = 1

    /**
     * Percentage of candidate solutions that will be kept using [selection] method in the case of a restart. The
     * other will be randomly generated new candidates.
     */
    var restartKeeps: Float = 0.2f

    /**
     * Maximum number of steps for each of the [restarts].
     */
    var maxSteps: Int = max(500, problem.nbrBinaryVariables)

    /**
     * Threshold of improvement to stop current iteration in the search.
     */
    var eps: Float = 1E-4f

    /**
     * This is the maximum number of steps that can be performed with no improvement on the [O] objective function.
     * It is only applied if a feasible solution is found.
     */
    var stallSteps: Int = max(50, problem.nbrBinaryVariables / 4)

    /**
     * The method by which candidate solutions are selected to form the next candidate that is added to the pool of
     * candidates (see also [elimination]).
     */
    var selection: SelectionOperator<Candidates> = TournamentSelection(max(2, candidateSize / 10))

    /**
     * The method by which candidate solution are selected to eliminate from the pool of candidates
     * (see also [selection]).
     */
    var elimination: SelectionOperator<Candidates> = TournamentElimination(max(2, candidateSize / 5))

    /**
     * The recombinaton operator controls the way that new candidates are generated after the initial population.
     */
    var recombination: RecombinationOperator<ValidatorCandidates> = KPointRecombination(1)

    /**
     * Probability that the [recombination] operator is used when eliminating a candidate.
     */
    var recombinationProbability: Float = 1.0f

    /**
     * The [mutation] operator in conjunction with the [mutationProbability] adds additional diversity to the candidate
     * solutions. The default flips one random variable with probability 1.
     */
    var mutation: MutationOperator<ValidatorCandidates> = FixedMutation()

    /**
     * This mutation operator is applied to the guess if applied once for each candidate.
     */
    var guessMutator: MutationOperator<ValidatorCandidates> = mutation

    /**
     * The [mutation] operator in conjunction with the [mutationProbability] adds additional diversity to the candidate
     * solutions. The default flips one random variable with probability 1.
     */
    var mutationProbability: Float = 1.0f

    /**
     * In order to discourage the optimizer to converge to an infeasible candidate solution we add an external penalty
     * to the objective function. If using linear objective, make sure to set this to [DisjunctPenalty].
     */
    var penalty: PenaltyFunction = SquaredPenalty()

    /**
     * If true then perform unit propagation before solving when assumptions are used.
     * This will sometimes drastically reduce the number of variables and make it easier to solve.
     */
    var propagateAssumptions: Boolean = true

    /**
     * Use this for introspection during development to sample all scores.
     */
    var scoreSample: DataSample = VoidSample

    /**
     * Use this for introspection during development to sample all minimum scores.
     */
    var minScoreSample: DataSample = VoidSample

    override fun optimizeOrThrow(function: O, assumptions: IntCollection, guess: MutableInstance?): Instance {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val rng = randomSequence.next()
        val lowerBound = function.lowerBound()
        val upperBound = function.upperBound()

        val assumption: Constraint
        val p: Problem
        if (propagateAssumptions && assumptions.isNotEmpty()) {
            val units = IntHashSet()
            units.addAll(assumptions)
            p = Problem(problem.nbrBinaryVariables, problem.unitPropagation(units, true))
            assumption = Conjunction(units)
        } else {
            p = problem
            assumption = if (assumptions.isEmpty()) Tautology else Conjunction(assumptions)
        }

        fun score(s: Validator) = function.value(s).let { it + penalty.penalty(it, s.totalUnsatisfied, lowerBound, upperBound) }

        val candidates = let {
            val validators: Array<Validator> = Array(candidateSize) {
                if (guess != null) Validator(p, guess.copy(), assumption)
                else {
                    val instance = instanceBuilder.create(p.nbrBinaryVariables)
                    initializer.initialize(instance, assumption, rng, function)
                    Validator(p, instance, assumption)
                }
            }
            val scores = FloatArray(candidateSize) {
                score(validators[it]).also { s ->
                    if (abs(s - lowerBound) < eps && validators[it].totalUnsatisfied == 0)
                        return validators[it].instance
                }
            }
            ValidatorCandidates(validators, IntArray(validators.size), scores)
        }
        if (guess != null) {
            for (i in 1 until candidateSize) {
                guessMutator.mutate(i, candidates, rng)
                val score = score(candidates.instances[i])
                candidates.update(i, 0L, score)
            }
        }

        for (restart in 1..restarts) {
            var stalls = 0

            for (step in 1L..maxSteps) {
                val eliminated = let {
                    val e = elimination.select(candidates, rng)
                    if (e < 0) rng.nextInt(candidateSize)
                    else e
                }
                val recombined = if (rng.nextFloat() < recombinationProbability) {
                    val parent1: Int = selection.select(candidates, rng)
                    val parent2: Int = selection.select(candidates, rng)
                    recombination.combine(parent1, parent2, eliminated, candidates, rng)
                    parent1 != parent2
                } else {
                    // Copy selected individual to eliminated and force mutation
                    val parent: Instance = candidates.instances[selection.select(candidates, rng)].instance
                    val target = candidates.instances[eliminated]
                    for (i in parent.indices) if (target[i] != parent[i]) target.flip(i)
                    false
                }
                if (!recombined || rng.nextFloat() < mutationProbability)
                    mutation.mutate(eliminated, candidates, rng)
                val updatedInstance = candidates.instances[eliminated]
                val score = score(updatedInstance)
                if (abs(score - lowerBound) < eps && updatedInstance.totalUnsatisfied == 0)
                    return updatedInstance

                if (!candidates.update(eliminated, step, score)) stalls++
                else stalls = 0

                scoreSample.accept(score)
                minScoreSample.accept(candidates.bestScore)

                if (millis() > end || (stalls >= stallSteps && restart < restarts) || candidates.bestScore == candidates.worstScore)
                    break
            }

            if (restart == restarts || millis() > end) break

            val keep = IntHashSet(nullValue = -1)
            var tries = 0
            while (keep.size < max(0.2f, restartKeeps) * candidateSize || tries++ < candidateSize)
                keep.add(selection.select(candidates, rng))
            tries = 0
            while (keep.size < restartKeeps * candidateSize) keep.add(tries)

            for (i in 0 until candidateSize) {
                if (i in keep) {
                    candidates.update(i, 1, candidates.scores[i])
                } else {
                    val newInstance = instanceBuilder.create(p.nbrBinaryVariables)
                    initializer.initialize(newInstance, assumption, rng, function)
                    candidates.instances[i] = Validator(p, newInstance, assumption)
                    @Suppress("UNCHECKED_CAST")
                    (candidates.instances as Array<MutableInstance>)[i] = candidates.instances[i]
                    val newScore = score(candidates.instances[i])
                    candidates.update(i, 0, newScore)
                }
            }
        }

        val ix = (0 until candidateSize).minBy {
            if (candidates.instances[it].totalUnsatisfied == 0) candidates.scores[it]
            else Float.POSITIVE_INFINITY
        }!!
        if (candidates.instances[ix].totalUnsatisfied == 0)
            return candidates.instances[ix].instance

        if (millis() > end)
            throw TimeoutException(timeout)
        else
            throw IterationsReachedException(restarts)
    }
}


/**
 * This class changes the default parameters to be suitable for SAT solving.
 */
class GeneticAlgorithmSolver(problem: Problem) : GeneticAlgorithmOptimizer<SatObjective>(problem), Solver {
    init {
        restarts = 1
        maxSteps = Int.MAX_VALUE
        penalty = LinearPenalty()
        elimination = OldestElimination()
    }

    override fun witnessOrThrow(assumptions: IntCollection, guess: MutableInstance?) = optimizeOrThrow(SatObjective, assumptions, guess)
}

