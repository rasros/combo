package combo.sat

import combo.math.IntPermutation
import combo.math.nextBinomial
import combo.model.TestModels
import combo.sat.constraints.Cardinality
import combo.sat.constraints.Conjunction
import combo.sat.constraints.Disjunction
import combo.sat.constraints.Relation
import combo.sat.solvers.ExhaustiveSolver
import combo.test.assertContentEquals
import combo.util.IntHashSet
import combo.util.IntList
import combo.util.isNotEmpty
import kotlin.random.Random
import kotlin.test.*

class ProblemTest {
    private val problem = Problem(
            arrayOf(Disjunction(IntList(intArrayOf(1, 2, 3))),
                    Disjunction(IntList(intArrayOf(-1, -3))),
                    Disjunction(IntList(intArrayOf(5, 6))),
                    Disjunction(IntList(intArrayOf(-1, -2, -3, -5))),
                    Conjunction(IntList(intArrayOf(3))),
                    Cardinality(IntList(intArrayOf(3, 4)), 1, Relation.LE)
            ), 6)

    @Test
    fun unitPropagationReduction() {
        val units = IntHashSet()
        val reduced = problem.unitPropagation(units)
        assertTrue(reduced.size < problem.nbrConstraints)
    }

    @Test
    fun unitPropagationSameSolution() {
        val solutions1 = ExhaustiveSolver(problem).asSequence().toSet()
        val units = IntHashSet()
        var reducedConstraints = problem.unitPropagation(units, returnConstraints = true)
        if (units.isNotEmpty()) reducedConstraints += Conjunction(units)
        val reducedProblem = Problem(reducedConstraints, problem.nbrVariables)
        val solutions2 = ExhaustiveSolver(reducedProblem).asSequence(units.toArray().apply { sort() }).toSet()
        val unitsSentence = Conjunction(units)
        val constraints: MutableList<Constraint> = reducedProblem.constraints.toMutableList()
        constraints.add(unitsSentence)
        val solutions3 = ExhaustiveSolver(
                Problem(constraints.toTypedArray(), 6)).asSequence().toSet()
        for (l in solutions1) {
            assertTrue(problem.satisfies(l))
            assertTrue(reducedProblem.satisfies(l))
            assertTrue(solutions2.contains(l))
            assertTrue(solutions3.contains(l))
        }
        assertEquals(solutions1.size, solutions2.size)
        assertEquals(solutions1.size, solutions3.size)
    }

    @Test
    fun unitPropagationUnsat() {
        assertFailsWith(UnsatisfiableException::class) {
            Problem(arrayOf(Disjunction(IntList(intArrayOf(1))), Disjunction(IntList(intArrayOf(-1)))), 1).unitPropagation()
        }
    }

    @Test
    fun randomPropagation() {
        val rng = Random.Default
        val p = TestModels.LARGE2.problem
        val perm = IntPermutation(p.nbrVariables, rng)
        val lits = (0 until rng.nextBinomial(0.7f, p.nbrVariables)).asSequence()
                .map { perm.encode(it) }
                .map { it.toLiteral(rng.nextBoolean()) }
                .toList().toIntArray().apply { sort() }
        val sents: Array<Constraint> = p.constraints.toList().toTypedArray()
        val p2 = Problem(sents + Conjunction(IntList(lits)), p.nbrVariables)
        val reduced = try {
            val units = IntHashSet().apply { addAll(lits) }
            var reduced = p.unitPropagation(units)
            if (units.isNotEmpty()) reduced += Conjunction(units)
            Problem(reduced, p.nbrVariables)
        } catch (e: UnsatisfiableException) {
            return
        }
        InstancePermutation(p.nbrVariables, BitArrayBuilder, rng).iterator().asSequence().take(100).forEach {
            assertEquals(p2.satisfies(it), reduced.satisfies(it))
        }
    }

    @Test
    fun satisfies() {
        val sentences = arrayOf(Cardinality(IntList(intArrayOf(1, 2, 3)), 1, Relation.LE))
        val problem = Problem(sentences, 3)
        assertFalse(problem.satisfies(BitArray(3, IntArray(1) { 0b110 })))
        assertTrue(problem.satisfies(BitArray(3, IntArray(1) { 0b000 })))
        assertTrue(problem.satisfies(BitArray(3, IntArray(1) { 0b010 })))
    }

    @Test
    fun clauseMatch() {
        val problem = Problem(arrayOf(
                Disjunction(IntList(intArrayOf(1, 2, 3))),
                Conjunction(IntList(intArrayOf(-1)))), 3)
        assertContentEquals(intArrayOf(0, 1), problem.constraintsWith(0))
        assertContentEquals(intArrayOf(0), problem.constraintsWith(1))
        assertContentEquals(intArrayOf(0), problem.constraintsWith(2))
    }
}

