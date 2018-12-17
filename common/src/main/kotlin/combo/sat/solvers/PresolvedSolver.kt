package combo.sat.solvers

import combo.math.LongPermutation
import combo.math.RandomSequence
import combo.sat.Conjunction
import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.UnsatisfiableException
import kotlin.jvm.JvmOverloads

class PresolvedSolver @JvmOverloads constructor(private val solutions: Array<Labeling>,
                                                override val config: SolverConfig = SolverConfig()) : Solver {

    private val incrementingRandom = RandomSequence(config.randomSeed)

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        if (solutions.isEmpty()) throw UnsatisfiableException(message = "Empty pre-solved solutions.")
        val rng = incrementingRandom.next()
        if (contextLiterals.isNotEmpty()) {
            val c = Conjunction(contextLiterals);
            val permutation = LongPermutation(solutions.size.toLong(), rng)
            for (i in solutions.indices) {
                val l = solutions[permutation.encode(i.toLong()).toInt()]
                if (c.satisfies(l)) return l
            }
            throw UnsatisfiableException("No pre-solved solutions matches the required fixed literals.")
        } else return solutions[rng.nextInt(solutions.size)]
    }

    override fun sequence(contextLiterals: Literals): Sequence<Labeling> {
        val conjunction = Conjunction(contextLiterals)
        return solutions.asSequence().filter {
            conjunction.satisfies(it)
        }
    }
}
