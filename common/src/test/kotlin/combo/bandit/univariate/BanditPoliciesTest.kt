package combo.bandit.univariate

import combo.math.CountData
import combo.math.RunningVariance
import combo.math.VarianceEstimator
import combo.test.assertEquals
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class BanditPolicyTest<E : VarianceEstimator> {
    abstract fun banditPolicy(): BanditPolicy<E>

    @Test
    fun initialEvaluate() {
        val bp = banditPolicy()
        val data = bp.baseData()
        bp.addArm(data)
        bp.evaluate(data, true, Random)
    }

    @Test
    fun oneArmRound() {
        val bp = banditPolicy()
        val arm1 = bp.baseData()
        bp.addArm(arm1)
        bp.round(Random)
        bp.update(arm1, 2.0f, 10.0f)
        assertFalse(arm1.mean.isNaN())
        assertEquals(bp.baseData().nbrWeightedSamples + 10.0f, arm1.nbrWeightedSamples, 1E-6f)
    }

    @Test
    fun twoArmCompetitionMaximize() {
        val bp = banditPolicy()
        val arm1 = bp.baseData()
        val arm2 = bp.baseData()
        bp.addArm(arm1)
        bp.addArm(arm2)
        val rng = Random(0)
        for (i in 1..50) {
            bp.round(rng)
            val s1 = bp.evaluate(arm1, true, rng)
            val s2 = bp.evaluate(arm2, true, rng)
            if (s1 > s2) bp.update(arm1, 10.0f, 10.0f)
            else bp.update(arm2, 0.0f, 10.0f)
        }
        assertTrue(arm1.mean > arm2.mean)
        assertTrue(bp.evaluate(arm1, true, rng) > bp.evaluate(arm2, true, rng))
        assertEquals(bp.baseData().nbrWeightedSamples * 2 + 500.0f,
                arm1.nbrWeightedSamples + arm2.nbrWeightedSamples, 1E-6f)
    }

    @Test
    fun twoArmCompetitionMinimize() {
        val bp = banditPolicy()
        val arm1 = bp.baseData()
        val arm2 = bp.baseData()
        bp.addArm(arm1)
        bp.addArm(arm2)
        val rng = Random(0)
        for (i in 1..50) {
            bp.round(rng)
            val s1 = bp.evaluate(arm1, false, rng)
            val s2 = bp.evaluate(arm2, false, rng)
            if (s1 > s2) bp.update(arm1, 10.0f, 10.0f)
            else bp.update(arm2, 0.0f, 10.0f)
        }
        assertTrue(arm1.mean > arm2.mean)
        assertTrue(bp.evaluate(arm1, false, rng) < bp.evaluate(arm2, false, rng))
        assertEquals(bp.baseData().nbrWeightedSamples * 2 + 500.0f,
                arm1.nbrWeightedSamples + arm2.nbrWeightedSamples, 1E-6f)
    }
}

class ThompsonSamplingTest : BanditPolicyTest<VarianceEstimator>() {
    override fun banditPolicy() = ThompsonSampling(NormalPosterior)
}

class PooledThompsonSamplingTest : BanditPolicyTest<VarianceEstimator>() {
    override fun banditPolicy() = PooledThompsonSampling(
            HierarchicalNormalPosterior(PooledVarianceEstimator(RunningVariance(0.0f, 0.02f, 0.02f))))
}

class UCB1Test : BanditPolicyTest<CountData>() {
    override fun banditPolicy() = UCB1()
}

class UCB1NormalTest : BanditPolicyTest<SquaredEstimator>() {
    override fun banditPolicy() = UCB1Normal()
}

class UCB1TunedTest : BanditPolicyTest<SquaredEstimator>() {
    override fun banditPolicy() = UCB1Tuned()
}

class EpsilonGreedyTest : BanditPolicyTest<VarianceEstimator>() {
    override fun banditPolicy() = EpsilonGreedy()
}

class EpsilonDecreasingTest : BanditPolicyTest<VarianceEstimator>() {
    override fun banditPolicy() = EpsilonDecreasing()
}

