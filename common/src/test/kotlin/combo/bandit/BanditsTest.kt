package combo.bandit

import combo.math.*
import combo.model.TestModels
import combo.model.TestModels.SAT_PROBLEMS
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.test.assertContentEquals
import combo.test.assertEquals
import combo.util.IntCollection
import combo.util.collectionOf
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.*

abstract class BanditTest<B : Bandit<*>> {
    abstract fun bandit(problem: Problem, type: BanditType): B
    open fun infeasibleBandit(problem: Problem, maximize: Boolean, type: BanditType): B? =
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
        for ((i, p) in TestModels.UNSAT_PROBLEMS.withIndex()) {
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
        val p = TestModels.MODEL1.problem
        val bandit1 = bandit(p, BanditType.BINOMIAL)
        bandit1.rewards = FullSample()
        bandit1.maximize = true
        bandit1.randomSeed = 1
        val bandit2 = bandit(p, BanditType.BINOMIAL)
        bandit2.rewards = FullSample()
        bandit2.maximize = false
        bandit2.randomSeed = 2
        val rng = Random(1L)

        for (i in 1..200) {
            val instance1 = bandit1.chooseOrThrow()
            val instance2 = bandit2.chooseOrThrow()
            assertTrue(p.satisfies(instance1))
            assertTrue(p.satisfies(instance2))
            bandit1.update(instance1, BanditType.BINOMIAL.linearRewards(instance1, rng), (rng.nextInt(5) + 1).toFloat())
            bandit2.update(instance2, BanditType.BINOMIAL.linearRewards(instance2, rng), (rng.nextInt(5) + 1).toFloat())
        }
        val sum1 = bandit1.rewards.toArray().sum()
        val sum2 = bandit2.rewards.toArray().sum()
        assertTrue(sum1 > sum2)
    }

    @Test
    fun assumptionsSatisfied() {
        val p = TestModels.MODEL4.problem
        val bandit = bandit(p, BanditType.POISSON)
        bandit.rewards = GrowingDataSample(4)
        for (i in 1..100) {
            val instance = if (Random.nextBoolean()) bandit.chooseOrThrow(collectionOf(3, 12)).also {
                assertTrue { Conjunction(collectionOf(3, 12)).satisfies(it) }
            }
            else bandit.chooseOrThrow()
            assertTrue(p.satisfies(instance))
            bandit.update(instance, BanditType.POISSON.linearRewards(instance, Random))
        }
    }

    @Test
    fun assumptionsToInfeasible() {
        fun testInfeasible(assumptions: IntCollection, p: Problem) {
            assertFailsWith(ValidationException::class) {
                val bandit = bandit(p, BanditType.NORMAL)
                bandit.chooseOrThrow(assumptions)
                bandit.chooseOrThrow(assumptions)
            }
        }
        testInfeasible(collectionOf(11, 12), SAT_PROBLEMS[0])
        testInfeasible(collectionOf(6, -7, -8), SAT_PROBLEMS[0])
        testInfeasible(collectionOf(-2, 4), SAT_PROBLEMS[0])
        testInfeasible(collectionOf(1, 6), SAT_PROBLEMS[2])
        testInfeasible(collectionOf(3, 4, 5), SAT_PROBLEMS[3])
        testInfeasible(collectionOf(-10, -11, -12), SAT_PROBLEMS[3])
        testInfeasible(collectionOf(-4, 5), SAT_PROBLEMS[4])
    }

