package combo.bandit.ga

import combo.bandit.univariate.BanditPolicy
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.math.RunningVariance
import combo.math.VarianceEstimator
import combo.model.TestModels.MODEL1
import combo.model.TestModels.MODEL3
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.solvers.LocalSearchSolver
import combo.test.assertEquals
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

fun <E : VarianceEstimator> createCandidates(problem: Problem, n: Int, bp: BanditPolicy<E>): BanditCandidates<E> {
    val instances: Array<Instance> = LocalSearchSolver(problem).apply {
        this.randomSeed = randomSeed
    }.asSequence().take(n).toList().toTypedArray()
    return BanditCandidates(instances, 5.0f, true, bp)
}

class BanditCandidatesTest {

    @Test
    fun createOne() {
        val candidates = createCandidates(MODEL1.problem, 1, ThompsonSampling(NormalPosterior, RunningVariance()))
        assertEquals(0, candidates.oldestCandidate)
        val e = candidates.estimators[candidates.instances[0]]!!
        assertEquals(e.mean, candidates.worstScore, 0.0f)
        assertEquals(e.mean, candidates.bestScore, 0.0f)
    }

    @Test
    fun minMaxScoreAfterUpdate() {
        val candidates = createCandidates(MODEL3.problem, 20, ThompsonSampling(NormalPosterior, RunningVariance()))
        candidates.update(candidates.instances[2], 1.0f, 1.0f)

        candidates.maximize = true
        assertEquals(-1.0f, candidates.bestScore, 0.0f)
        assertEquals(0.0f, candidates.worstScore, 0.0f)

        candidates.maximize = false
        assertEquals(0.0f, candidates.bestScore, 0.0f)
        assertEquals(1.0f, candidates.worstScore, 0.0f)
    }

    @Test
    @Ignore
    fun doTheRest() {
        TODO()
    }
    /*
    @Test
    fun candidatesWithAge() {
        val origins = intArrayOf(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6)
        val scores = FloatArray(origins.size) { it.toFloat() }
        val problem = Problem(emptyArray(), 1)
        val validators = Array(origins.size) {
            val instance = BitArray(1)
            Validator(problem, instance, Tautology)
        }
        val candidates = ValidatorCandidates(validators, origins, scores)
        assertEquals(6, candidates.oldestCandidate)
        assertEquals(1, candidates.oldestOrigin)
    }

    @Test
    fun oldestAfterUpdate() {
        val origins = intArrayOf(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6)
        val scores = FloatArray(origins.size) { it.toFloat() }
        val problem = Problem(emptyArray(), 10)
        val validators = Array(origins.size) {
            val instance = BitArray(10)
            WordRandomSet().initialize(instance, Tautology, Random, null)
            Validator(problem, instance, Tautology)
        }
        val candidates = ValidatorCandidates(validators, origins, scores)
        assertEquals(6, candidates.oldestCandidate)
        assertEquals(1, candidates.oldestOrigin)
        candidates.update(6, 10, 0.0f)
        assertEquals(0, candidates.oldestCandidate)
        assertEquals(1, candidates.oldestOrigin)
    }

    @Test
    fun changeToYoungerOrigin() {
        // This tests the similar functionality in GeneticAlgorithmOptimizer that changes the origin to something older
        val origins = IntArray(20) { 10 + it }
        val scores = FloatArray(20) { it.toFloat() }
        val problem = Problem(emptyArray(), 10)
        val validators = Array(origins.size) {
            val instance = BitArray(10)
            WordRandomSet().initialize(instance, Tautology, Random, null)
            Validator(problem, instance, Tautology)
        }

        val candidates = ValidatorCandidates(validators, origins, scores)
        val keep = IntHashSet(nullValue = -1).apply { addAll(0 until 5) }
        for (i in 0 until candidates.nbrCandidates) {
            if (i in keep) {
                candidates.update(i, 1, -1.0f)
            } else {
                candidates.update(i, 0, 1.0f)
            }
        }
        assertTrue(candidates.oldestCandidate >= 5)
        assertEquals(0, candidates.oldestOrigin)
    }
     */
}

