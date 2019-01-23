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
 * @param maximize whether the bandit should maximize or minimize the total rewards.
 * @param historicData any historic data can be added in the map, this can be used to store and re-start the bandit.
 * @param randomSeed set the random seed to a specific value to have a reproducible algorithm.
 * @param posterior the posterior family distribution to use for each labeling. Default is normal distribution.
 * @param prior the arms will start of with the value given here. Make sure that it results in valid parameters to
 * the posterior (eg. variance should be above zero for normal distribution).
 * @param rewards sample of the obtained rewards for analysis convenience.
 */
class MultiArmedBandit @JvmOverloads constructor(labelings: Array<Labeling>,
                                                 val maximize: Boolean = true,
                                                 historicData: Map<Labeling, VarianceStatistic>? = null,
                                                 val randomSeed: Long = nanos(),
                                                 val posterior: Posterior = NormalPosterior,
                                                 prior: VarianceStatistic = posterior.defaultPrior(),
                                                 override val rewards: DataSample = GrowingDataSample(20)) : Bandit {

    val labelingData: Map<Labeling, VarianceStatistic> = HashMap<Labeling, VarianceStatistic>().apply {
        labelings.associateTo(this) { it to prior.copy() }
        if (historicData != null) putAll(historicData)
    }

    private val randomSequence = RandomSequence(randomSeed)

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
