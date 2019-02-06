package combo.bandit

import combo.math.FullSample
import combo.math.GrowingDataSample
import combo.math.nextNormal
import combo.math.nextPoisson
import combo.model.ModelTest
import combo.sat.*
import combo.sat.solvers.SolverTest
import combo.sat.solvers.SolverTest.Companion.SMALL_PROBLEMS
import combo.test.assertContentEquals
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

abstract class BanditTest<D> {
    abstract fun bandit(problem: Problem, type: BanditType): Bandit<D>
    open fun infeasibleBandit(problem: Problem, maximize: Boolean, type: BanditType): Bandit<D>? =
            bandit(problem, type)

    @Test
    fun emptyProblem() {
        val p = Problem(arrayOf(), 0)
        val bandit = bandit(p, BanditType.random())
        val l = bandit.chooseOrThrow()
        assertEquals(0, l.size)
    }

    @Test
    fun smallInfeasibleProblem() {
        for ((i, p) in SolverTest.SMALL_UNSAT_PROBLEMS.withIndex()) {
            try {
                val infeasibleBandit = infeasibleBandit(p, Random.nextBoolean(), BanditType.random())
                if (infeasibleBandit != null) {
                    assertFailsWith(ValidationException::class, "Model $i") {
                        infeasibleBandit.chooseOrThrow()
                    }
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun minimizeVsMaximize() {
        val p = ModelTest.SMALL1.problem
        val bandit1 = bandit(p, BanditType.BINOMIAL)
        bandit1.rewards = FullSample()
        bandit1.maximize = true
        bandit1.randomSeed = 1L
        val bandit2 = bandit(p, BanditType.BINOMIAL)
        bandit2.rewards = FullSample()
        bandit2.maximize = false
        bandit2.randomSeed = 2L
        val rng = Random(1L)
        for (i in 1..100) {
            val l1 = bandit1.chooseOrThrow()
            val l2 = bandit2.chooseOrThrow()
            assertTrue(p.satisfies(l1))
            assertTrue(p.satisfies(l2))
            bandit1.update(l1, BanditType.BINOMIAL.linearRewards(l1, rng), (rng.nextInt(5) + 1).toDouble())
            bandit2.update(l2, BanditType.BINOMIAL.linearRewards(l2, rng), (rng.nextInt(5) + 1).toDouble())
        }
        val sum1 = bandit1.rewards.toArray().sum()
        val sum2 = bandit2.rewards.toArray().sum()
        assertTrue(sum1 > sum2)
    }

    @Test
    fun assumptionsSatisfied() {
        val p = ModelTest.SMALL3.problem
        val bandit = bandit(p, BanditType.POISSON)
        bandit.rewards = GrowingDataSample(4)
        for (i in 1..100) {
            val l = if (Random.nextBoolean()) bandit.chooseOrThrow(intArrayOf(4, 12)).also {
                assertTrue { Conjunction(collectionOf(intArrayOf(4, 12))).satisfies(it) }
            }
            else bandit.chooseOrThrow()
            assertTrue(p.satisfies(l))
            bandit.update(l, BanditType.POISSON.linearRewards(l, Random))
        }
    }

    @Test
    fun assumptionsToInfeasible() {
        fun testInfeasible(assumptions: IntArray, p: Problem) {
            assertFailsWith(ValidationException::class) {
                val bandit = bandit(p, BanditType.NORMAL)
                bandit.chooseOrThrow(assumptions)
            }
        }
        testInfeasible(intArrayOf(20, 22), SMALL_PROBLEMS[0])
        testInfeasible(intArrayOf(10, 13, 15), SMALL_PROBLEMS[0])
        testInfeasible(intArrayOf(4, 9), SMALL_PROBLEMS[0])
        testInfeasible(intArrayOf(1, 6), SMALL_PROBLEMS[2])
        testInfeasible(intArrayOf(6, 8, 10), SMALL_PROBLEMS[2])
        testInfeasible(intArrayOf(12, 15, 17, 19), SMALL_PROBLEMS[2])
        testInfeasible(intArrayOf(7, 8, 10), SMALL_PROBLEMS[3])
    }

    @Test
    fun randomSeedDeterministic() {
        val bandit1 = bandit(SMALL_PROBLEMS[2], BanditType.NORMAL)
        val bandit2 = bandit(SMALL_PROBLEMS[2], BanditType.NORMAL)
        bandit1.randomSeed = 0L
        bandit2.randomSeed = 0L
        val rng1 = Random(1L)
        val rng2 = Random(1L)
        val labelings1 = generateSequence {
            bandit1.chooseOrThrow().also {
                bandit1.update(it, BanditType.NORMAL.linearRewards(it, rng1))
            }
        }.take(10).toList()
        val labelings2 = generateSequence {
            bandit2.chooseOrThrow().also {
                bandit2.update(it, BanditType.NORMAL.linearRewards(it, rng2))
            }
        }.take(10).toList()
        for (i in 0 until 10) {
            assertEquals(labelings1[i], labelings2[i])
        }
        assertContentEquals(labelings1, labelings2)
    }

    @Test
    fun storeLoadStore() {
        //for ((index, p) in SMALL_PROBLEMS.withIndex()) {
        val p = SMALL_PROBLEMS[1]
        val bandit = bandit(p, BanditType.BINOMIAL)
        for (i in 0 until 100) {
            val l = bandit.chooseOrThrow()
            bandit.update(l, BanditType.BINOMIAL.linearRewards(l, Random))
        }
        val list1 = bandit.exportData()
        val bandit2 = bandit(p, BanditType.BINOMIAL)
        bandit2.importData(list1)

        bandit.randomSeed = 1L
        bandit2.randomSeed = 1L

        if (bandit.chooseOrThrow() != bandit2.chooseOrThrow()) {
            throw IllegalArgumentException()
        }
        assertEquals(bandit.chooseOrThrow(), bandit2.chooseOrThrow())
        if (list1 is Array<*>)
            assertEquals(list1.size, (bandit2.exportData() as Array<*>).size)
        //}
    }
}

enum class BanditType {
    BINOMIAL {
        override fun linearRewards(mean: Double, rng: Random) = (mean > rng.nextDouble()).toInt().toDouble()
    },
    NORMAL {
        override fun linearRewards(mean: Double, rng: Random) = rng.nextNormal(mean)
    },
    POISSON {
        override fun linearRewards(mean: Double, rng: Random) = rng.nextPoisson(mean).toDouble()
    };

    fun linearRewards(labeling: Labeling, rng: Random): Double {
        val sum = labeling.truthIterator().asSequence().map { if (it.toBoolean()) 1.0 else 0.0 }.sum()
        return linearRewards(sum / labeling.size, rng)
    }

    abstract fun linearRewards(mean: Double, rng: Random): Double

    companion object {
        fun random() = BanditType.values()[Random.nextInt(BanditType.values().size)]
    }
}

