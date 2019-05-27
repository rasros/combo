package combo.bandit

import combo.bandit.univariate.UCB1
import combo.bandit.univariate.UCB1Normal
import combo.bandit.univariate.UCB1Tuned
import combo.math.BinarySum
import combo.math.RunningSquaredEstimator
import combo.model.TestModels.MODEL1
import combo.sat.Problem
import combo.sat.solvers.ExhaustiveSolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombinatorialBanditTest : BanditTest<CombinatorialBandit<*>>() {

    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): CombinatorialBandit<*> {
        val instances = ExhaustiveSolver(problem).apply { randomSeed = 0 }
                .asSequence().take(100).toList().toTypedArray()
        return when (type) {
            BanditType.BINOMIAL -> CombinatorialBandit(instances, UCB1())
            BanditType.NORMAL -> CombinatorialBandit(instances, UCB1Normal())
            BanditType.POISSON -> CombinatorialBandit(instances, UCB1Tuned())
        }
    }

    @Test
    fun importRestructure() {
        val problem = MODEL1.problem
        val solutions1 = ExhaustiveSolver(problem).asSequence().take(10).toList().toTypedArray()
        val solutions2 = ExhaustiveSolver(problem).asSequence().take(150).toList().toTypedArray()

        val bandit = CombinatorialBandit(solutions1, UCB1Normal())
        bandit.importData(solutions2.map { InstanceData(it, RunningSquaredEstimator()) }.toTypedArray(), true)
        val postData = bandit.exportData()

        // Verify that data has been replaced by solutions2
        assertEquals(150, postData.size)
        for (d in postData)
            assertTrue(d.instance in solutions2)
    }

    @Test
    fun importNoRestructure() {
        val problem = MODEL1.problem
        val solutions1 = ExhaustiveSolver(problem).asSequence().take(20).toList().toTypedArray()
        val solutions2 = ExhaustiveSolver(problem).asSequence().take(50).toList().toTypedArray()

        // Make sure that there are some overlap
        if (!solutions1.contains(solutions2[0]))
            solutions1[0] = solutions2[0]

        val bandit = CombinatorialBandit(solutions1, UCB1())
        bandit.importData(solutions2.map { InstanceData(it, BinarySum(0.5f, 10.0f)) }.toTypedArray(), false)
        val postData = bandit.exportData()

        // Verify that data has not been replaced by solutions2
        assertEquals(20, postData.size)
        for (d in postData) {
            assertTrue(d.instance in solutions1)
            if (d.instance in solutions2) {
                assertTrue(d.data.nbrWeightedSamples >= 10.0f)
            }
        }
    }
}
