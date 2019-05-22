package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.DataSample
import combo.math.GrowingDataSample
import combo.math.VarianceEstimator
import combo.sat.Constraint
import combo.sat.Instance
import combo.sat.Tautology
import combo.sat.UnsatisfiableException
import combo.sat.constraints.Conjunction
import combo.util.IntCollection
import combo.util.isNotEmpty
import combo.util.nanos
import kotlin.collections.set
import kotlin.random.Random

/**
 * This bandit uses an independent univariate bandit policy for each of a pre-defined [Instance].
 * Use the sequence method in [combo.sat.solvers.Solver] to generate the instances.
 *
 * @param instances all arms to use by the bandit.
 * @param banditPolicy the policy that the next bandit arm is selected with.
 */
class CombinatorialBandit<E : VarianceEstimator>(instances: Array<Instance>,
                                                 val banditPolicy: BanditPolicy<E>) : Bandit<Array<InstanceData<E>>> {

    private val instanceData = LinkedHashMap<Instance, E>().apply {
        instances.associateTo(this) {
            it to banditPolicy.baseData().also { banditPolicy.addArm(it) }
        }
    }

    override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            field = value
        }
    private var rng = Random(randomSeed)

    override var maximize: Boolean = true
    override var rewards: DataSample = GrowingDataSample()

    override fun importData(historicData: Array<InstanceData<E>>) {
        for (data in historicData) {
            if (data.instance in instanceData) {
                banditPolicy.removeArm(instanceData[data.instance]!!)
                instanceData[data.instance] = data.data
                banditPolicy.addArm(data.data)
            }
        }
    }

    override fun exportData(): Array<InstanceData<E>> {
        val itr = instanceData.iterator()
        return Array(instanceData.size) {
            val (l, t) = itr.next()
            InstanceData(l, t)
        }
    }

    override fun update(instance: Instance, result: Float, weight: Float) {
        rewards.accept(result, weight)
        val d = instanceData[instance]
        if (d != null) banditPolicy.update(d, result, weight)
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        banditPolicy.round(rng)
        val con: Constraint = if (assumptions.isNotEmpty()) Conjunction(assumptions) else Tautology
        val instance = instanceData.maxBy {
            if (con.satisfies(it.key)) banditPolicy.evaluate(it.value, maximize, rng)
            else Float.NEGATIVE_INFINITY
        }?.key
        if (instance == null || !con.satisfies(instance))
            throw UnsatisfiableException("No instance matching assumption literals.")
        return instance
    }
}

