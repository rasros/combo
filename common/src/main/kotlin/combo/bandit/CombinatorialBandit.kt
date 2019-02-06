package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.*
import combo.sat.Conjunction
import combo.sat.Labeling
import combo.sat.UnsatisfiableException
import combo.util.collectionOf
import combo.util.nanos
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * This bandit uses an independent univariate bandit policy for each of a pre-defined [Labeling].
 * Use the sequence method in [combo.sat.solvers.Solver] to generate the labelings.
 *
 * @param labelings all arms to use by the bandit.
 * @param banditPolicy the policy that the next bandit arm is selected with.
 */
class CombinatorialBandit<E : VarianceEstimator>(labelings: Array<Labeling>,
                                                 val banditPolicy: BanditPolicy<E>) : Bandit<Array<LabelingData<E>>> {

    private val labelingData = LinkedHashMap<Labeling, E>().apply {
        labelings.associateTo(this) {
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
            if (data.labeling in labelingData) {
                banditPolicy.removeArm(labelingData[data.labeling]!!)
                labelingData[data.labeling] = data.data
                banditPolicy.addArm(data.data)
            }
        }
    }

    override fun exportData(): Array<LabelingData<E>> {
        val itr = labelingData.iterator()
        return Array(labelingData.size) {
            val (l, t) = itr.next()
            LabelingData(l, t)
        }
    }

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        rewards.accept(result, weight)
        banditPolicy.completeRound(labelingData[labeling]!!, result, weight)
    }

    override fun chooseOrThrow(assumptions: IntArray): Labeling {
        val rng = randomSequence.next()
        banditPolicy.beginRound(rng)
        val con = Conjunction(collectionOf(assumptions))
        val labeling = labelingData.maxBy {
            val s = if (con.satisfies(it.key)) banditPolicy.evaluate(it.value, rng)
            else Double.NEGATIVE_INFINITY
            if (maximize) s else -s
        }?.key
        if (labeling == null || !con.satisfies(labeling))
            throw UnsatisfiableException("No labeling matching assumption literals.")
        return labeling
    }
}

