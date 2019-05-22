package combo.bandit

import combo.bandit.univariate.*
import combo.math.CountData
import combo.math.VarianceEstimator
import combo.math.nextNormal
import combo.model.TestModels
import combo.sat.BitArray
import combo.sat.Problem
import combo.sat.constraints.Conjunction
import combo.util.collectionOf
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.*

class DecisionTreeBanditTest : BanditTest<Array<LiteralData<VarianceEstimator>>>() {

    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): DecisionTreeBandit<VarianceEstimator> {
        val policy: BanditPolicy<VarianceEstimator> = when (type) {
            BanditType.BINOMIAL -> ThompsonSampling(BinomialPosterior) as BanditPolicy<VarianceEstimator>
            BanditType.NORMAL -> ThompsonSampling(NormalPosterior)
            BanditType.POISSON -> ThompsonSampling(PoissonPosterior)
        }
        return DecisionTreeBandit(problem, policy)
    }

    override fun infeasibleBandit(problem: Problem, maximize: Boolean, type: BanditType) = bandit(problem, type).apply {
        solver.timeout = 1L
    }

    @Test
    fun exportEmpty() {
        val bandit = DecisionTreeBandit(Problem(arrayOf(), 1), UCB1Tuned())
        assertTrue(bandit.exportData().isEmpty())
    }

    @Test
    fun loadJunkData() {
        val n1 = LiteralData(intArrayOf(1, 4), CountData(5.0f, 1.0f))
        val n2 = LiteralData(intArrayOf(-1, 4), CountData(4.0f, 1.0f))
        val n3 = LiteralData(intArrayOf(1, 4, 3), CountData(3.0f, 1.0f))
        val n4 = LiteralData(intArrayOf(3, 4), CountData(2.0f, 1.0f))
        val n5 = LiteralData(intArrayOf(2), CountData(1.0f, 1.0f))
        val bandit = DecisionTreeBandit(TestModels.MODEL1.problem, ThompsonSampling(BinomialPosterior, CountData(0.0f, 1.0f)))
        bandit.importData(arrayOf(n5, n4, n3, n2, n1))
        val export = bandit.exportData()
        // Only the first two nodes are useful
        assertEquals(9.0, export.sumByDouble { it.data.mean.toDouble() })
    }

    @Test
    fun loadOutOfBoundsLiterals() {
        val n1 = LiteralData(intArrayOf(0, 101), CountData(3.0f, 1.0f))
        val n2 = LiteralData(intArrayOf(0, 100, 2), CountData(2.0f, 1.0f))
        val n3 = LiteralData(intArrayOf(0, 100, 3), CountData(1.0f, 1.0f))
        val n4 = LiteralData(intArrayOf(1), CountData(0.0f, 1.0f))
        assertFailsWith(IllegalArgumentException::class) {
            DecisionTreeBandit(TestModels.MODEL1.problem, ThompsonSampling(BinomialPosterior)).apply {
                importData(arrayOf(n3, n4, n2, n1))
            }
        }
    }

    @Test
    fun storeLoadStoreReducedData() {
        val p = TestModels.MODEL1.problem
        val bandit = bandit(p, BanditType.POISSON)
        for (i in 1..100) {
            val instance = bandit.chooseOrThrow()
            bandit.update(instance, BanditType.POISSON.linearRewards(instance, Random), abs(Random.nextNormal(1.0f)))
        }
        val data = bandit.exportData()
        val reduced = data.sliceArray(0 until data.size / 2)
        val bandit2 = DecisionTreeBandit(p, ThompsonSampling(PoissonPosterior))
        bandit2.importData(reduced)
        val data2 = bandit2.exportData()
        assertTrue(data2.size >= reduced.size)
        for (node in reduced) {
            assertNotNull(data2.find { it.setLiterals.contentEquals(node.setLiterals) }?.also {
                assertEquals(node.data.mean, it.data.mean)
                assertEquals(node.data.variance, it.data.variance)
                assertEquals(node.data.nbrWeightedSamples, it.data.nbrWeightedSamples)
            })
        }
    }

    @Test
    fun disjunctExportData() {
        val p = TestModels.MODEL4.problem
        val bandit = bandit(p, BanditType.NORMAL).apply {
            tau = 0.0f
            delta = 0.5f
            maxLiveNodes = 5
        }
        for (i in 1..100) {
            val instance = bandit.chooseOrThrow()
            bandit.update(instance, BanditType.NORMAL.linearRewards(instance, Random))
        }
        val data = bandit.exportData()
        for (l in 0 until p.nbrVariables.toFloat().pow(2.0f).toInt()) {
            val instance = BitArray(p.nbrVariables, intArrayOf(l))
            val nbrMatching = data.sumBy { if (Conjunction(collectionOf(*it.setLiterals)).satisfies(instance)) 1 else 0 }
            assertEquals(1, nbrMatching)
        }
    }
}
