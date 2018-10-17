package combo.sat

import combo.model.ValidationException
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class SolverTest {

    abstract fun solver(problem: Problem): Solver
    abstract fun unsatSolver(problem: Problem): Solver
    abstract fun timeoutSolver(problem: Problem): Solver

    private fun unsatWitness(problem: Problem) {
        assertFailsWith(ValidationException::class) {
            unsatSolver(problem).witnessOrThrow()
        }
    }

    @Test
    fun smallUnsatWitness() = unsatWitness(smallUnsat)

    @Test
    fun mediumUnsatWitness() = unsatWitness(mediumUnsat)

    private fun unsatSequence(problem: Problem) {
        assertEquals(0, unsatSolver(problem).sequence().count())
    }

    @Test
    fun smallUnsatSequence() = unsatSequence(smallUnsat)

    @Test
    fun mediumUnsatSequence() = unsatSequence(mediumUnsat)

    private fun satWitness(problem: Problem) {
        assertTrue(problem.satisfies(solver(problem).witnessOrThrow()))
    }

    @Test
    fun smallSatWitness() = satWitness(smallSat)

    @Test
    fun mediumSatWitness() = satWitness(mediumSat)

    @Test
    fun largeSatWitness() = satWitness(largeSat)

    private fun satWitnesscontextLiterals(problem: Problem, contextLiterals: Literals) {
        val labeling = solver(problem).witnessOrThrow(contextLiterals)
        for (literal in contextLiterals) assertTrue(labeling.asLiteral(literal.asIx()) == literal)
        assertTrue(problem.satisfies(labeling))
    }

    @Test
    fun smallSatWitnesscontextLiterals() {
        satWitnesscontextLiterals(smallSat, intArrayOf(4))
        satWitnesscontextLiterals(smallSat, intArrayOf(1, 2, 5, 7))
    }

    @Test
    fun mediumSatWitnesscontextLiterals() {
        satWitnesscontextLiterals(mediumSat, intArrayOf(10))
        satWitnesscontextLiterals(mediumSat, intArrayOf(0, 9, 19))
        satWitnesscontextLiterals(mediumSat, intArrayOf(0, 3, 5, 7, 9, 10, 13, 15, 17, 19))
    }

    @Test
    fun largeSatWitnesscontextLiterals() {
        satWitnesscontextLiterals(largeSat, intArrayOf(10))
        satWitnesscontextLiterals(largeSat, intArrayOf(10, 21))
        satWitnesscontextLiterals(largeSat, intArrayOf(
                1, 3, 5, 7, 9, 10, 13, 14, 17, 19, 21, 23, 25, 27, 29))
    }

    private fun satWitnesscontextLiteralsToUnsat(problem: Problem, contextLiterals: Literals) {
        assertFailsWith(ValidationException::class) {
            solver(problem).witnessOrThrow(contextLiterals)
        }
    }

    @Test
    fun smallSatWitnesscontextLiteralsToUnsat() = satWitnesscontextLiteralsToUnsat(smallSat, intArrayOf(4, 6))

    @Test
    fun mediumSatWitnesscontextLiteralsToUnsat() = satWitnesscontextLiteralsToUnsat(mediumSat, intArrayOf(4, 6))

    @Test
    fun largeSatWitnesscontextLiteralsToUnsat() = satWitnesscontextLiteralsToUnsat(largeSat, intArrayOf(0, 8))

    @Test
    fun openSequenceSize() {
        val problem = open(4)
        val solver = solver(problem)
        val toSet = solver.sequence().take(200).toSet()
        assertEquals(2.0.pow(problem.nbrVariables).toInt(), toSet.size)
    }

    private fun satSequencecontextLiterals(problem: Problem, contextLiterals: Literals) {
        var set = false
        solver(problem)
                .sequence(contextLiterals).take(4).forEach { labeling ->
                    for (literal in contextLiterals) assertTrue(labeling.asLiteral(literal.asIx()) == literal)
                    set = true
                    assertTrue(problem.satisfies(labeling))
                }
        assertTrue(set)
    }

    @Test
    fun smallSatSequencecontextLiterals() =
            satSequencecontextLiterals(smallSat, intArrayOf(4))

    @Test
    fun mediumSatSequencecontextLiterals() =
            satSequencecontextLiterals(mediumSat, intArrayOf(7))

    @Test
    fun largeSatSequencecontextLiterals() =
            satSequencecontextLiterals(largeSat, intArrayOf(0))

    private fun satSequencecontextLiteralsToUnsat(problem: Problem, contextLiterals: Literals) {
        assertEquals(0, unsatSolver(problem)
                .sequence(contextLiterals).count())
    }

    @Test
    fun smallSatSequencecontextLiteralsToUnsat() = satSequencecontextLiteralsToUnsat(smallSat, intArrayOf(4, 6))

    @Test
    fun mediumSatSequencecontextLiteralsToUnsat() = satSequencecontextLiteralsToUnsat(mediumSat, intArrayOf(4, 6))

    @Test
    fun largeSatSequencecontextLiteralsToUnsat() = satSequencecontextLiteralsToUnsat(largeSat, intArrayOf(0, 8))

    @Test
    fun timeoutWitness() {
        assertFailsWith(ValidationException::class) {
            timeoutSolver(hugeSat).witnessOrThrow()
        }
    }

    @Test
    fun timeoutSequence() {
        timeoutSolver(hugeSat).sequence().count()
    }
}
