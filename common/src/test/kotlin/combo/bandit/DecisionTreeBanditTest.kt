package combo.bandit

import combo.bandit.univariate.*
import combo.math.CountData
import combo.math.VarianceEstimator
import combo.math.nextNormal
import combo.model.ModelTest
import combo.sat.BitFieldLabeling
import combo.sat.Conjunction
import combo.sat.Problem
import combo.util.collectionOf
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.*

class DecisionTreeBanditTest : BanditTest<Array<LiteralData<VarianceEstimator>>>() {

    override fun bandit(problem: Problem, type: BanditType) = DecisionTreeBandit(problem, UCB1())

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
        val n1 = LiteralData(intArrayOf(0, 6), CountData(5.0, 1.0))
        val n2 = LiteralData(intArrayOf(1, 6), CountData(4.0, 1.0))
        val n3 = LiteralData(intArrayOf(0, 6, 4), CountData(3.0, 1.0))
        val n4 = LiteralData(intArrayOf(4, 6), CountData(2.0, 1.0))
        val n5 = LiteralData(intArrayOf(2), CountData(1.0, 1.0))
        val bandit = DecisionTreeBandit(ModelTest.SMALL1.problem, ThompsonSampling(BinomialPosterior, CountData(0.0, 1.0)))
        bandit.importData(arrayOf(n5, n4, n3, n2, n1))
        val export = bandit.exportData()
        // Only the first two nodes are useful
        assertEquals(9.0, export.sumByDouble { it.data.mean })
    }

    @Test
    fun loadOutOfBoundsLiterals() {
        val n1 = LiteralData(intArrayOf(0, 101), CountData(3.0, 1.0))
        val n2 = LiteralData(intArrayOf(0, 100, 2), CountData(2.0, 1.0))
        val n3 = LiteralData(intArrayOf(0, 100, 3), CountData(1.0, 1.0))
        val n4 = LiteralData(intArrayOf(1), CountData(0.0, 1.0))
        assertFailsWith(IllegalArgumentException::class) {
            DecisionTreeBandit(ModelTest.SMALL1.problem, ThompsonSampling(BinomialPosterior)).apply {
                importData(arrayOf(n3, n4, n2, n1))
            }
        }
    }

    @Test
    fun storeLoadStoreReducedData() {
        val p = ModelTest.SMALL1.problem
        val bandit = bandit(p, BanditType.POISSON)
        for (i in 1..100) {
            val l = bandit.chooseOrThrow()
            bandit.update(l, BanditType.POISSON.linearRewards(l, Random), abs(Random.nextNormal(1.0)))
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
        val p = ModelTest.SMALL4.problem
        val bandit = bandit(p, BanditType.NORMAL).apply {
            tau = 0.0
            delta = 0.5
            maxLiveNodes = 5
        }
        for (i in 1..100) {
            val l = bandit.chooseOrThrow()
            bandit.update(l, BanditType.NORMAL.linearRewards(l, Random))
        }
        val data = bandit.exportData()
        for (l in 0 until p.nbrVariables.toDouble().pow(2.0).toLong()) {
            val labeling = BitFieldLabeling(p.nbrVariables, longArrayOf(l))
            val nbrMatching = data.sumBy { if (Conjunction(collectionOf(it.setLiterals)).satisfies(labeling)) 1 else 0 }
            assertEquals(1, nbrMatching)
        }
    }
}