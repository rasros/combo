package combo.sat

import combo.math.ExtendedRandom
import combo.math.IntPermutation
import combo.math.binomial
import combo.model.ModelTest
import combo.model.UnsatisfiableException
import combo.sat.solvers.ExhaustiveSolver
import combo.test.assertContentEquals
import combo.util.HashIntSet
import kotlin.random.Random
import kotlin.test.*

class ProblemTest {
    private val problem = Problem(
            arrayOf(Disjunction(intArrayOf(0, 2, 4)),
                    Disjunction(intArrayOf(1, 5)),
                    Disjunction(intArrayOf(8, 10)),
                    Disjunction(intArrayOf(1, 3, 5, 9)),
                    Conjunction(intArrayOf(4)),
                    Cardinality(intArrayOf(4, 6))
            ), 6)

    @Test
    fun unitPropagationReduction() {
        problem.sentences.forEach { it.validate() }
        val reduced = problem.simplify(HashIntSet())
        assertTrue(reduced.nbrSentences < problem.nbrSentences)
    }

    @Test
    fun unitPropagationSameSolution() {
        val solutions1 = ExhaustiveSolver(problem).sequence().toSet()
        val units = HashIntSet()
        val reduced = problem.simplify(units, true)
        val solutions2 = ExhaustiveSolver(reduced).sequence(units.toArray().apply { sort() }).toSet()
        val unitsSentence = Conjunction(units.toArray().apply { sort() })
        val sentences: MutableList<Sentence> = reduced.sentences.toMutableList()
        sentences.add(unitsSentence)
        val solutions3 = ExhaustiveSolver(
                Problem(sentences.toTypedArray(), 6)).sequence().toSet()
        for (l in solutions1) {
            assertTrue(problem.satisfies(l))
            assertTrue(reduced.satisfies(l))
            assertTrue(solutions2.contains(l))
            assertTrue(solutions3.contains(l))
        }
        assertEquals(solutions1.size, solutions2.size)
        assertEquals(solutions1.size, solutions3.size)
    }

    @Test
    fun unitPropagationUnsat() {
        assertFailsWith(UnsatisfiableException::class) {
            Problem(arrayOf(Disjunction(intArrayOf(0)), Disjunction(intArrayOf(1))), 1).unitPropagation()
        }
    }

    @Test
    fun randomPropagation() {
        val r = ExtendedRandom(Random.Default)
        val p = ModelTest.large2.problem
        val perm = IntPermutation(p.nbrVariables, r.rng)
        val lits = (0 until r.binomial(0.7, p.nbrVariables)).asSequence()
                .map { perm.encode(it) }
                .map { it.asLiteral(r.rng.nextBoolean()) }
                .toList().toIntArray().apply { sort() }
        val sents: Array<Sentence> = p.sentences.toList().toTypedArray()
        val p2 = Problem(sents + Conjunction(lits), p.nbrVariables)
        val reduced = try {
            p.simplify(HashIntSet().apply { addAll(lits) }, true)
        } catch (e: UnsatisfiableException) {
            return
        }
        LabelingPermutation.sequence(p.nbrVariables, r.rng).take(100).forEach {
            assertEquals(p2.satisfies(it), reduced.satisfies(it))
        }
    }

    @Test
    fun satisfies() {
        val sentences = arrayOf(Cardinality(intArrayOf(0, 2, 4), 1, Cardinality.Operator.AT_MOST))
        val problem = Problem(sentences, 3)
        assertFalse(problem.satisfies(BitFieldLabeling(3, LongArray(1) { 0b110 })))
        assertTrue(problem.satisfies(BitFieldLabeling(3, LongArray(1) { 0b000 })))
        assertTrue(problem.satisfies(BitFieldLabeling(3, LongArray(1) { 0b010 })))
    }


    @Test
    fun clauseMatch() {
        val problem = Problem(arrayOf(
                Disjunction(intArrayOf(0, 2, 4)),
                Conjunction(intArrayOf(1))), 3)
        assertContentEquals(intArrayOf(0, 1), problem.sentencesWith(0))
        assertContentEquals(intArrayOf(0), problem.sentencesWith(1))
        assertContentEquals(intArrayOf(0), problem.sentencesWith(2))
    }
}

