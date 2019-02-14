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
        if (p.satisfies(t.instance) && t.assumption.satisfies(t.instance)) {
            assertEquals(0, t.totalUnsatisfied
                    - t.assumption.flipsToSatisfy(t.instance))
        } else {
            assertEquals(p.constraints.sumBy { it.flipsToSatisfy(t.instance) }
                    + t.assumption.flipsToSatisfy(t.instance), t.totalUnsatisfied)
        }
    }

    @Test
    fun initializeSeededRandom() {
        val p = SolverTest.SMALL_PROBLEMS[2]
        val f = factory(p)
        for (z in 1..REPEAT) {
            val state1 = f.build(ByteArrayInstanceFactory.create(p.nbrVariables), EMPTY_INT_ARRAY, RandomSelector, null, Random(0))
            val state2 = f.build(ByteArrayInstanceFactory.create(p.nbrVariables), EMPTY_INT_ARRAY, RandomSelector, null, Random(0))
            assertEquals(state1.instance, state2.instance)
        }
    }

    @Test
    fun initializePreSolved() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val f = factory(p)
            for (z in 1..REPEAT) {
                val instance = solver.witnessOrThrow() as MutableInstance
                val copy = instance.copy()
                val state = f.buildPreDefined(instance, EMPTY_INT_ARRAY)
                assertEquals(0, state.totalUnsatisfied)
                assertEquals(copy, instance)
            }
        }
    }

    @Test
    fun initializePreSolvedSameAssumptions() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val f = factory(p)
            for (z in 1..REPEAT) {
                val instance = solver.witnessOrThrow() as MutableInstance
                val copy = instance.copy()
                val assumptions = intArrayOf(instance.literal(Random.nextInt(p.nbrVariables)))
                val state = f.buildPreDefined(instance, assumptions)
                checkUnsatisfied(p, state)
                assertEquals(copy, instance)
            }
        }
    }

    @Test
    fun initializePreSolvedDifferentAssumptions() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val f = factory(p)
            val solver = ExhaustiveSolver(p)
            for (z in 1..REPEAT) {
                val instance = solver.witnessOrThrow() as MutableInstance
                val copy = instance.copy()
                val assumptions = intArrayOf(!instance.literal(Random.nextInt(p.nbrVariables)))
                val state = f.buildPreDefined(instance, assumptions)
                checkUnsatisfied(p, state)
                assertNotEquals(copy, instance)
            }
        }
    }

    @Test
    fun initialize() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstance(p.nbrVariables)
                val state = f.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun initializeAssumptions() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstance(p.nbrVariables)
                val assumptions = IntArray(Random.nextInt(p.nbrVariables)) { it.toLiteral(Random.nextBoolean()) }
                val state = f.build(instance, assumptions, RandomSelector, null, Random)
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun flip() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstance(p.nbrVariables)
                val state = f.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random(0))
                checkUnsatisfied(p, state)
                val ix = Random(0).nextInt(p.nbrVariables)
                val lit = state.instance.literal(ix)
                state.flip(ix)
                checkUnsatisfied(p, state)
                assertNotEquals(lit, state.instance.literal(ix))
            }
        }
    }

    @Test
    fun flipMany() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstance(p.nbrVariables)
                val state = f.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random)
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
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstanceFactory.create(p.nbrVariables)
                val state = f.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                val ix = Random.nextInt(p.nbrVariables)
                val copy = instance.copy()
                state.improvement(ix)
                assertEquals(copy, instance)
                checkUnsatisfied(p, state)
            }
        }
    }

    @Test
    fun improvement() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.SMALL_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstanceFactory.create(p.nbrVariables)
                val state = f.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                val ix = Random.nextInt(p.nbrVariables)
                val imp = state.improvement(ix)
                val preFlips = p.flipsToSatisfy(instance)
                state.flip(ix)
                val postFlips = p.flipsToSatisfy(instance)
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
            val instance = BitFieldInstanceFactory.create(p.nbrVariables)
            val state = f.build(instance, intArrayOf(0, 2, 4, 6), RandomSelector, null, Random)
            if (state.instance[0]) state.flip(0)
            val imp = state.improvement(0)
            assertTrue(imp > 0)
            checkUnsatisfied(p, state)
        }
    }

    @Test
    fun randomUnsatisfied() {
        for (p in SolverTest.SMALL_UNSAT_PROBLEMS) {
            val f = try {
                factory(p)
            } catch (e: UnsatisfiableException) {
                continue
            }
            for (z in 1..REPEAT) {
                val instance = BitFieldInstanceFactory.create(p.nbrVariables)
                val state = f.build(instance, EMPTY_INT_ARRAY, RandomSelector, null, Random)
                val sent = state.randomUnsatisfied(Random)
                assertFalse(sent.satisfies(state.instance))
            }
        }
    }

    @Test
    fun randomUnsatisfiedAssumption() {
        val p = Problem(arrayOf(), 5)
        val f = factory(p)
        for (z in 1..REPEAT) {
            val instance = BitFieldInstance(5)
            val state = f.build(instance, intArrayOf(1, 3, 5, 7, 9), RandomSelector, null, Random)
            assertEquals(0, state.totalUnsatisfied)
            for (i in 0 until 5) if (!state.instance[i]) state.flip(i)
            assertEquals(5, state.totalUnsatisfied)
            val assumption = state.randomUnsatisfied(Random)
            assertTrue(assumption is Conjunction)
            assertFalse(assumption.satisfies(state.instance))
        }
    }
}

class BasicSearchStateTest : SearchStateTest() {
    override fun factory(p: Problem) = BasicSearchStateFactory(p)
}

class PropSearchStateTest : SearchStateTest() {
    override fun factory(p: Problem) = PropSearchStateFactory(p)
}

