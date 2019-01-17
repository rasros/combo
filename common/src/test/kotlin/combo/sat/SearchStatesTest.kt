package combo.sat

import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.SolverTest
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random
import kotlin.test.*

abstract class SearchStateTest {

    abstract fun factory(p: Problem): SearchStateFactory

    private companion object {
        // Since these tests are random the numbers here can be tweaked during debugging
        val REPEAT: Int = 3
    }

    private fun checkUnsatisfied(p: Problem, t: SearchState) {
        if (p.satisfies(t.labeling) && t.assumption.satisfies(t.labeling)) {
            assertEquals(0, t.totalUnsatisfied
                    - t.assumption.flipsToSatisfy(t.labeling))
        } else {
            assertEquals(p.constraints.sumBy { it.flipsToSatisfy(t.labeling) }
                    + t.assumption.flipsToSatisfy(t.labeling), t.totalUnsatisfied)
        }
    }

    @Test
    fun initializeSeededRandom() {
        val p = SolverTest.SMALL_PROBLEMS[2]
        val f = factory(p)
        for (z in 1..REPEAT) {
            val state1 = f.build(ByteArrayLabelingFactory.create(p.nbrVariables), EMPTY_INT_ARRAY, RandomSelector, null, Random(0))
            val state2 = f.build(ByteArrayLabelingFactory.create(p.nbrVariables), EMPTY_INT_ARRAY, RandomSelector, null, Random(0))
            assertEquals(state1.labeling, state2.labeling)
        }
    }

    @Test
    fun initializePreSolved() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val f = factory(p)
            for (z in 1..REPEAT) {
                val l = solver.witnessOrThrow() as MutableLabeling
                val copy = l.copy()
                val state = f.build(l, EMPTY_INT_ARRAY)
                assertEquals(0, state.totalUnsatisfied)
                assertEquals(copy, l)
            }
        }
    }

    @Test
    fun initializePreSolvedSameAssumptions() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val f = factory(p)
            for (z in 1..REPEAT) {
                val l = solver.witnessOrThrow() as MutableLabeling
                val copy = l.copy()
                val assumptions = intArrayOf(l.literal(Random.nextInt(p.nbrVariables)))
                val state = f.build(l, assumptions)
                checkUnsatisfied(p, state)
                assertEquals(copy, l)
            }
        }
    }

    @Test
    fun initializePreSolvedDifferentAssumptions() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val f = factory(p)
            val solver = ExhaustiveSolver(p)
            for (z in 1..REPEAT) {
                val l = solver.witnessOrThrow() as MutableLabeling
                val copy = l.copy()
                val assumptions = intArrayOf(!l.literal(Random.nextInt(p.nbrVariables)))
                val state = f.build(l, assumptions)
                checkUnsatisfied(p, state)
                assertNotEquals(copy, l)
            }
        }
    }

    @Test
    fun initialize() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabeling(p.nbrVariables)
                val state = f.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun initializeAssumptions() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabeling(p.nbrVariables)
                val assumptions = IntArray(Random.nextInt(p.nbrVariables)) { it.toLiteral(Random.nextBoolean()) }
                val state = f.build(l, assumptions, RandomSelector, null, Random)
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun flip() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabeling(p.nbrVariables)
                val state = f.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random(0))
                checkUnsatisfied(p, state)
                val ix = Random(0).nextInt(p.nbrVariables)
                val lit = state.labeling.literal(ix)
                state.flip(ix)
                checkUnsatisfied(p, state)
                assertNotEquals(lit, state.labeling.literal(ix))
            }
        }
    }

    @Test
    fun flipMany() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabeling(p.nbrVariables)
                val state = f.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                checkUnsatisfied(p, state)
                for (lit in 1..10) {
                    state.flip(Random.nextInt(p.nbrVariables))
                    checkUnsatisfied(p, state)
                }
            }
        }
    }

    @Test
    fun improvementNoChange() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabelingFactory.create(p.nbrVariables)
                val state = f.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                val ix = Random.nextInt(p.nbrVariables)
                val copy = l.copy()
                state.improvement(ix)
                assertEquals(copy, l)
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun improvement() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabelingFactory.create(p.nbrVariables)
                val state = f.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                val ix = Random.nextInt(p.nbrVariables)
                val imp = state.improvement(ix)
                val preFlips = p.flipsToSatisfy(l)
                state.flip(ix)
                val postFlips = p.flipsToSatisfy(l)
                assertEquals(postFlips, preFlips - imp, "$postFlips = $preFlips - $imp")
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun improvementAssumptions() {
        val p = Problem(arrayOf(), 10)
        val f = factory(p)
        for (z in 1..REPEAT) {
            val l = BitFieldLabelingFactory.create(p.nbrVariables)
            val state = f.build(l, intArrayOf(0, 2, 4, 6), RandomSelector, null, Random)
            if (state.labeling[0]) state.flip(0)
            val imp = state.improvement(0)
            assertTrue(imp > 0)
            checkUnsatisfied(p, state)
        }
    }

    @Test
    fun randomUnsatisfied() {
        val list = SolverTest.SMALL_UNSAT_PROBLEMS
        for (p in list) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val l = BitFieldLabelingFactory.create(p.nbrVariables)
                val state = f.build(l, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                val sent = state.randomUnsatisfied(Random)
                assertFalse(sent.satisfies(state.labeling))
            }
        }
    }

    @Test
    fun randomUnsatisfiedAssumption() {
        val p = Problem(arrayOf(), 5)
        val f = factory(p)
        for (z in 1..REPEAT) {
            val l = BitFieldLabeling(5)
            val state = f.build(l, intArrayOf(1, 3, 5, 7, 9), RandomSelector, null, Random)
            assertEquals(0, state.totalUnsatisfied)
            for (i in 0 until 5) if (!state.labeling[i]) state.flip(i)
            assertEquals(5, state.totalUnsatisfied)
            val assumption = state.randomUnsatisfied(Random)
            assertTrue(assumption is Conjunction)
            assertFalse(assumption.satisfies(state.labeling))
        }
    }
}

class BasicSearchStateTest : SearchStateTest() {
    override fun factory(p: Problem) = BasicSearchStateFactory(p)
}

class PropSearchStateTest : SearchStateTest() {
    override fun factory(p: Problem) = PropSearchStateFactory(p)
}

