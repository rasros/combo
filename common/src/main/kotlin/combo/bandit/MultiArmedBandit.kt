package combo.bandit

import combo.math.*
import combo.sat.Conjunction
import combo.sat.Labeling
import combo.sat.UnsatisfiableException
import combo.util.collectionOf
import combo.util.nanos
import kotlin.jvm.JvmOverloads

/**
 * This bandit assigns an independent uni-variate posterior distribution to each of a pre-solved [Labeling].
 * Use the sequence method in [combo.sat.solvers.Solver] to generate the labelings. The posterior is then used for
 * Thompson sampling.
 *
 * For Java, there is a builder to use.
 *
 * @param labelings all arms to use by the bandit.
 * @param posterior the posterior family distribution to use for each labeling. Default is normal distribution.
 * @param prior the arms will start of with the value given here. Make sure that it results in valid parameters to
 * the posterior (eg. variance should be above zero for normal distribution).
 * @param historicData any historic data can be added in the map, this can be used to store and re-start the bandit.
 */
class MultiArmedBandit @JvmOverloads constructor(labelings: Array<Labeling>,
                                                 val posterior: Posterior = GaussianPosterior,
                                                 prior: VarianceStatistic = posterior.defaultPrior(),
                                                 historicData: Array<BanditArmData>? = null) : Bandit {

    private val labelingData: Map<Labeling, VarianceStatistic> = HashMap<Labeling, VarianceStatistic>().apply {
        labelings.associateTo(this) { it to prior.copy() }
        historicData?.forEach { put(it.labeling, it.total) }
    }

    override var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed
    override var maximize: Boolean = true
    override var rewards: DataSample = GrowingDataSample(20)

    private var randomSequence = RandomSequence(nanos())

    /**
     * Exports all data to use for external storage. They can be used to create a new [MultiArmedBandit] instance that
     * continues optimizing through the historicData constructor parameter. The order of the returned array does not
     * matter.
     */
    fun exportData() =
            labelingData.asSequence().map { BanditArmData(it.key, it.value) }.toList().toTypedArray()

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        rewards.accept(result)
        posterior.update(labelingData[labeling]!!, result, weight)
    }

    override fun chooseOrThrow(assumptions: IntArray): Labeling {
        val rng = randomSequence.next()
        val con = Conjunction(collectionOf(assumptions))
        val labeling = if (maximize) {
            labelingData.maxBy { if (con.satisfies(it.key)) posterior.sample(rng, it.value) else Double.NEGATIVE_INFINITY }
        } else {
            labelingData.minBy { if (con.satisfies(it.key)) posterior.sample(rng, it.value) else Double.POSITIVE_INFINITY }
        }?.key
        if (labeling == null || !con.satisfies(labeling)) throw UnsatisfiableException("No labeling matching assumption literals.")
        return labeling
    }
}

class BanditArmData(val labeling: Labeling, val total: VarianceStatistic)