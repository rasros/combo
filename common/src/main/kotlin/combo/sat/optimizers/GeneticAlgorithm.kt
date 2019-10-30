package combo.sat.optimizers

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
 * @param randomSeed Set the random seed to a specific value to have a reproducible algorithm.
 * @param timeout The solver will abort after timeout in milliseconds have been reached, without a real-time guarantee.
 * @param candidateSize The number of solution candidates that is generated by the search. This is the most important parameter to tweak.
 * @param instanceFactory Determines the [Instance] that will be created for solving.
 * @param initializer This determines how instances are given their starting values.
 * @param restarts The search will be restarted up to [restarts] number of time and the best value will be selected from each restart.
 * @param restartKeeps Percentage of candidate solutions that will be kept using [selection] method in the case of a restart.
 * @param maxSteps Maximum number of steps for each of the [restarts].
 * @param eps Threshold of improvement to stop current iteration in the search.
 * @param stallSteps Maximum number of steps that can be performed with no improvement on feasible the objective function.
 * @param selection How candidate solutions are selected to create new candidate (see also [elimination]).
 * @param elimination How candidate solutions are eliminated to make room for new candidate (see also [selection]).
 * @param recombination The recombinaton operator controls the way that new candidates are generated after the initial population.
 * @param recombinationProbability Probability that the [recombination] operator is used when eliminating a candidate.
 * @param mutation Adds additional diversity to the candidate solutions (see also [mutationProbability])
 * @param mutationProbability Probability to apply [mutation] on new candidate.
 * @param guessMutator Applied to the guess once for each candidate.
 * @param penalty Added to objective function to discourage convergence to an infeasible solution.
 * @param propagateAssumptions Whether unit propagation before search is performed when assumptions are used.
 * @param scoreSample Use this for introspection during development to sample all scores.
 * @param minScoreSample Use this for introspection during development to sample all minimum scores.
 */
