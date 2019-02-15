package combo.math

import combo.sat.*
import combo.sat.solvers.SolverTest
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun createCandidates(problem: Problem, nbrStates: Int, rng: Random): Pair<CandidateSolutions, Array<TrackingInstance>> {
    val factory = BasicTrackingInstanceFactory(problem)
    val instanceFactory = BitFieldInstanceFactory
    val searchStates = Array(nbrStates) {
        factory.build(instanceFactory.create(problem.nbrVariables), EMPTY_INT_ARRAY, RandomInitializer(), null, rng)
    }
    val scores = DoubleArray(nbrStates) { searchStates[it].totalUnsatisfied.toDouble() }
    val sum = scores.sum()
    val candidates = CandidateSolutions(searchStates, scores, IntArray(nbrStates)) to searchStates
    assertEquals(sum, candidates.first.scores.sum())
    return candidates
}

class CandidateSolutionsTest {

    @Test
    fun createOne() {
        val (candidates, _) = createCandidates(SolverTest.SMALL_PROBLEMS[0], 1, Random)
        assertEquals(0, candidates.oldest)
        assertEquals(candidates.scores[0], candidates.maxScore)
        assertEquals(candidates.scores[0], candidates.minScore)
    }

    @Test
    fun scoreOrdering() {
        val (candidates, trackers) = createCandidates(SolverTest.SMALL_PROBLEMS[2], 10, Random)
        assertEquals(9, candidates.oldest)
        for (i in 0 until candidates.nbrCandidates)
            assertEquals(candidates.scores[i], trackers[i].totalUnsatisfied.toDouble())
        for (i in 1 until candidates.nbrCandidates)
            assertTrue(candidates.scores[i] >= candidates.scores[i - 1])
    }
}

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

    abstract fun mutationOperator(nbrVariables: Int): PointMutationOperator

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
            val preMutated = BitFieldInstance(n)
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
