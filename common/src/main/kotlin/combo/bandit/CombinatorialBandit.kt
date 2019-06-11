package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.DataSample
import combo.math.BucketSample
import combo.math.VarianceEstimator
import combo.sat.Constraint
import combo.sat.Instance
import combo.sat.Tautology
import combo.sat.UnsatisfiableException
import combo.sat.constraints.Conjunction
import combo.util.AtomicLong
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
            it to banditPolicy.baseData().also { e -> banditPolicy.addArm(e) }
        }
    }

    override var randomSeed: Int = nanos().toInt()
        set(value) {
            this.rng = Random(value)
            field = value
        }
    private var rng = Random(randomSeed)

    override var maximize: Boolean = true
    override var rewards: DataSample = BucketSample()

    private val step = AtomicLong()

    @Suppress("UNCHECKED_CAST")
    override fun importData(data: Array<InstanceData<E>>, restructure: Boolean) {
        // Remove all missing instances
        if (restructure) {
            val set = data.mapTo(HashSet()) { it.instance }
            val itr = instanceData.keys.iterator()
            while (itr.hasNext()) {
                val instance = itr.next()
                if (instance !in set)
                    itr.remove()
            }
        }

        for ((instance, e) in data) {
            val old = this.instanceData[instance]
            if (old != null) {
                @Suppress("UNCHECKED_CAST")
                val combined = old.combine(e) as E
                banditPolicy.removeArm(old)
                this.instanceData[instance] = combined
                banditPolicy.addArm(combined)
            } else if (restructure) {
                // Add new instances
                val armData = e.copy() as E
                banditPolicy.addArm(armData)
                this.instanceData[instance] = armData
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
        val e = instanceData[instance]
        if (e != null) banditPolicy.update(e, result, weight)
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val con: Constraint = if (assumptions.isNotEmpty()) Conjunction(assumptions) else Tautology
        val t = step.getAndIncrement()
        val instance = instanceData.maxBy {
            if (con.satisfies(it.key)) banditPolicy.evaluate(it.value, t, maximize, rng)
            else Float.NEGATIVE_INFINITY
        }?.key
        if (instance == null || !con.satisfies(instance))
            throw UnsatisfiableException("No instance matching assumption literals.")
        return instance
    }
}

