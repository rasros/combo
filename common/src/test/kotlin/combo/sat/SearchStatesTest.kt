package combo.sat

import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.SolverTest
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random
import kotlin.test.*

abstract class TrackingInstanceTest {

    abstract fun factory(p: Problem): TrackingInstanceFactory

    private companion object {
        // Since these tests are random the numbers here can be tweaked during debugging
        const val REPEAT: Int = 3
    }

    private fun checkUnsatisfied(p: Problem, t: TrackingInstance) {
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
            val tracker1 = f.build(ByteArrayInstanceFactory.create(p.nbrVariables), EMPTY_INT_ARRAY, RandomInitializer(), null, Random(0))
            val tracker2 = f.build(ByteArrayInstanceFactory.create(p.nbrVariables), EMPTY_INT_ARRAY, RandomInitializer(), null, Random(0))
            assertEquals(tracker1.instance, tracker2.instance)
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
                val tracker = f.buildPreDefined(instance, EMPTY_INT_ARRAY)
                assertEquals(0, tracker.totalUnsatisfied)
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
                val tracker = f.buildPreDefined(instance, assumptions)
                checkUnsatisfied(p, tracker)
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
                val tracker = f.buildPreDefined(instance, assumptions)
                checkUnsatisfied(p, tracker)
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
                val tracker = f.build(instance, EMPTY_INT_ARRAY, RandomInitializer(), null, Random)
                checkUnsatisfied(p, tracker)
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
                val tracker = f.build(instance, assumptions, RandomInitializer(), null, Random)
                checkUnsatisfied(p, tracker)
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
                val tracker = f.build(instance, EMPTY_INT_ARRAY, RandomInitializer(), null, Random(0))
                checkUnsatisfied(p, tracker)
                val ix = Random(0).nextInt(p.nbrVariables)
                val lit = tracker.instance.literal(ix)
                tracker.flip(ix)
                checkUnsatisfied(p, tracker)
                assertNotEquals(lit, tracker.instance.literal(ix))
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
                val tracker = f.build(instance, EMPTY_INT_ARRAY, RandomInitializer(), null, Random)
                checkUnsatisfied(p, tracker)
                for (lit in 1..10) {
                    tracker.flip(Random.nextInt(p.nbrVariables))
                    checkUnsatisfied(p, tracker)
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
                val tracker = f.build(instance, EMPTY_INT_ARRAY, RandomInitializer(), null, Random)
                val ix = Random.nextInt(p.nbrVariables)
                val copy = instance.copy()
                tracker.improvement(ix)
                assertEquals(copy, instance)
                checkUnsatisfied(p, tracker)
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
                val tracker = f.build(instance, EMPTY_INT_ARRAY, RandomInitializer(), null, Random)
                val ix = Random.nextInt(p.nbrVariables)
                val imp = tracker.improvement(ix)
                val preFlips = p.flipsToSatisfy(instance)
                tracker.flip(ix)
                val postFlips = p.flipsToSatisfy(instance)
                assertEquals(postFlips, preFlips - imp, "$postFlips = $preFlips - $imp")
                checkUnsatisfied(p, tracker)
            }
        }
    }

    @Test
    fun improvementAssumptions() {
        val p = Problem(arrayOf(), 10)
        val f = factory(p)
        for (z in 1..REPEAT) {
            val instance = BitFieldInstanceFactory.create(p.nbrVariables)
            val tracker = f.build(instance, intArrayOf(0, 2, 4, 6), RandomInitializer(), null, Random)
            if (tracker.instance[0]) tracker.flip(0)
            val imp = tracker.improvement(0)
            assertTrue(imp > 0)
            checkUnsatisfied(p, tracker)
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
                val tracker = f.build(instance, EMPTY_INT_ARRAY, RandomInitializer(), null, Random)
                val sent = tracker.randomUnsatisfied(Random)
                assertFalse(sent.satisfies(tracker.instance))
            }
        }
    }

    @Test
    fun randomUnsatisfiedAssumption() {
        val p = Problem(arrayOf(), 5)
        val f = factory(p)
        for (z in 1..REPEAT) {
            val instance = BitFieldInstance(5)
            val tracker = f.build(instance, intArrayOf(1, 3, 5, 7, 9), RandomInitializer(), null, Random)
            assertEquals(0, tracker.totalUnsatisfied)
            for (i in 0 until 5) if (!tracker.instance[i]) tracker.flip(i)
            assertEquals(5, tracker.totalUnsatisfied)
            val assumption = tracker.randomUnsatisfied(Random)
            assertTrue(assumption is Conjunction)
            assertFalse(assumption.satisfies(tracker.instance))
        }
    }
}

class BasicTrackingInstanceTest : TrackingInstanceTest() {
    override fun factory(p: Problem) = BasicTrackingInstanceFactory(p)
}

class PropTrackingInstanceTest : TrackingInstanceTest() {
    override fun factory(p: Problem) = PropTrackingInstanceFactory(p)
}

