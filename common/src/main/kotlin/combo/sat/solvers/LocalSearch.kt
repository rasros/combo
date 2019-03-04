package combo.sat.solvers

import combo.math.IntPermutation
import combo.math.RandomSequence
import combo.sat.*
import combo.util.millis
import combo.util.nanos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * This solver implements WalkSAT for sat solving and Hill climbing for optimization.
 * @param problem the problem contains the [Constraint]s and the number of variables.
 */
open class LocalSearchOptimizer<O : ObjectiveFunction>(val problem: Problem) : Optimizer<O> {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
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
    var pRandomWalk: Double = 0.0

    /**
     * Determines the [Instance] that will be created for solving, for very sparse problems use
     * [IntSetInstanceFactory] otherwise [BitArrayFactory].
     */
    var instanceFactory: InstanceFactory = BitArrayFactory

    /**
     * This contains cached information about satisfied constraints during search. [PropTrackingInstanceFactory] is more
     * efficient for optimizing but uses more memory than [BasicTrackingInstanceFactory].
     */
    var trackingInstanceFactory: TrackingInstanceFactory = BasicTrackingInstanceFactory(problem)

    /**
     * Variables will be initialized according to this during each iteration.T he default is [RandomInitializer] which
     * initializes uniform at random, consider switching to [combo.sat.WeightInitializer] for [LinearObjective].
     */
    var initializer: ValueInitializer<O> = RandomInitializer()

    /**
     * Threshold of improvement to stop current iteration in the search.
     */
    var eps: Double = 1E-4

    /**
     * Maximum number of variables to consider during each search, set to [Int.MAX_VALUE] to disable.
     */
    var maxConsideration: Int = max(20, problem.nbrVariables / 5)

    private var randomSequence = RandomSequence(nanos())

    override fun optimizeOrThrow(function: O, assumptions: Literals): Instance {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE

        val adjustedMaxConsideration = max(2, min(maxConsideration, problem.nbrVariables))

        var bestValue = Double.POSITIVE_INFINITY
        var bestInstance: Instance? = null

        val lowerBound = function.lowerBound()

        for (restart in 1..restarts) {
            val rng = randomSequence.next()

            val instance = instanceFactory.create(problem.nbrVariables)
            val tracker = trackingInstanceFactory.build(instance, assumptions, initializer, function, rng)

            fun setReturnValue(value: Double) {
                if (value < bestValue && tracker.totalUnsatisfied == 0) {
                    bestValue = value
                    bestInstance = tracker.instance.copy()
                }
            }

            var prevValue = function.value(instance)
            setReturnValue(prevValue)

            if (tracker.totalUnsatisfied == 0 && (abs(bestValue - lowerBound) < eps || problem.nbrVariables == 0))
                return tracker.instance


            for (step in 1..maxSteps) {
                val n: Int
                val ix: Int = if (pRandomWalk > rng.nextDouble()) {
                    if (tracker.totalUnsatisfied > 0) tracker.randomUnsatisfied(rng).literals.random(rng).toIx()
                    else rng.nextInt(problem.nbrVariables)
                } else {
                    val itr: IntIterator = if (tracker.totalUnsatisfied > 0) {
                        val literals = tracker.randomUnsatisfied(rng).literals
                        n = min(adjustedMaxConsideration, literals.size)
                        literals.permutation(rng)
                    } else {
                        n = min(adjustedMaxConsideration, problem.nbrVariables)
                        if (problem.nbrVariables > adjustedMaxConsideration) IntPermutation(problem.nbrVariables, rng).iterator()
                        else (0 until problem.nbrVariables).iterator()
                    }
                    var maxSatImp = Int.MIN_VALUE
                    var maxOptImp = 0.0
                    var bestIx = -1
                    for (k in 0 until n) {
                        val ix = itr.nextInt().toIx()
                        val satScore = tracker.improvement(ix)
                        val optScore = function.improvement(instance, ix, tracker.literalPropagations(ix))
                        if (satScore > maxSatImp || (satScore == maxSatImp && optScore > maxOptImp)) {
                            bestIx = ix
                            maxSatImp = satScore
                            maxOptImp = optScore
                        }
                    }
                    bestIx
                }

                if (ix < 0) break
                val improvement = function.improvement(instance, ix, tracker.literalPropagations(ix))
                val score = prevValue - improvement
                tracker.flip(ix)
                setReturnValue(score)

                if (step.rem(10) == 0) prevValue = function.value(instance)
                else prevValue -= improvement
                if (tracker.totalUnsatisfied == 0) {
                    if (abs(bestValue - lowerBound) < eps) return tracker.instance
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

    override fun witnessOrThrow(assumptions: Literals) = optimizeOrThrow(SatObjective, assumptions)
}
