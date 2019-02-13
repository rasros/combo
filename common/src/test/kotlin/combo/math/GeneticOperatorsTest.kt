package combo.math

import combo.sat.*
import combo.sat.solvers.SolverTest
import combo.util.EMPTY_INT_ARRAY
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun createSatPopulationState(problem: Problem, nbrStates: Int, rng: Random): Pair<PopulationState, Array<SearchState>> {
    val factory = BasicSearchStateFactory(problem)
    val labelingFactory = BitFieldLabelingFactory
    val searchStates = Array(nbrStates) {
        factory.build(labelingFactory.create(problem.nbrVariables), EMPTY_INT_ARRAY, RandomSelector, null, rng)
    }
    val scores = DoubleArray(nbrStates) { searchStates[it].totalUnsatisfied.toDouble() }
    val sum = scores.sum()
    val state = PopulationState(searchStates, scores, IntArray(nbrStates)) to searchStates
    assertEquals(sum, state.first.scores.sum())
    return state
}

class PopulationStateTest {

    @Test
    fun createOne() {
        val (state, _) = createSatPopulationState(SolverTest.SMALL_PROBLEMS[0], 1, Random)
        assertEquals(0, state.oldest)
        assertEquals(state.scores[0], state.maxScore)
        assertEquals(state.scores[0], state.minScore)
    }

    @Test
    fun scoreOrdering() {
        val (state, searchStates) = createSatPopulationState(SolverTest.SMALL_PROBLEMS[2], 10, Random)
        assertEquals(9, state.oldest)
        for (i in 0 until state.populationSize)
            assertEquals(state.scores[i], searchStates[i].totalUnsatisfied.toDouble())
        for (i in 1 until state.populationSize)
            assertTrue(state.scores[i] >= state.scores[i - 1])
    }
}

abstract class CrossoverOperatorTest {

    abstract fun crossoverOperator(): CrossoverOperator

    @Test
    fun crossoverSelf() {
        val p = SolverTest.SMALL_PROBLEMS[4]
        val (state, _) = createSatPopulationState(p, 10, Random)
        val crossoverOperator = crossoverOperator()
        val l1 = state.labelings[0].copy()
        crossoverOperator.crossover(0, 0, 0, state, Random)
        val l2 = state.labelings[0]
        assertEquals(l1, l2)
    }

    @Test
    fun testDifference() {
        for (p in SolverTest.SMALL_PROBLEMS + SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val rng = Random
            val popSize = 10
            val (state, _) = createSatPopulationState(p, popSize, rng)
            val perm = IntPermutation(popSize, rng)
            val p1 = perm.encode(0)
            val p2 = perm.encode(1)
            val c = perm.encode(2)
            val l1 = state.labelings[p1].copy()
            val l2 = state.labelings[p2].copy()
            val crossoverOperator = crossoverOperator()
            crossoverOperator.crossover(p1, p2, c, state, rng)
            for (i in 0 until l1.size) {
                assertEquals(l1[i], state.labelings[p1][i])
                assertEquals(l2[i], state.labelings[p2][i])

                if (l1[i] == l2[i]) assertEquals(l1[i], state.labelings[c][i], "$i")
                else assertTrue(l1[i] == state.labelings[c][i] || l2[i] == state.labelings[c][i])
            }
        }
    }
}

class UniformCrossoverTest : CrossoverOperatorTest() {
    override fun crossoverOperator() = UniformCrossover()
}

class KPointCrossoverTest : CrossoverOperatorTest() {
    override fun crossoverOperator() = KPointCrossover(1)
}

class KPoint2CrossoverTest : CrossoverOperatorTest() {
    override fun crossoverOperator() = KPointCrossover(2)
}

abstract class SelectionOperatorTest {

    abstract fun selectionOperator(popSize: Int): SelectionOperator

    @Test
    fun totalSpread() {
        for (p in SolverTest.SMALL_PROBLEMS + SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val rng = Random(1)
            val n = 10
            val (state, _) = createSatPopulationState(p, n, rng)
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
        val (state, _) = createSatPopulationState(SolverTest.SMALL_PROBLEMS[0], 10, Random)
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
            val rate = mutator.mutationRate(createSatPopulationState(SolverTest.SMALL_PROBLEMS[0], 1, Random).first, Random)
            assertTrue(rate > 0 && rate <= 1, "$n")
        }
    }

    @Test
    fun mutateCanChange() {
        for (p in SolverTest.SMALL_PROBLEMS + SolverTest.SMALL_UNSAT_PROBLEMS + SolverTest.LARGE_PROBLEMS) {
            val mutator = mutationOperator(p.nbrVariables)
            val rng = Random
            val (ps, _) = createSatPopulationState(p, 1, rng)
            val preMutated = ps.labelings[0].copy()
            var postMutated: Labeling
            var itr = 0
            do {
                mutator.mutate(0, ps, rng)
                postMutated = ps.labelings[0].copy()
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
