package combo.math

import combo.sat.*
import combo.sat.solvers.OptimizerCandidateSolutions
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random

data class ExpandedCandidates(val candidates: CandidateSolutions,
                              val instances: Array<TrackingInstance>,
                              val scores: FloatArray)

fun createCandidates(problem: Problem, nbrStates: Int, rng: Random): ExpandedCandidates {
    val factory = BasicTrackingInstanceFactory(problem)
    val instanceFactory = BitArrayFactory
    val instances = Array(nbrStates) {
        factory.build(instanceFactory.create(problem.nbrVariables), EMPTY_INT_ARRAY, RandomInitializer(), null, rng)
    }
    val scores = FloatArray(nbrStates) { instances[it].totalUnsatisfied.toFloat() }
    val candidates = OptimizerCandidateSolutions(instances, IntArray(nbrStates), scores)
    return ExpandedCandidates(candidates, instances, scores)
}

/*
class CandidateSolutionsTest {

    @Test
    fun createOne() {
        val (candidates, _, scores) = createCandidates(SolverTest.SMALL_PROBLEMS[0], 1, Random)
        assertEquals(0, candidates.oldestCandidate)
        assertEquals(scores[0], candidates.maxScore)
        assertEquals(scores[0], candidates.minScore)
    }

    @Test
    fun minMaxScore() {
        val (candidates, instances, _) = createCandidates(SolverTest.SMALL_PROBLEMS[2], 20, Random)
        val min = instances.map { it.totalUnsatisfied.toDouble() }.min()!!
        val max = instances.map { it.totalUnsatisfied.toDouble() }.max()!!
        assertEquals(min, candidates.minScore)
        assertEquals(max, candidates.maxScore)
    }

    @Test
    fun candidatesWithAge() {
        val origins = intArrayOf(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6)
        val score = DoubleArray(origins.size) { it.toDouble() }
        val candidates = OptimizerCandidateSolutions(Array(origins.size) { BitArray(1) }, origins) { score[it] }
        assertEquals(6, candidates.oldestCandidate)
        assertEquals(1, candidates.oldestOrigin)
    }

    @Test
    fun oldestAfterUpdate() {
        val origins = intArrayOf(1, 2, 3, 4, 5, 6, 1, 2, 3, 4, 5, 6)
        val score = DoubleArray(origins.size) { it.toDouble() }
        val candidates = OptimizerCandidateSolutions(Array(origins.size) { BitArray(1) }, origins) { score[it] }
        assertEquals(6, candidates.oldestCandidate)
        assertEquals(1, candidates.oldestOrigin)
        candidates.update(6, 10, 0.0)
        assertEquals(0, candidates.oldestCandidate)
        assertEquals(1, candidates.oldestOrigin)
    }

    @Test
    fun changeToYoungerOrigin() {
        // This tests the similar functionality in GAOptimizer that changes the origin to something older
        val origins = IntArray(20) { 10 + it }
        val score = DoubleArray(20) { it.toDouble() }

        val candidates = OptimizerCandidateSolutions(Array(20) { BitArray(1) }, origins) { score[it] }
        val keep = IntHashSet().apply { addAll(0 until 5) }
        for (i in 0 until candidates.nbrCandidates) {
            if (i in keep) {
                candidates.update(i, 1, -1.0)
            } else {
                candidates.update(i, 0, 1.0)
            }
        }
        assertTrue(candidates.oldestCandidate >= 5)
        assertEquals(0, candidates.oldestOrigin)
    }
}
*/