class GeneticAlgorithm(val problem: Problem,
                       override val randomSeed: Int = nanos().toInt(),
                       override val timeout: Long = -1L,
                       val candidateSize: Int = max(20, min(problem.nbrValues * 5, 300)),
                       val instanceFactory: InstanceFactory = BitArrayFactory,
                       val initializer: InstanceInitializer<*> = ConstraintCoercer(problem, WordRandomSet()),
                       val restarts: Int = 1,
                       val restartKeeps: Float = 0.2f,
                       val maxSteps: Int = max(500, problem.nbrValues),
                       val eps: Float = 1E-4f,
                       val stallSteps: Int = max(50, problem.nbrValues / 4),
                       val selection: SelectionOperator<Candidates> = TournamentSelection(max(2, candidateSize / 10)),
                       val elimination: SelectionOperator<Candidates> = TournamentElimination(max(2, candidateSize / 5)),
                       val recombination: RecombinationOperator<ValidatorCandidates> = KPointRecombination(1),
                       val recombinationProbability: Float = 1.0f,
                       val mutation: MutationOperator<ValidatorCandidates> = FixedMutation(),
                       val mutationProbability: Float = 1.0f,
                       val guessMutator: MutationOperator<ValidatorCandidates> = RateMutationOperator(FastGAMutation(problem.nbrValues)),
                       val penalty: PenaltyFunction = SquaredPenalty(),
                       val propagateAssumptions: Boolean = true,
                       val scoreSample: DataSample = VoidSample,
                       val minScoreSample: DataSample = VoidSample)
    : Optimizer<ObjectiveFunction> {

    private var randomSequence = RandomSequence(randomSeed)

    override fun optimizeOrThrow(function: ObjectiveFunction, assumptions: IntCollection, guess: Instance?): Instance {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        val rng = randomSequence.next()
        val lowerBound = function.lowerBound()
        val upperBound = function.upperBound()

        val assumption: Constraint
        val p: Problem
        if (propagateAssumptions && assumptions.isNotEmpty()) {
            val units = IntHashSet()
            units.addAll(assumptions)
            p = Problem(problem.nbrValues, problem.unitPropagation(units, true))
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
                    val instance = instanceFactory.create(p.nbrValues)
                    @Suppress("UNCHECKED_CAST")
                    (initializer as InstanceInitializer<ObjectiveFunction>).initialize(instance, assumption, rng, function)
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
                    for (i in parent.indices) if (target.isSet(i) != parent.isSet(i)) target.flip(i)
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
                    val newInstance = instanceFactory.create(p.nbrValues)
                    @Suppress("UNCHECKED_CAST")
                    (initializer as InstanceInitializer<ObjectiveFunction>).initialize(newInstance, assumption, rng, function)
                    candidates.instances[i] = Validator(p, newInstance, assumption)
                    @Suppress("UNCHECKED_CAST")
                    (candidates.instances as Array<Instance>)[i] = candidates.instances[i]
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

    override fun witnessOrThrow(assumptions: IntCollection, guess: Instance?) =
            optimizeOrThrow(SatObjective, assumptions, guess)

    class Builder(val problem: Problem) {
        private var randomSeed: Int = nanos().toInt()
        private var timeout: Long = -1L
        private var candidateSize: Int = max(20, min(problem.nbrValues * 5, 300))
        private var instanceFactory: InstanceFactory = BitArrayFactory
        private var restarts: Int = 1
        private var restartKeeps: Float = 0.2f
        private var maxSteps: Int = max(500, problem.nbrValues)
        private var eps: Float = 1E-4f
        private var stallSteps: Int = max(50, problem.nbrValues / 4)
        private var selection: SelectionOperator<Candidates> = TournamentSelection(max(2, candidateSize / 10))
        private var elimination: SelectionOperator<Candidates> = TournamentElimination(max(2, candidateSize / 5))
        private var recombination: RecombinationOperator<ValidatorCandidates> = KPointRecombination(1)
        private var recombinationProbability: Float = 1.0f
        private var mutation: MutationOperator<ValidatorCandidates>? = null
        private var mutationProbability: Float = 1.0f
        private var guessMutator: MutationOperator<ValidatorCandidates> = RateMutationOperator(FixedRateMutation())
        private var penalty: PenaltyFunction = SquaredPenalty()
        private var propagateAssumptions: Boolean = true
        private var scoreSample: DataSample = VoidSample
        private var minScoreSample: DataSample = VoidSample

        private var initializerType: InitializerType = InitializerType.PROPAGATE_COERCE
        private var initializerBias: Float = 0.5f
        private var initializerNoise: Float = 0.5f

        /** The number of solution candidates that is generated by the search. This is the most important parameter to tweak. */
        fun candidateSize(candidateSize: Int) = apply { this.candidateSize = candidateSize }

        /** Set the random seed to a specific value to have a reproducible algorithm. */
        fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }

        /** The solver will abort after timeout in milliseconds have been reached, without a real-time guarantee. */
        fun timeout(timeout: Long) = apply { this.timeout = timeout }

        /** The search will be restarted up to [restarts] number of time and the best value will be selected from each restart. */
        fun restarts(restarts: Int) = apply { this.restarts = restarts }

        /** Percentage of candidate solutions that will be kept using [selection] method in the case of a restart. */
        fun restartKeeps(restartKeeps: Float) = apply { this.restartKeeps = restartKeeps }

        /** Maximum number of steps for each of the [restarts]. */
        fun maxSteps(maxSteps: Int) = apply { this.maxSteps = maxSteps }

        /** Maximum number of steps that can be performed with no improvement on feasible the objective function. */
        fun stallSteps(stallSteps: Int) = apply { this.stallSteps = stallSteps }

        /** Whether to use sparse or dense bit array as instance. */
        fun sparse(sparse: Boolean) = apply { if (sparse) instanceFactory = SparseBitArrayFactory else BitArrayFactory }

        /** Type of initialization strategy. */
        fun initializer(initializer: InitializerType) = apply { this.initializerType = initializer }

        /** Preference when randomizing for initializing each value with 1 (bias close to 1) or 0 (bias close to 0). */
        fun initializerBias(initializerBias: Float) = apply { this.initializerBias = initializerBias }

        /** Noise added to weights for [InitializerType.WEIGHT_MAX] for [LinearObjective]. */
        fun initializerNoise(initializerNoise: Float) = apply { this.initializerNoise = initializerNoise }

        /** Threshold of improvement to stop current iteration in the search. */
        fun stallEps(eps: Float) = apply { this.eps = eps }

        /** How candidate solutions are selected to create new candidate (see also [elimination]). */
        fun selection(selection: SelectionOperator<Candidates>) = apply { this.selection = selection }

        /** How candidate solutions are eliminated to make room for new candidate (see also [selection]).*/
        fun elimination(elimination: SelectionOperator<Candidates>) = apply { this.elimination = elimination }

        /** The recombinaton operator controls the way that new candidates are generated after the initial population. */
        fun recombination(recombination: RecombinationOperator<ValidatorCandidates>) = apply { this.recombination = recombination }

        /** Probability that the [recombination] operator is used when eliminating a candidate. */
        fun recombinationProbability(recombinationProbability: Float) = apply { this.recombinationProbability = recombinationProbability }

        /** Adds additional diversity to the candidate solutions (see also [mutationProbability]) */
        fun mutation(mutation: MutationOperator<ValidatorCandidates>) = apply { this.mutation = mutation }

        /** Probability to apply [mutation] on new candidate. */
        fun mutationProbability(mutationProbability: Float) = apply { this.mutationProbability = mutationProbability }

        /** Applied to the guess once for each candidate. */
        fun guessMutator(guessMutator: MutationOperator<ValidatorCandidates>) = apply { this.guessMutator = guessMutator }

        /** Added to objective function to discourage convergence to an infeasible solution. */
        fun penalty(penalty: PenaltyFunction) = apply { this.penalty = penalty }

        /** Whether unit propagation before search is performed when assumptions are used. */
        fun propagateAssumptions(propagateAssumptions: Boolean) = apply { this.propagateAssumptions = propagateAssumptions }

        /** Use this for introspection during development to sample all scores. */
        fun scoreSample(scoreSample: DataSample) = apply { this.scoreSample = scoreSample }

        /** Use this for introspection during development to sample all minimum scores. */
        fun minScoreSample(minScoreSample: DataSample) = apply { this.minScoreSample = minScoreSample }

        /** Wrap this in a cached optimizer. */
        fun cached() = CachedOptimizer.Builder<ObjectiveFunction>(build())

        fun build(): GeneticAlgorithm {

            val randomizer = if (initializerBias > 0.999f) RandomSet(initializerBias)
            else if (initializerBias == 0.0f) NoInitializer(false)
            else if (initializerBias <= 0.01f) GeometricRandomSet(initializerBias)
            else WordRandomSet(initializerBias)

            val digraph = if (mutation == null || initializerType == InitializerType.PROPAGATE_COERCE ||
                    initializerType == InitializerType.WEIGHT_MAX_PROPAGATE_COERCE) TransitiveImplications(problem)
            else null

            val init = when (initializerType) {
                InitializerType.WEIGHT_MAX -> WeightSet(initializerNoise)
                InitializerType.RANDOM -> randomizer
                InitializerType.COERCE -> ConstraintCoercer(problem, randomizer)
                InitializerType.PROPAGATE_COERCE -> ImplicationConstraintCoercer(problem, digraph!!, randomizer)
                InitializerType.WEIGHT_MAX_PROPAGATE_COERCE -> ImplicationConstraintCoercer(problem, digraph!!, WeightSet(initializerNoise))
                InitializerType.NONE -> NoInitializer(true)
            }

            return GeneticAlgorithm(problem = problem, randomSeed = randomSeed, timeout = timeout,
                    candidateSize = candidateSize, instanceFactory = instanceFactory, initializer = init,
                    restarts = restarts, restartKeeps = restartKeeps, maxSteps = maxSteps, eps = eps,
                    stallSteps = stallSteps, selection = selection, elimination = elimination,
                    recombination = recombination, recombinationProbability = recombinationProbability,
                    mutation = mutation ?: PropagatingMutator(FixedRateMutation(), digraph!!),
                    mutationProbability = mutationProbability, guessMutator = guessMutator,
                    penalty = penalty, propagateAssumptions = propagateAssumptions, scoreSample = scoreSample,
                    minScoreSample = minScoreSample)
        }
    }
}

