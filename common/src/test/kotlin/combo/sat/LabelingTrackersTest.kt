package combo.sat

import combo.sat.solvers.SolverTest
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class LabelingTrackerTest {
    abstract fun tracker(l: MutableLabeling, p: Problem, pt: UnitPropagationTable, assumptions: Literals, rng: Random): LabelingTracker

    @Test
    fun unsatisfiableTracker() {
        for ((i, d) in SolverTest.smallUnsatProblems.withIndex()) {
            val (p, pt) = d
            val l = BitFieldLabeling(p.nbrVariables)
            val t = PropLabelingTracker(l, p, pt, EMPTY_INT_ARRAY, Random, RandomSelector(Random))
            assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
        }
    }

    @Test
    fun satOrUnsat() {
        fun helper(p: Problem, pt: UnitPropagationTable, i: Int) {
            val l = BitFieldLabeling(p.nbrVariables)
            val t = PropLabelingTracker(l, p, pt, EMPTY_INT_ARRAY, Random, RandomSelector(Random))
            if (p.satisfies(l)) assertTrue(t.unsatisfied.isEmpty(), "Model $i")
            else {
                assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
                assertEquals(p.sentences.count { !it.satisfies(l) }, t.unsatisfied.size)
            }
        }

        val list = SolverTest.smallUnsatProblems + SolverTest.smallProblems + SolverTest.largeProblems
        for ((i, d) in list.withIndex()) {
            val (p, pt) = d
            helper(p, pt, i)
        }
    }

    @Test
    fun undo() {
        fun helper(p: Problem, pt: UnitPropagationTable) {
            val l = BitFieldLabeling(p.nbrVariables)
            val t = PropLabelingTracker(l, p, pt, EMPTY_INT_ARRAY, Random, RandomSelector(Random))
            val ix = Random.nextInt(p.nbrVariables)
            val lit = !l.asLiteral(ix)
            val l1 = l.copy()
            t.set(lit)
            t.undo(lit)
            assertEquals(l1, l)
        }

        val list = SolverTest.smallUnsatProblems + SolverTest.smallProblems + SolverTest.largeProblems
        for (d in list) {
            val (p, pt) = d
            helper(p, pt)
        }
    }

    @Test
    fun updateUnsatisfied() {
        fun helper(p: Problem, pt: UnitPropagationTable, i: Int) {
            val l = BitFieldLabeling(p.nbrVariables)
            val t = PropLabelingTracker(l, p, pt, EMPTY_INT_ARRAY, Random, RandomSelector(Random))
            val ix = Random.nextInt(p.nbrVariables)
            val lit = !l.asLiteral(ix)
            t.set(lit)
            t.updateUnsatisfied(lit)
            if (p.satisfies(l)) assertTrue(t.unsatisfied.isEmpty(), "Model $i")
            else {
                assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
                assertEquals(p.sentences.count { !it.satisfies(l) }, t.unsatisfied.size)
            }
        }

        val list = SolverTest.smallUnsatProblems + SolverTest.smallProblems + SolverTest.largeProblems
        for ((i, d) in list.withIndex()) {
            val (p, pt) = d
            helper(p, pt, i)
        }
    }
}

class FlipLabelingTrackerTest : LabelingTrackerTest() {
    override fun tracker(l: MutableLabeling, p: Problem, pt: UnitPropagationTable, assumptions: Literals, rng: Random) =
            FlipLabelingTracker(l, p, assumptions, RandomSelector(Random))
}

class PropLabelingTrackerTest : LabelingTrackerTest() {
    override fun tracker(l: MutableLabeling, p: Problem, pt: UnitPropagationTable, assumptions: Literals, rng: Random) =
            PropLabelingTracker(l, p, pt, assumptions, rng, RandomSelector(Random))
}
