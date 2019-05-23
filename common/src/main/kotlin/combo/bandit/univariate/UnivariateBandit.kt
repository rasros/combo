package combo.bandit.univariate

import combo.math.DataSample
import combo.math.GrowingDataSample
import combo.math.VarianceEstimator
import combo.util.nanos
import kotlin.random.Random

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
    var randomSeed: Int = nanos().toInt()
        set(value) {
            field = value
            this.rng = Random(value)
        }
    private var rng = Random(randomSeed)

    /**
     * Whether the bandit should maximize or minimize the total rewards.
     */
    var maximize: Boolean = true

    /**
     * A sample of the total rewards obtained, for use in analysis and debugging.
     */
    var rewards: DataSample = GrowingDataSample()

    private val data: Array<VarianceEstimator> = Array(nbrArms) { banditPolicy.baseData().also { banditPolicy.addArm(it) } }

    /**
     * Select the next bandit to use. Indexed from 0 to [nbrArms].
     */
    fun choose(): Int {
        banditPolicy.round(rng)
        return (nbrArms - 1 downTo 0).maxBy { banditPolicy.evaluate(data[it] as E, maximize, rng) }!!
    }

    fun update(armIndex: Int, result: Float, weight: Float = 1.0f) {
        banditPolicy.update(data[armIndex] as E, result, weight)
        rewards.accept(result, weight)
    }

    /**
     * Add historic data to the bandit, this can be used to stop and re-start the bandit. The array must be the same
     * length as [nbrArms].
     */
    fun importData(historicData: Array<E>) {
        require(historicData.size == nbrArms){"Inconsistent array length with number of arms."}
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
    fun exportData(): Array<E> = Array(data.size) { data[it].copy() } as Array<E> //.apply { data.forEach { add(it as E) } }
}

