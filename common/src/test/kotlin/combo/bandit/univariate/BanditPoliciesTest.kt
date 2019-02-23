package combo.bandit.univariate

import combo.math.VarianceEstimator
import combo.test.assertEquals
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

abstract class BanditPolicyTest<E : VarianceEstimator> {
    abstract fun banditPolicy(): BanditPolicy<E>

    @Test
    fun emptyEvaluate() {
        val bp = banditPolicy()
        bp.evaluate(bp.baseData(), Random)
    }

    @Test
    fun oneArmRound() {
        val bp = banditPolicy()
        val arm1 = bp.baseData()
        bp.addArm(arm1)
        bp.beginRound(Random)
        val m1 = arm1.mean
        bp.completeRound(arm1, 10.0, 1.0)
        assertTrue(arm1.mean > m1)
        assertEquals(bp.baseData().nbrWeightedSamples + 1.0, arm1.nbrWeightedSamples, 1E-6)
    }

    @Test
    fun twoArmCompetition() {
        val bp = banditPolicy()
        val arm1 = bp.baseData()
        val arm2 = bp.baseData()
        bp.addArm(arm1)
        bp.addArm(arm2)
        val rng = Random(0)
        for (i in 1..50) {
            bp.beginRound(rng)
            val s1 = bp.evaluate(arm1, rng)
            val s2 = bp.evaluate(arm2, rng)
            if (s1 > s2) bp.completeRound(arm1, 10.0, 10.0)
            else bp.completeRound(arm2, 0.0, 10.0)
        }
        assertTrue(arm1.mean > arm2.mean)
        assertTrue(bp.evaluate(arm1, rng) > bp.evaluate(arm2, rng))
        assertEquals(bp.baseData().nbrWeightedSamples * 2 + 500.0,
                arm1.nbrWeightedSamples + arm2.nbrWeightedSamples, 1E-6)
    }
}

class ThompsonSamplingTest : BanditPolicyTest<VarianceEstimator>() {
    override fun banditPolicy() = ThompsonSampling(NormalPosterior)
}

class UCB1Test : BanditPolicyTest<VarianceEstimator>() {
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

