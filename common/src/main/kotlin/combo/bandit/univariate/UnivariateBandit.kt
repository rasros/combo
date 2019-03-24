package combo.bandit.univariate

import combo.math.DataSample
import combo.math.GrowingDataSample
import combo.math.RandomSequence
import combo.math.VarianceEstimator
import combo.util.nanos

/**
 * A bandit optimizes an online binary decision problem. These bandits are uni-variate,
 * ie. there are is a single variable that changes.
 */
@Suppress("UNCHECKED_CAST")
class UnivariateBandit<E : VarianceEstimator>(val nbrArms: Int, val banditPolicy: BanditPolicy<E>) {

    init {
        require(nbrArms > 0)
    }

    /**
     * Set the random seed to a specific value to have a reproducible algorithm.
     */
    var randomSeed: Long
        set(value) {
            this.randomSequence = RandomSequence(value)
        }
        get() = randomSequence.startingSeed

    /**
     * Whether the bandit should maximize or minimize the total rewards.
     */
    var maximize: Boolean = true

    /**
     * A sample of the total rewards obtained, for use in analysis and debugging.
     */
    var rewards: DataSample = GrowingDataSample()
    private var randomSequence = RandomSequence(nanos())

    private val data: Array<VarianceEstimator> = Array(nbrArms) { banditPolicy.baseData().also { banditPolicy.addArm(it) } }

    /**
     * Select the next bandit to use. Indexed from 0 to [nbrArms].
     */
    fun choose(): Int {
        val rng = randomSequence.next()
        banditPolicy.beginRound(rng)
        return (0 until nbrArms).maxBy {
            val score = banditPolicy.evaluate(data[it] as E, rng)
            if (maximize) score else -score
        }!!
    }

    fun update(armIndex: Int, result: Float, weight: Float = 1.0f) {
        banditPolicy.completeRound(data[armIndex] as E, result, weight)
        rewards.accept(result, weight)
    }

    /**
     * Add historic data to the bandit, this can be used to stop and re-start the bandit. The array must be the same
     * length as [nbrArms].
     */
    fun importData(historicData: Array<E>) {
        require(historicData.size == nbrArms)
        @Suppress("UNCHECKED_CAST")
        for (i in 0 until nbrArms) {
            banditPolicy.removeArm(data[i] as E)
            data[i] = historicData[i].copy() as E
            banditPolicy.addArm(data[i] as E)
        }
    }

    /**
     * Exports all data to use for external storage. They can be used in a new [UnivariateBandit] instance that
     * continues optimizing through the [importData] function. The order of the returned array must be maintained.
     */
    fun exportData(): Array<E> = data.copyOf() as Array<E>
}