    @Test
    fun randomSeedDeterministic() {
        val bandit1 = bandit(SAT_PROBLEMS[2], BanditType.NORMAL)
        val bandit2 = bandit(SAT_PROBLEMS[2], BanditType.NORMAL)
        bandit1.randomSeed = 0
        bandit2.randomSeed = 0
        val rng1 = Random(1L)
        val rng2 = Random(1L)
        val instances1 = generateSequence {
            bandit1.chooseOrThrow().also {
                bandit1.update(it, BanditType.NORMAL.linearRewards(it, rng1))
            }
        }.take(10).toList()
        val instances2 = generateSequence {
            bandit2.chooseOrThrow().also {
                bandit2.update(it, BanditType.NORMAL.linearRewards(it, rng2))
            }
        }.take(10).toList()
        for (i in 0 until 10) {
            assertEquals(instances1[i], instances2[i])
        }
        assertContentEquals(instances1, instances2)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun exportIdempotent() {
        for (p in SAT_PROBLEMS) {
            val bandit = bandit(p, BanditType.BINOMIAL)
            for (i in 0 until 100) {
                val instance = bandit.chooseOrThrow()
                bandit.update(instance, BanditType.BINOMIAL.linearRewards(instance, Random))
            }
            val list1 = (bandit as Bandit<Any>).exportData()
            val list2 = (bandit as Bandit<Any>).exportData()
            if (list1 is Array<*>)
                assertContentEquals(list1, list2 as Array<*>)
            else throw IllegalArgumentException("Update test with other types")
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun exportImport() {
        for (p in SAT_PROBLEMS) {
            val bandit = bandit(p, BanditType.BINOMIAL)
            for (i in 0 until 100) {
                val instance = bandit.chooseOrThrow()
                bandit.update(instance, BanditType.BINOMIAL.linearRewards(instance, Random))
            }
            val list1 = (bandit as Bandit<Any>).exportData()
            val bandit2 = bandit(p, BanditType.BINOMIAL)
            (bandit2 as Bandit<Any>).importData(list1)

            assertNotNull(bandit2.choose())
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun exportImportReplace() {
        for (p in SAT_PROBLEMS) {
            val bandit1 = bandit(p, BanditType.NORMAL)
            val bandit2 = bandit(p, BanditType.NORMAL)
            for (i in 0 until 100) {
                val instance1 = bandit1.chooseOrThrow()
                bandit1.update(instance1, BanditType.NORMAL.linearRewards(instance1, Random))
                val instance2 = bandit1.chooseOrThrow()
                bandit2.update(instance2, BanditType.NORMAL.linearRewards(instance2, Random))
            }
            val list1 = (bandit1 as Bandit<Any>).exportData()
            (bandit2 as Bandit<Any>).importData(list1)
            assertNotNull(bandit2.choose())
        }
    }

    @Test
    fun relativeWeightImportance() {
        val p = Problem(emptyArray(), 1)
        val inst0 = BitArray(1, intArrayOf(0))
        val inst1 = BitArray(1, intArrayOf(1))
        // This has a small chance of failure so we fix seed
        val rng = Random(0)
        for (type in BanditType.values()) {
            val bandit = bandit(p, type)
            for (i in 0 until 100) {
                bandit.update(inst0, type.linearRewards(inst0, rng))
                bandit.update(inst1, type.linearRewards(inst0, rng), 0.5f)
                bandit.update(inst1, type.linearRewards(inst1, rng), 1.0f)
            }
            var count = 0
            for (i in 0 until 100)
                if (bandit.chooseOrThrow()[0]) count++
            assertTrue(count > 50, "$type")
        }
    }
}

abstract class PredictionBanditTest<B : PredictionBandit<*>> : BanditTest<B>() {

    abstract override fun bandit(problem: Problem, type: BanditType): B

    @Test
    fun updatePrediction() {
        val p = Problem(emptyArray(), 2)
        val instances = (0 until 4).asSequence().map { BitArray(2, intArrayOf(it)) }.toList()
        val rng = Random(0)
        for (type in BanditType.values()) {
            val bandit = bandit(p, type)
            val means = (0 until 4).asSequence().map { RunningMean() }.toList()
            for (t in 0 until 400) {
                for ((i, inst) in instances.withIndex()) {
                    val result = type.linearRewards(inst, rng)
                    bandit.update(inst, result)
                    means[i].accept(result)
                }
            }
            for (i in 0 until 4)
                assertEquals(means[i].mean, bandit.predict(instances[i]), 0.2f, "$type")
        }
    }
}

enum class BanditType {

    BINOMIAL {
        override fun linearRewards(mean: Float, trials: Int, rng: Random) = rng.nextBinomial(mean, trials).toFloat() / trials
    },
    NORMAL {
        override fun linearRewards(mean: Float, trials: Int, rng: Random) = rng.nextNormal(2.0f + 3 * mean, sqrt(2.0f) / trials)
    },
    POISSON {
        override fun linearRewards(mean: Float, trials: Int, rng: Random) = generateSequence { rng.nextPoisson(1 + 3 * mean).toFloat() }.take(trials).average().toFloat()
    };

    fun linearRewards(instance: Instance, rng: Random): Float {
        val weights = FloatArray(instance.size) { 1 + it * 0.1f }
        return linearRewards((1 + (instance dot weights)) / (weights.sum() + 2), 1, rng)
    }

    abstract fun linearRewards(mean: Float, trials: Int, rng: Random): Float

    companion object {
        fun random() = values()[Random.nextInt(values().size)]
    }
}

