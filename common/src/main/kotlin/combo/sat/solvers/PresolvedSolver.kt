package combo.sat.solvers

import combo.math.LongPermutation
import combo.math.RandomSequence
import combo.sat.Conjunction
import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.UnsatisfiableException
import combo.util.collectionOf
import combo.util.nanos
import kotlin.jvm.JvmOverloads

class PresolvedSolver @JvmOverloads constructor(private val solutions: Array<Labeling>,
                                                val randomSeed: Long = nanos()) : Solver, Optimizer<ObjectiveFunction> {

    private val randomSequence = RandomSequence(randomSeed)

    override fun witnessOrThrow(assumptions: Literals): Labeling {
        if (solutions.isEmpty()) throw UnsatisfiableException(message = "Empty pre-solved solutions.")
        val rng = randomSequence.next()
        if (assumptions.isNotEmpty()) {
            val c = Conjunction(collectionOf(assumptions))
            val permutation = LongPermutation(solutions.size.toLong(), rng)
            for (i in solutions.indices) {
                val l = solutions[permutation.encode(i.toLong()).toInt()]
                if (c.satisfies(l)) return l
            }
            throw UnsatisfiableException("No pre-solved solutions matches the required fixed literals.")
        } else return solutions[rng.nextInt(solutions.size)]
    }

    override fun sequence(assumptions: Literals): Sequence<Labeling> {
        val conjunction = Conjunction(collectionOf(assumptions))
        return solutions.asSequence().filter {
            conjunction.satisfies(it)
        }
    }

    override fun optimizeOrThrow(function: ObjectiveFunction, assumptions: Literals) = sequence(assumptions).minBy {
        function.value(it)
    } ?: throw UnsatisfiableException()
}
