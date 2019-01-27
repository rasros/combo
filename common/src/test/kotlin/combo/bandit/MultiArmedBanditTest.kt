package combo.bandit

import combo.math.BinomialPosterior
import combo.math.GaussianPosterior
import combo.math.PoissonPosterior
import combo.sat.BitFieldLabelingFactory
import combo.sat.Problem
import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.SolverTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class MultiArmedBanditTest : BanditTest() {
    override fun bandit(problem: Problem, type: BanditType) = MultiArmedBandit(
            ExhaustiveSolver(problem).apply {
                randomSeed = 0L
                labelingFactory = BitFieldLabelingFactory
            }.sequence().toList().toTypedArray().let { if (it.size > 100) it.sliceArray(0 until 100) else it },
            posterior = when (type) {
                BanditType.BINOMIAL -> BinomialPosterior
                BanditType.NORMAL -> GaussianPosterior
                BanditType.POISSON -> PoissonPosterior
            })

    @Test
    fun storeLoadStore() {
        for (p in SolverTest.SMALL_PROBLEMS) {
            val bandit = bandit(p, BanditType.BINOMIAL)
            for (i in 0 until 100) {
                val l = bandit.chooseOrThrow()
                bandit.update(l, BanditType.BINOMIAL.linearRewards(l, Random))
            }
            val list1 = bandit.exportData()
            val bandit2 = MultiArmedBandit(list1.map { it.labeling }.toTypedArray(), posterior = BinomialPosterior, historicData = list1)
            val list2 = bandit2.exportData()
            assertEquals(list1.size, list2.size)
            for (i in 0 until list1.size) {
                assertEquals(list1[i].labeling, list2[i].labeling)
                assertEquals(list1[i].total.mean, list2[i].total.mean)
                assertEquals(list1[i].total.nbrWeightedSamples, list2[i].total.nbrWeightedSamples)
            }
        }
    }
}