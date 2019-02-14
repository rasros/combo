package combo.math

import combo.sat.*
import combo.sat.solvers.SolverTest
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun createCandidates(problem: Problem, nbrStates: Int, rng: Random): Pair<CandidateSolutions, Array<SearchState>> {
    val factory = BasicSearchStateFactory(problem)
    val instanceFactory = BitFieldInstanceFactory
    val searchStates = Array(nbrStates) {
        factory.build(instanceFactory.create(problem.nbrVariables), EMPTY_INT_ARRAY, RandomSelector, null, rng)
    }
    val scores = DoubleArray(nbrStates) { searchStates[it].totalUnsatisfied.toDouble() }
    val sum = scores.sum()
    val state = CandidateSolutions(searchStates, scores, IntArray(nbrStates)) to searchStates
    assertEquals(sum, state.first.scores.sum())
    return state
}

class CandidateSolutionsTest {

    @Test
    fun createOne() {
        val (state, _) = createCandidates(SolverTest.SMALL_PROBLEMS[0], 1, Random)
        assertEquals(0, state.oldest)
        assertEquals(state.scores[0], state.maxScore)
        assertEquals(state.scores[0], state.minScore)
    }

    @Test
    fun scoreOrdering() {
        val (state, searchStates) = createCandidates(SolverTest.SMALL_PROBLEMS[2], 10, Random)
        assertEquals(9, state.oldest)
        for (i in 0 until state.populationSize)
            assertEquals(state.scores[i], searchStates[i].totalUnsatisfied.toDouble())
        for (i in 1 until state.populationSize)
            assertTrue(state.scores[i] >= state.scores[i - 1])
    }
}

abstract class RecombinationOperatorTest {

    abstract fun crossoverOperator(): RecombinationOperator

    @Test
    fun crossoverSelf() {
        val p = SolverTest.SMALL_PROBLEMS[4]
        val (state, candidates) = createCandidates(p, 10, Random)
        val crossoverOperator = crossoverOperator()
        val instance1 = state.instances[0].copy()
        crossoverOperator.combine(0, 0, 0, state, Random)
        val instance2 = candidates[0]
        assertEquals(instance1, instance2.instance)
    }

    @Test
    fun testDifference() {
        for (p in SolverTest.SMALL_PROBLEMS + SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val rng = Random
            val popSize = 10
            val (state, _) = createCandidates(p, popSize, rng)
            val perm = IntPermutation(popSize, rng)
            val p1 = perm.encode(0)
            val p2 = perm.encode(1)
            val c = perm.encode(2)
            val instance1 = state.instances[p1].copy()
            val instance2 = state.instances[p2].copy()
            val crossoverOperator = crossoverOperator()
            crossoverOperator.combine(p1, p2, c, state, rng)
            for (i in 0 until instance1.size) {
                assertEquals(instance1[i], state.instances[p1][i])
                assertEquals(instance2[i], state.instances[p2][i])

                if (instance1[i] == instance2[i]) assertEquals(instance1[i], state.instances[c][i], "$i")
                else assertTrue(instance1[i] == state.instances[c][i] || instance2[i] == state.instances[c][i])
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
            val (state, _) = createCandidates(p, n, rng)
            val selectionOperator = selectionOperator(n)
            val selected = generateSequence { selectionOperator.select(state, rng) }.take(500).toSet()
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
        val (state, _) = createCandidates(SolverTest.SMALL_PROBLEMS[0], 10, Random)
        for (i in 0 until 8) {
            state.update(i, i, state.scores[i])
        }
        assertEquals(9, OldestElimination().select(state, Random))
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
            var postMutated = BitFieldInstance(n)
            val preMutated = postMutated.copy()
            var itr = 0
            // TODO
            /*
            do {
                mutator.mutate(, rng)
                postMutated = ps.instances[0].copy()
                assertTrue(itr++ <= 50, "Max iterations reached")
            } while (preMutated == postMutated)
            */
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
