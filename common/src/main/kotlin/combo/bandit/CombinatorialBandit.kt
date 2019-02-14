package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.*
import combo.sat.Conjunction
import combo.sat.Instance
import combo.sat.UnsatisfiableException
import combo.util.collectionOf
import combo.util.nanos
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * This bandit uses an independent univariate bandit policy for each of a pre-defined [Instance].
 * Use the sequence method in [combo.sat.solvers.Solver] to generate the instances.
 *
 * @param instances all arms to use by the bandit.
 * @param banditPolicy the policy that the next bandit arm is selected with.
 */
class CombinatorialBandit<E : VarianceEstimator>(instances: Array<Instance>,
                                                 val banditPolicy: BanditPolicy<E>) : Bandit<Array<LabelingData<E>>> {

    private val instanceData = LinkedHashMap<Instance, E>().apply {
        instances.associateTo(this) {
            it to banditPolicy.baseData().also { banditPolicy.addArm(it) }
        }
    }

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
    override var maximize: Boolean = true
    override var rewards: DataSample = GrowingDataSample()

    private var randomSequence = RandomSequence(nanos())

    override fun importData(historicData: Array<LabelingData<E>>) {
        for (data in historicData) {
            if (data.instance in instanceData) {
                banditPolicy.removeArm(instanceData[data.instance]!!)
                instanceData[data.instance] = data.data
                banditPolicy.addArm(data.data)
            }
        }
    }

    override fun exportData(): Array<LabelingData<E>> {
        val itr = instanceData.iterator()
        return Array(instanceData.size) {
            val (l, t) = itr.next()
            LabelingData(l, t)
        }
    }

    override fun update(instance: Instance, result: Double, weight: Double) {
        rewards.accept(result, weight)
        banditPolicy.completeRound(instanceData[instance]!!, result, weight)
    }

    override fun chooseOrThrow(assumptions: IntArray): Instance {
        val rng = randomSequence.next()
        banditPolicy.beginRound(rng)
        val con = Conjunction(collectionOf(assumptions))
        val instance = instanceData.maxBy {
            val s = if (con.satisfies(it.key)) banditPolicy.evaluate(it.value, rng)
            else Double.NEGATIVE_INFINITY
            if (maximize) s else -s
        }?.key
        if (instance == null || !con.satisfies(instance))
            throw UnsatisfiableException("No instance matching assumption literals.")
        return instance
    }
}

