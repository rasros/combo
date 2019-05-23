package combo.bandit

import combo.bandit.univariate.*
import combo.math.SumEstimator
import combo.model.TestModels
import combo.sat.BitArray
import combo.sat.Problem
import combo.sat.constraints.Conjunction
import combo.util.collectionOf
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.*

class DecisionTreeBanditTest : PredictionBanditTest<DecisionTreeBandit<*>>() {

    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): DecisionTreeBandit<*> {
        val policy = when (type) {
            BanditType.BINOMIAL -> ThompsonSampling(BinomialPosterior)
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
        val n1 = LiteralData(intArrayOf(1, 4), SumEstimator(5.0f, 1.0f))
        val n2 = LiteralData(intArrayOf(-1, 4), SumEstimator(4.0f, 1.0f))
        val n3 = LiteralData(intArrayOf(1, 4, 3), SumEstimator(3.0f, 1.0f))
        val n4 = LiteralData(intArrayOf(3, 4), SumEstimator(2.0f, 1.0f))
        val n5 = LiteralData(intArrayOf(2), SumEstimator(1.0f, 1.0f))
        val bandit = DecisionTreeBandit(TestModels.MODEL1.problem, ThompsonSampling(BinomialPosterior, SumEstimator(0.0f, 1.0f)))
        bandit.importData(arrayOf(n5, n4, n3, n2, n1))
        val export = bandit.exportData()
        // Only the first two nodes are useful
        assertEquals(9.0, export.sumByDouble { it.data.mean.toDouble() })
    }

    @Test
    fun loadOutOfBoundsLiterals() {
        val n1 = LiteralData(intArrayOf(0, 101), SumEstimator(3.0f, 1.0f))
        val n2 = LiteralData(intArrayOf(0, 100, 2), SumEstimator(2.0f, 1.0f))
        val n3 = LiteralData(intArrayOf(0, 100, 3), SumEstimator(1.0f, 1.0f))
        val n4 = LiteralData(intArrayOf(1), SumEstimator(0.0f, 1.0f))
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
            bandit.update(instance, BanditType.POISSON.linearRewards(instance, Random), 1 + Random.nextInt(10).toFloat())
        }
        val data = bandit.exportData()
        val reduced = data.sliceArray(0 until data.size / 2)
        val bandit2 = DecisionTreeBandit(p, ThompsonSampling(PoissonPosterior))
        @Suppress("UNCHECKED_CAST")
        (bandit2 as Bandit<Any>).importData(reduced)
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
