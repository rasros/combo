package combo.sat

import combo.math.IntPermutation
import combo.model.TimeoutException
import combo.model.ValidationException
import combo.util.millis


class HillClimber(val problem: Problem,
                  override val config: SolverConfig,
                  val baseSolver: Solver = WalkSat(problem, config),
                  val timeout: Long = -1L,
                  val restarts: Int = 20,
                  val maxSteps: Int = 100,
                  val maxSidewaySteps: Int = 50,
                  val pSideway: Double = 1.0,
                  val pRandom: Double = 0.1) : Optimizer {

    override fun optimizeOrThrow(function: ObjectiveFunction, contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        var solution: Labeling? = null
        var best = Double.NEGATIVE_INFINITY
        val con = if (contextLiterals.isNotEmpty()) Conjunction(contextLiterals) else null

        fun update(score: Double, l: Labeling) {
            if (score > best || solution == null) {
                best = score
                solution = l.copy()
            }
        }

        fun score(literal: Literal, labeling: MutableLabeling): Double {
            labeling.set(literal)
            //labeling.setAll(problem.propagationGraph[literal]) // TODO
            return if ((con != null && !con.satisfies(labeling)) ||
                    !problem.satisfies(labeling)) Double.NEGATIVE_INFINITY
            else {
                val d = function.value(labeling)
                if (config.maximize) d else -d
            }
        }

        for (i in 1..restarts) {
            val labeling = baseSolver.witness(contextLiterals) as? MutableLabeling
            var sidewaySteps = 0
            if (labeling != null) {
                var score = let {
                    val d = function.value(labeling)
                    if (config.maximize) d else -d
                }
                update(score, labeling)
                val rng = config.nextRandom()
                for (j in 1..maxSteps) {

                    var nextVariableId: Int = -1
                    var itrScore = score
                    if (rng.nextDouble() < pRandom) {
                        nextVariableId = rng.nextInt(labeling.size)
                    } else {
                        for (k in IntPermutation(labeling.size, rng).iterator()) {
                            val copy = labeling.copy()
                            val s = score(!copy.asLiteral(k), copy)
                            if (s > score || ((itrScore == score && s == score && sidewaySteps < maxSidewaySteps && rng.nextDouble() < pSideway))) {
                                nextVariableId = k
                                itrScore = s
                            }
                            if (millis() > end) return solution ?: throw TimeoutException(timeout)
                        }
                    }

                    if (nextVariableId >= 0) {
                        if (itrScore == score) sidewaySteps++
                        score = score(!labeling.asLiteral(nextVariableId), labeling)
                        update(score, labeling)
                    }
                }
            }
        }
        if (solution == null || best.isInfinite()) throw ValidationException("Failed to generate any solution.")
        else return solution!!
    }
}
