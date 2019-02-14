package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.*
import combo.sat.Instance
import combo.sat.Literals
import combo.sat.Problem
import combo.sat.solvers.LocalSearchSolver
import combo.sat.solvers.Solver
import combo.util.nanos
import kotlin.jvm.JvmOverloads

class GABandit<E : VarianceEstimator> @JvmOverloads constructor(
        val problem: Problem,
        val banditPolicy: BanditPolicy<E>,
        val solver: Solver = LocalSearchSolver(problem)) : Bandit<Array<LabelingData<E>>> {

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
    private var randomSequence = RandomSequence(nanos())

    override var rewards: DataSample = GrowingDataSample()
    override var maximize = true

    var populationSize: Int = 20
        set(value) {
            require(!populationDelegate.isInitialized()) { "Population size cannot be changed after use." }
            field = value
        }

    var selectionOperator: SelectionOperator = TournamentSelection(5)
    var elimination: SelectionOperator = OldestElimination()
    var mutationOperator: MutationOperator = FixedRateMutation()

    private val populationDelegate = lazy {
        CandidateSolutions(arrayOf(), doubleArrayOf(), intArrayOf())
    }
    private val population by populationDelegate

    override fun chooseOrThrow(assumptions: Literals): Instance {
        TODO("not implemented")
    }

    override fun update(instance: Instance, result: Double, weight: Double) {
        /*
        val ix = population.instances.indexOfFirst { it == instance }
        if (ix < 0) return
        populationState.scores[ix] =
        val rng = randomSequence.next()
        val parent1 = selectionOperator.select(populationState, rng)
        val parent2 = selectionOperator.select(populationState, rng)
        val eliminated = elimination.select(populationState, rng)
        val l1 = populationState.population[parent1].instance.toLiterals(false)
        val l2 = populationState.population[parent2].instance.toLiterals(false)
        val assumptions = l1.intersect(IntList().apply { addAll(l2) }).toList().toTypedArray().apply { sort() }
        */

        TODO("not implemented")
    }

    override fun exportData(): Array<LabelingData<E>> {
        TODO("not implemented")
    }

    override fun importData(historicData: Array<LabelingData<E>>) {
        TODO("not implemented")
    }
}
