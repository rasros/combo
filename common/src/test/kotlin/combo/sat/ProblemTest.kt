package combo.sat

import combo.math.IntPermutation
import combo.math.nextBinomial
import combo.model.ModelTest
import combo.sat.solvers.ExhaustiveSolver
import combo.test.assertContentEquals
import combo.util.IntList
import combo.util.IntSet
import kotlin.random.Random
import kotlin.test.*

class ProblemTest {
    private val problem = Problem(
            arrayOf(Disjunction(IntList(intArrayOf(0, 2, 4))),
                    Disjunction(IntList(intArrayOf(1, 5))),
                    Disjunction(IntList(intArrayOf(8, 10))),
                    Disjunction(IntList(intArrayOf(1, 3, 5, 9))),
                    Conjunction(IntList(intArrayOf(4))),
                    Cardinality(IntList(intArrayOf(4, 6)), 1, Relation.LE)
            ), 6)

    @Test
    fun unitPropagationReduction() {
        val units = IntSet()
        val reduced = problem.unitPropagation(units)
        assertTrue(reduced.size < problem.nbrConstraints)
    }

    @Test
    fun unitPropagationSameSolution() {
        val solutions1 = ExhaustiveSolver(problem).sequence().toSet()
        val units = IntSet()
        var reducedConstraints = problem.unitPropagation(units)
        if (units.isNotEmpty()) reducedConstraints += Conjunction(units)
        val reducedProblem = Problem(reducedConstraints, problem.nbrVariables)
        val solutions2 = ExhaustiveSolver(reducedProblem).sequence(units.toArray().apply { sort() }).toSet()
        val unitsSentence = Conjunction(units)
        val constraints: MutableList<Constraint> = reducedProblem.constraints.toMutableList()
        constraints.add(unitsSentence)
        val solutions3 = ExhaustiveSolver(
                Problem(constraints.toTypedArray(), 6)).sequence().toSet()
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
            Problem(arrayOf(Disjunction(IntList(intArrayOf(0))), Disjunction(IntList(intArrayOf(1)))), 1).unitPropagation()
        }
    }

    @Test
    fun randomPropagation() {
        val rng = Random.Default
        val p = ModelTest.LARGE2.problem
        val perm = IntPermutation(p.nbrVariables, rng)
        val lits = (0 until rng.nextBinomial(0.7, p.nbrVariables)).asSequence()
                .map { perm.encode(it) }
                .map { it.toLiteral(rng.nextBoolean()) }
                .toList().toIntArray().apply { sort() }
        val sents: Array<Constraint> = p.constraints.toList().toTypedArray()
        val p2 = Problem(sents + Conjunction(IntList(lits)), p.nbrVariables)
        val reduced = try {
            val units = IntSet().apply { addAll(lits) }
            var reduced = p.unitPropagation(units)
            if (units.isNotEmpty()) reduced += Conjunction(units)
            Problem(reduced, p.nbrVariables)
        } catch (e: UnsatisfiableException) {
            return
        }
        LabelingPermutation(p.nbrVariables, BitFieldLabelingFactory, rng).iterator().asSequence().take(100).forEach {
            assertEquals(p2.satisfies(it), reduced.satisfies(it))
        }
    }

    @Test
    fun satisfies() {
        val sentences = arrayOf(Cardinality(IntList(intArrayOf(0, 2, 4)), 1, Relation.LE))
        val problem = Problem(sentences, 3)
        assertFalse(problem.satisfies(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertTrue(problem.satisfies(BitFieldLabeling(3, LongArray(1) { 0b000 })))
        assertTrue(problem.satisfies(BitFieldLabeling(3, LongArray(1) { 0b010 })))
    }


    @Test
    fun clauseMatch() {
        val problem = Problem(arrayOf(
                Disjunction(IntList(intArrayOf(0, 2, 4))),
                Conjunction(IntList(intArrayOf(1)))), 3)
        assertContentEquals(intArrayOf(0, 1), problem.constraintsWith(0))
        assertContentEquals(intArrayOf(0), problem.constraintsWith(1))
        assertContentEquals(intArrayOf(0), problem.constraintsWith(2))
    }
}

