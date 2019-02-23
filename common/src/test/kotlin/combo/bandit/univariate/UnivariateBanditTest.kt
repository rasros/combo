package combo.bandit.univariate

import combo.bandit.BanditType
import combo.math.FullSample
import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnivariateBanditTest {

    @Test
    fun zeroArms() {
        assertFailsWith(IllegalArgumentException::class) {
            UnivariateBandit(0, UCB1())
        }
    }

    @Test
    fun minimizeVsMaximize() {
        val bandit1 = UnivariateBandit(10, UCB1Tuned())
        bandit1.rewards = FullSample()
        bandit1.maximize = true
        bandit1.randomSeed = 1L
        val bandit2 = UnivariateBandit(10, UCB1Tuned())
        bandit2.rewards = FullSample()
        bandit2.maximize = false
        bandit2.randomSeed = 2L
        val rng = Random(1L)
        for (i in 1..100) {
            val i1 = bandit1.choose()
            val i2 = bandit2.choose()
            bandit1.update(i1, BanditType.BINOMIAL.linearRewards(i1.toDouble() / 10, rng), (rng.nextInt(5) + 1).toDouble())
            bandit2.update(i2, BanditType.BINOMIAL.linearRewards(i2.toDouble() / 10, rng), (rng.nextInt(5) + 1).toDouble())
        }
        val sum1 = bandit1.rewards.toArray().sum()
        val sum2 = bandit2.rewards.toArray().sum()
        assertTrue(sum1 > sum2)
    }

    @Test
    fun randomSeedDeterministic() {
        val bandit1 = UnivariateBandit(10, ThompsonSampling(HierarchicalNormalPosterior))
        val bandit2 = UnivariateBandit(10, ThompsonSampling(HierarchicalNormalPosterior))
        bandit1.randomSeed = 0L
        bandit2.randomSeed = 0L
        val rng1 = Random(1L)
        val rng2 = Random(1L)
        val arms1 = generateSequence {
            bandit1.choose().also {
                bandit1.update(it, BanditType.NORMAL.linearRewards(it.toDouble(), rng1))
            }
        }.take(10).toList()
        val arm2 = generateSequence {
            bandit2.choose().also {
                bandit2.update(it, BanditType.NORMAL.linearRewards(it.toDouble(), rng2))
            }
        }.take(10).toList()
        for (i in 0 until 10) {
            assertEquals(arms1[i], arm2[i])
        }
        assertContentEquals(arms1, arm2)
    }

    @Test
    fun storeLoadStore() {
        val bandit = UnivariateBandit(20, EpsilonDecreasing())
        for (i in 0 until 100) {
            val j = bandit.choose()
            bandit.update(j, BanditType.BINOMIAL.linearRewards(j.toDouble() / 20, Random))
        }
        val list1 = bandit.exportData()
        val bandit2 = UnivariateBandit(20, EpsilonDecreasing())
        bandit2.importData(list1)

        bandit.randomSeed = 1L
        bandit2.randomSeed = 1L

        assertEquals(bandit.choose(), bandit2.choose())
        assertEquals(list1.size, bandit2.exportData().size)
    }
}