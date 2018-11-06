package combo.sat

import combo.math.RngSequence
import combo.model.UnsatisfiableException
import combo.math.LongPermutation
import kotlin.jvm.JvmOverloads

class PresolvedSolver @JvmOverloads constructor(private val solutions: Array<Labeling>,
                                                override val config: SolverConfig = SolverConfig()) : Solver {

    private val incrementingRng = RngSequence(config.randomSeed)

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        if (solutions.isEmpty()) throw UnsatisfiableException(message = "Empty pre-solved solutions.")
        val rng = incrementingRng.next()
        if (contextLiterals.isNotEmpty()) {
            val c = Conjunction(contextLiterals);
            val permutation = LongPermutation(solutions.size.toLong(), rng)
            for (i in solutions.indices) {
                val l = solutions[permutation.encode(i.toLong()).toInt()]
                if (c.satisfies(l)) return l
            }
            throw UnsatisfiableException("No pre-solved solutions matches the required fixed literals.")
        } else return solutions[rng.int(solutions.size)]
    }

    override fun sequence(contextLiterals: Literals): Sequence<Labeling> {
        val conjunction = Conjunction(contextLiterals)
        return solutions.asSequence().filter {
            conjunction.satisfies(it)
        }
    }
}
