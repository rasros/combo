package combo.sat

import combo.math.Vector
import combo.model.TimeoutException
import combo.model.ValidationException
import combo.util.millis

class HillClimber(val problem: Problem,
                  override val config: SolverConfig,
                  val baseSolver: Solver = WalkSat(problem, config),
                  val timeout: Long = -1L,
                  val restarts: Int = 10,
                  var maxFlips: Int = 100) : LinearOptimizer {

    override fun optimizeOrThrow(weights: Vector, contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        var solution: Labeling? = null
        var best = Double.NEGATIVE_INFINITY
        val con = if (contextLiterals.isNotEmpty()) Conjunction(contextLiterals) else null

        fun update(s: Double, l: Labeling) {
            if (s > best) {
                best = s
                solution = l
            }
        }

        fun score(literal: Literal, labeling: MutableLabeling): Double {
            labeling.set(literal)
            labeling.setAll(problem.implicationGraph[literal])
            return if ((con != null && !con.satisfies(labeling)) ||
                    !problem.satisfies(labeling)) Double.NEGATIVE_INFINITY
            else {
                val d = labeling dot weights
                if (config.maximize) d else -d
            }
        }

        iteration@ for (i in 1..restarts) {
            flip@ for (j in 1..maxFlips) {
                var labeling = baseSolver.witness(contextLiterals) as? MutableLabeling
                if (labeling != null) {
                    val current = let {
                        val d = labeling!! dot weights
                        if (config.maximize) d else -d
                    }
                    update(current, labeling)
                    var updated = false
                    for (k in 0 until labeling!!.size) {
                        val copy = labeling.copy()
                        val s = score(!copy.asLiteral(k), copy)
                        if (s > current) {
                            labeling = copy
                            update(s, labeling)
                            updated = true
                            break
                        }
                        if (millis() > end) return solution ?: throw TimeoutException(timeout)
                    }
                    if (!updated) break@flip
                }
            }
        }
        return solution ?: throw ValidationException("Failed to generate any solution.")
    }
}
