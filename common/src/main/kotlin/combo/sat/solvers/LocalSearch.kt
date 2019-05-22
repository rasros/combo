package combo.sat.solvers

import combo.math.IntPermutation
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * This solver implements local search for sat solving and optimization. For most easy optimization and solving tasks
 * this is the best option since there is very little overhead.
 *
 * Local search works by randomly generating a candidate solution. During each step a change in the [Instance] is taken
 * either randomly with probability [pRandomWalk] or a maximum greedy improvement is selected. The the candidate is
 * replaced by the improved candidate. If there are any unsatisfied constraints then the variables to inspect are taken
 * from the variables in the constraint, otherwise they are selected randomly. At most [maxConsideration] variables are
 * looked at for each step, if this parameter is set too low it can lead to premature termination of the algorithm.
 *
 * The specific algorithm used can be either simple Hill climbing, WalkSAT, WalkSAT with annealing, and/or Tabu search.
 * The type of algorithm is only decided through setting the various parameters,  [tabuListSize] (Tabu search),
 * [pRandomWalk] (WalkSAT), [pRandomWalkDecay] (Simulated annealing).
 *
 * @param problem the problem contains the [Constraint]s and the number of variables.
 */
open class LocalSearchOptimizer<O : ObjectiveFunction>(val problem: Problem) : Optimizer<O> {

    final override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            field = value
        }
    private var rng = Random(randomSeed)

    override var timeout: Long = -1L

    /**
     * The search will be restarted up to [restarts] number of time and the best value will be selected from each
     * restart. For SAT solving restarts will be set to [Int.MAX_VALUE].
     */
    var restarts: Int = 5

    /**
     * Maximum number of steps for each of the [restarts].
     */
    var maxSteps: Int = max(100, problem.nbrVariables)

    /**
     * Chance of talking a random walk according to the WalkSAT algorithm.
     */
    var pRandomWalk: Float = 0.8f

    /**
     * Reduce the [pRandomWalk] by this during each step of the algorithm, using the iteration formula:
     * pRandomWalk_t+1 = pRandomWalk_t * pRandomWalkDecay
     */
    var pRandomWalkDecay: Float = 0.95f

    /**
     * Keep a ring-buffer with blocked assignments during search. Size is always a power of 2.
     */
    var tabuListSize: Int = Int.power2(min(problem.nbrVariables, 2))
        set(value) {
            field = if (value == 0) 0
            else Int.power2(value)
            tabuMask = tabuListSize - 1
        }
    private var tabuMask = tabuListSize - 1

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [SparseBitArrayBuilder] otherwise [BitArrayBuilder].
     */
    var instanceBuilder: InstanceBuilder = BitArrayBuilder

    /**
     * This determines how instances are given their starting values. This has a huge effect on the quality and
     * efficiency of solutions. Use [ConstraintCoercer] or [ImplicationConstraintCoercer] for a heuristic initial
     * solution or either [WordRandomSet] or [RandomSet] for a totally random initial guess.
     */
    var initializer: InstanceInitializer<O> = ConstraintCoercer(problem, WordRandomSet())

    /**
     * Threshold of improvement to stop current iteration in the search.
     */
    var eps: Float = 1E-4f

    /**
     * Maximum number of variables to consider during each search, set to [Int.MAX_VALUE] to disable.
     */
    var maxConsideration: Int = max(20, problem.nbrVariables / 5)


    override fun optimizeOrThrow(function: O, assumptions: IntCollection, guess: MutableInstance?): Instance {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE

        val adjustedMaxConsideration = max(2, min(maxConsideration, problem.nbrVariables))

        var bestValue = Float.POSITIVE_INFINITY
        var bestInstance: Instance? = null

        val lowerBound = function.lowerBound()
        val tabuBuffer = IntArray(tabuListSize) { -1 }
        var tabuI = 0
        val assumption: Constraint = if (assumptions.isEmpty()) Tautology else Conjunction(assumptions)

        for (restart in 1..restarts) {
            var pRandomWalk = pRandomWalk

            val instance: MutableInstance
            if (guess != null && restart == 1) {
                instance = guess
            } else {
                instance = instanceBuilder.create(problem.nbrVariables)
                initializer.initialize(instance, assumption, rng, function)
            }
            val validator = Validator.build(problem, instance, assumption)

            fun setReturnValue(value: Float) {
                if (value < bestValue && validator.totalUnsatisfied == 0) {
                    bestValue = value
                    bestInstance = validator.instance.copy()
                }
            }

            var prevValue = function.value(instance)
            setReturnValue(prevValue)

            if (validator.totalUnsatisfied == 0 && (abs(bestValue - lowerBound) < eps || problem.nbrVariables == 0))
                return validator.instance

            for (step in 1..maxSteps) {
                val n: Int
                val ix: Int = if (pRandomWalk > rng.nextFloat()) {
                    if (validator.totalUnsatisfied > 0) validator.randomUnsatisfied(rng).literals.random(rng).toIx()
                    else rng.nextInt(problem.nbrVariables)
                } else {
                    val itr: IntIterator = if (validator.totalUnsatisfied > 0) {
                        val literals = validator.randomUnsatisfied(rng).literals
                        n = min(adjustedMaxConsideration, literals.size)
                        literals.permutation(rng)
                    } else {
                        n = min(adjustedMaxConsideration, problem.nbrVariables)
                        if (problem.nbrVariables > adjustedMaxConsideration)
                            OffsetIterator(1, IntPermutation(problem.nbrVariables, rng).iterator())
                        else (1..problem.nbrVariables).iterator()
                    }
                    var maxSatImp = Int.MIN_VALUE
                    var maxOptImp = 0.0f
                    var bestIx = -1
                    for (k in 0 until n) {
                        val ix = itr.nextInt().toIx()
                        if (ix in tabuBuffer) continue
                        val satScore = validator.improvement(ix)
                        val optScore = function.improvement(instance, ix)
                        if (satScore > maxSatImp || (satScore == maxSatImp && optScore > maxOptImp)) {
                            bestIx = ix
                            maxSatImp = satScore
                            maxOptImp = optScore
                        }
                    }
                    bestIx
                }

                if (ix < 0)
                    break
                val improvement = function.improvement(instance, ix)
                val score = prevValue - improvement
                validator.flip(ix)
                setReturnValue(score)
                pRandomWalk *= pRandomWalkDecay
                if (tabuListSize > 0) {
                    tabuBuffer[tabuI] = ix
                    tabuI = (tabuI + 1) and tabuMask
                }

                if (step.rem(10) == 0) prevValue = function.value(instance)
                else prevValue -= improvement
                if (validator.totalUnsatisfied == 0) {
                    if (abs(bestValue - lowerBound) < eps) return validator.instance
                    else if (improvement < eps) break
                }
                if (millis() > end) break
            }
            if (millis() > end) break
        }
        return bestInstance
                ?: (if (millis() > end) throw TimeoutException(timeout) else throw IterationsReachedException(restarts))
    }
}

/**
 * This class changes the default parameters to be suitable for SAT solving.
 */
class LocalSearchSolver(problem: Problem) : LocalSearchOptimizer<SatObjective>(problem), Solver {
    init {
        restarts = Int.MAX_VALUE
    }

    override fun witnessOrThrow(assumptions: IntCollection, guess: MutableInstance?) =
            optimizeOrThrow(SatObjective, assumptions, guess)
}
