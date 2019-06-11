package combo.bandit.ga

import combo.bandit.BanditTest
import combo.bandit.BanditType
import combo.bandit.InstanceData
import combo.bandit.univariate.*
import combo.ga.TournamentElimination
import combo.math.*
import combo.model.TestModels.MODEL1
import combo.model.TestModels.MODEL4
import combo.sat.BitArray
import combo.sat.Problem
import combo.sat.cardinality
import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.LocalSearchSolver
import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneticAlgorithmBanditTest : BanditTest<GeneticAlgorithmBandit<*>>() {
    @Suppress("UNCHECKED_CAST")
    override fun bandit(problem: Problem, type: BanditType): GeneticAlgorithmBandit<*> {
        return when (type) {
            BanditType.BINOMIAL -> GeneticAlgorithmBandit(problem, UCB1())
            BanditType.NORMAL -> GeneticAlgorithmBandit(problem, ThompsonSampling(NormalPosterior))
            BanditType.POISSON -> GeneticAlgorithmBandit(problem, UCB1Tuned())
        }
    }

    @Test
    fun forcedInitialization() {
        val bandit = GeneticAlgorithmBandit(MODEL1.problem, EpsilonDecreasing())
        assertFalse(bandit.isInitialized)
        assertFalse(bandit.isInitialized)
        bandit.initialize()
        assertTrue(bandit.isInitialized)
    }

    @Test
    fun deterministicInitialization() {
        val p = MODEL1.problem
        val bandit1 = GeneticAlgorithmBandit(p, UCB1())
        bandit1.randomSeed = 0
        val bandit2 = GeneticAlgorithmBandit(p, UCB1())
        bandit2.randomSeed = 0
        val data1 = bandit1.exportData()
        val data2 = bandit2.exportData()
        assertContentEquals(data1, data2)
    }

    @Test
    fun changeCandidateSize() {
        val p = MODEL4.problem
        val bandit = GeneticAlgorithmBandit(p, UCB1())
        bandit.candidateSize = 10
        bandit.exportData()
        bandit.candidateSize = 100
    }

    @Test
    fun setNoDuplicates() {
        val p = MODEL1.problem
        val banditLS = GeneticAlgorithmBandit(p, UCB1Tuned(), LocalSearchSolver(p))
        banditLS.allowDuplicates = false
        banditLS.candidateSize = 20
        val instancesLS = banditLS.exportData().map { it.instance }.toSet()
        assertEquals(20, instancesLS.size)

        val banditES = GeneticAlgorithmBandit(p, UCB1Tuned(), ExhaustiveSolver(p))
        banditES.allowDuplicates = false
        banditES.candidateSize = 20
        val instancesES = banditES.exportData().map { it.instance }.toSet()
        assertEquals(20, instancesES.size)
    }

    @Test
    fun testMinimizeElimination() {
        val p = Problem(emptyArray(), 10)
        val bandit = GeneticAlgorithmBandit(p, ThompsonSampling(HierarchicalNormalPosterior(PooledVarianceEstimator())))
        bandit.maximize = false
        bandit.candidateSize = 10
        bandit.importData(Array(10) { InstanceData(BitArray(10, intArrayOf(it)), RunningVariance()) }, true)
        bandit.elimination = TournamentElimination(10)
        bandit.eliminationPeriod = 10
        bandit.allowDuplicates = false
        bandit.recombinationProbability = 1.0f
        bandit.minEliminationSamples = 0.0f
        for (i in 0 until 10)
            bandit.update(BitArray(10, intArrayOf(i)), i.toFloat())

        // Verify that instance with highest score has been eliminated
        val instances = bandit.exportData().map { it.instance }
        for (i in 0 until 8)
            assertTrue(BitArray(10, intArrayOf(i)) in instances)
        assertFalse(BitArray(10, intArrayOf(9)) in instances)
    }

    @Test
    fun testMaximizeElimination() {
        val p = Problem(emptyArray(), 10)
        val bandit = GeneticAlgorithmBandit(p, ThompsonSampling(HierarchicalNormalPosterior(PooledVarianceEstimator())))
        bandit.maximize = true
        bandit.candidateSize = 10
        bandit.importData(Array(10) { InstanceData(BitArray(10, intArrayOf(it)), RunningVariance()) }, true)
        bandit.elimination = TournamentElimination(10)
        bandit.eliminationPeriod = 10
        bandit.allowDuplicates = false
        bandit.recombinationProbability = 1.0f
        bandit.minEliminationSamples = 0.0f
        for (i in 0 until 10)
            bandit.update(BitArray(10, intArrayOf(i)), i.toFloat())

        // Verify that instance with lowest score has been eliminated
        val instances = bandit.exportData().map { it.instance }
        for (i in 1 until 9)
            assertTrue(BitArray(10, intArrayOf(i)) in instances)
        assertFalse(BitArray(10, intArrayOf(0)) in instances)
    }

    @Test
    fun importRestructureGrowing() {
        val problem = Problem(emptyArray(), 20)
        val bandit = GeneticAlgorithmBandit(problem, UCB1Normal())
        bandit.candidateSize = 20
        bandit.initialize()
        val import = LocalSearchSolver(problem).asSequence().take(20).toList().map {
            InstanceData<SquaredEstimator>(it, RunningSquaredMeans(10.0f))
        }.toTypedArray()
        bandit.importData(import, true)
        val postData = bandit.exportData()
        assertEquals(20, postData.size)
        val importInstances = import.map { it.instance }
        for ((i, _) in postData)
            assertTrue(i in importInstances)
        assertEquals(20, bandit.candidateSize)
    }

    @Test
    fun importRestructureShrinking() {
        val problem = Problem(emptyArray(), 20)
        val bandit = GeneticAlgorithmBandit(problem, UCB1Normal())
        bandit.candidateSize = 50
        bandit.initialize()

        // We set each score according to a known quantity so we can check that the best candidates are kept later
        for ((i, _) in bandit.exportData())
            bandit.update(i, i.cardinality().toFloat(), 1.0f)

        val import = LocalSearchSolver(problem).asSequence().take(20).toList().map {
            InstanceData<SquaredEstimator>(it, RunningSquaredMeans(0.0f))
        }.toTypedArray()
        bandit.importData(import, true)
        val postData = bandit.exportData()
        assertEquals(50, postData.size)
        val postInstances = postData.map { it.instance }
        for ((i, _) in import)
            assertTrue(i in postInstances)
        assertEquals(50, bandit.candidateSize)
    }

    @Test
    fun importNoRestructure() {
        val problem = Problem(emptyArray(), 20)
        val bandit = GeneticAlgorithmBandit(problem, UCB1Normal())
        bandit.candidateSize = 20
        val preImport = bandit.exportData()
        for (d in preImport) {
            d.data.accept(10.0f)
        }
        bandit.importData(preImport, false)
        val postImport = bandit.exportData()
        val instances = postImport.map { it.instance }

        for ((i, _) in preImport)
            assertTrue(i in instances)
        for ((_, d) in postImport)
            assertEquals(10.0f, d.mean)
    }

    @Test
    fun increaseDecreaseCandidateSizeMaximize() {
        val problem = MODEL1.problem
        val bandit = GeneticAlgorithmBandit(problem, UCB1Normal())
        bandit.candidateSize = 20
        bandit.maximize = true
        bandit.allowDuplicates = false
        bandit.eliminationPeriod = Int.MAX_VALUE
        bandit.elimination = TournamentElimination(Int.MAX_VALUE)

        val rng = Random
        val allInstances = bandit.exportData()
        val scores = FloatArray(20) { rng.nextLogNormal(4.0f, 10.0f) }
        for (i in allInstances.indices) {
            for (j in 1..5)
                bandit.update(allInstances[i].instance, scores[i] + rng.nextNormal())
        }

        val preShrink = bandit.exportData().sortedBy { -it.data.mean }
        bandit.candidateSize = 5
        val postShrink = bandit.exportData().sortedBy { -it.data.mean }
        assertContentEquals(preShrink.slice(0 until 5), postShrink)
    }

    @Test
    fun increaseDecreaseCandidateSizeMinimize() {
        val problem = MODEL1.problem
        val bandit = GeneticAlgorithmBandit(problem, UCB1Normal())
        bandit.candidateSize = 20
        bandit.maximize = false
        bandit.allowDuplicates = false
        bandit.eliminationPeriod = Int.MAX_VALUE
        bandit.elimination = TournamentElimination(Int.MAX_VALUE)

        val rng = Random
        val allInstances = bandit.exportData()
        val scores = FloatArray(20) { rng.nextLogNormal(4.0f, 10.0f) }
        for (i in allInstances.indices) {
            for (j in 1..5)
                bandit.update(allInstances[i].instance, scores[i] + rng.nextNormal())
        }

        val preShrink = bandit.exportData().sortedBy { -it.data.mean }
        bandit.candidateSize = 5
        val postShrink = bandit.exportData().sortedBy { -it.data.mean }
        assertContentEquals(preShrink.slice(15 until 20), postShrink)
    }
}