/*
abstract class RecombinationOperatorTest {

    abstract fun crossoverOperator(): RecombinationOperator

    @Test
    fun crossoverSelf() {
        val p = SolverTest.SMALL_PROBLEMS[4]
        val (candidates, trackers) = createCandidates(p, 10, Random)
        val crossoverOperator = crossoverOperator()
        val instance1 = candidates.instances[0].copy()
        crossoverOperator.combine(0, 0, 0, candidates, Random)
        val instance2 = trackers[0]
        assertEquals(instance1, instance2.instance)
    }

    @Test
    fun testDifference() {
        for (p in SolverTest.SMALL_PROBLEMS + SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val rng = Random
            val popSize = 10
            val (candidates, _) = createCandidates(p, popSize, rng)
            val perm = IntPermutation(popSize, rng)
            val p1 = perm.encode(0)
            val p2 = perm.encode(1)
            val c = perm.encode(2)
            val instance1 = candidates.instances[p1].copy()
            val instance2 = candidates.instances[p2].copy()
            val crossoverOperator = crossoverOperator()
            crossoverOperator.combine(p1, p2, c, candidates, rng)
            for (i in 0 until instance1.size) {
                assertEquals(instance1[i], candidates.instances[p1][i])
                assertEquals(instance2[i], candidates.instances[p2][i])

                if (instance1[i] == instance2[i]) assertEquals(instance1[i], candidates.instances[c][i], "$i")
                else assertTrue(instance1[i] == candidates.instances[c][i] || instance2[i] == candidates.instances[c][i])
            }
        }
    }
}

class UniformRecombinationTest : RecombinationOperatorTest() {
    override fun crossoverOperator() = UniformRecombination()
}

class KPointRecombinationTest : RecombinationOperatorTest() {
    override fun crossoverOperator() = KPointRecombination(1)
}

class KPoint2CrossoverTest : RecombinationOperatorTest() {
    override fun crossoverOperator() = KPointRecombination(2)
}

abstract class SelectionOperatorTest {

    abstract fun selectionOperator(popSize: Int): SelectionOperator

    @Test
    fun totalSpread() {
        for (p in SolverTest.SMALL_PROBLEMS + SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val rng = Random(1)
            val n = 10
            val (candidates, _) = createCandidates(p, n, rng)
            val selectionOperator = selectionOperator(n)
            val selected = generateSequence { selectionOperator.select(candidates, rng) }.take(500).toSet()
            // Uniquely worst genome can never be selected by tournament with size=2
            assertTrue(selected.size + 1 >= n)
        }
    }
}

class UniformSelectionTest : SelectionOperatorTest() {
    override fun selectionOperator(popSize: Int) = UniformSelection()
}

class StochasticAcceptanceSelectionTest : SelectionOperatorTest() {
    override fun selectionOperator(popSize: Int) = StochasticAcceptanceSelection()
}

class TournamentSelectionTest : SelectionOperatorTest() {
    override fun selectionOperator(popSize: Int) = TournamentSelection(2)
}

class OldestEliminationTest {
    @Test
    fun selectOldest() {
        val (candidates, _) = createCandidates(SolverTest.SMALL_PROBLEMS[0], 10, Random)
        for (i in 0 until 8) {
            candidates.update(i, i, candidates.scores[i])
        }
        assertEquals(9, OldestElimination().select(candidates, Random))
    }
}

abstract class PointMutationOperatorTest {

    abstract fun mutationOperator(nbrVariables: Int): RateMutationOperator

    @Test
    fun mutationRate() {
        for (n in 1..20) {
            val mutator = mutationOperator(n)
            val rate = mutator.mutationRate(n, Random)
            assertTrue(rate > 0 && rate <= 1, "$n")
        }
    }

    @Test
    fun mutateCanChange() {
        for (n in 1..20) {
            val mutator = mutationOperator(n)
            val rng = Random
            val preMutated = BitArray(n)
            for (j in 0 until n) preMutated[j] = rng.nextBoolean()
            val postMutated = preMutated.copy()
            var itr = 0
            do {
                mutator.mutate(postMutated, rng)
                assertTrue(itr++ <= 50, "Max iterations reached")
            } while (preMutated == postMutated)
        }
    }
}

class FastGAMutationTest : PointMutationOperatorTest() {
    override fun mutationOperator(nbrVariables: Int) = FastGAMutation(nbrVariables, 1.5)
}

class FixedRateMutationTest : PointMutationOperatorTest() {
    override fun mutationOperator(nbrVariables: Int) = FixedRateMutation()
}

class FixedRate2MutationTest : PointMutationOperatorTest() {
    override fun mutationOperator(nbrVariables: Int) = FixedRateMutation(2)
}
*/
