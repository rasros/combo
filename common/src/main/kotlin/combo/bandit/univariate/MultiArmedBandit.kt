package combo.bandit.univariate

import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.math.VoidSample
import combo.util.AtomicLong
import combo.util.RandomSequence
import combo.util.nanos
import kotlin.jvm.JvmOverloads

/**
 * A univariate bandit with fixed number of arms. It stores one estimator per arm.
 */
@Suppress("UNCHECKED_CAST")
class MultiArmedBandit<E : VarianceEstimator> @JvmOverloads constructor(
        val nbrArms: Int,
        val banditPolicy: BanditPolicy<E>,
        override val randomSeed: Int = nanos().toInt(),
        override val maximize: Boolean = true,
        override val rewards: DataSample = VoidSample) : UnivariateBandit<List<E>> {

    init {
        require(nbrArms > 0)
    }

    private val randomSequence = RandomSequence(randomSeed)
    private val step = AtomicLong()
    private val data: Array<VarianceEstimator> = Array(nbrArms) { banditPolicy.baseData().also { banditPolicy.addArm(it) } }

    /**
     * Select the next bandit to use. Indexed from 0 to [nbrArms].
     */
    override fun choose(): Int {
        val t = step.getAndIncrement()
        val rng = randomSequence.next()
        return (0 until nbrArms).maxBy { banditPolicy.evaluate(data[it] as E, t, maximize, rng) }!!
    }

    override fun update(armIndex: Int, result: Float, weight: Float) {
        rewards.accept(result, weight)
        banditPolicy.update(data[armIndex] as E, result, weight)
    }

    override fun updateAll(armIndices: IntArray, results: FloatArray, weights: FloatArray?) {
        require(armIndices.size == results.size) { "Arrays must be same length." }
        if (weights != null) require(weights.size == results.size) { "Arrays must be same length." }
        for (i in armIndices.indices) {
            val weight = weights?.get(i) ?: 1.0f
            val value = results[i]
            rewards.accept(value, weight)
            banditPolicy.update(data[armIndices[i]] as E, value, weight)
        }
    }

    /**
     * The array must be the same length as [nbrArms].
     */
    override fun importData(data: List<E>, replace: Boolean) {
        require(data.size == nbrArms) { "Inconsistent array length with number of arms." }
        if (replace) {
            for (i in 0 until nbrArms) {
                banditPolicy.removeArm(this.data[i] as E)
                banditPolicy.addArm(data[i])
            }
        } else {
            for (i in 0 until nbrArms) {
                banditPolicy.removeArm(this.data[i] as E)
                this.data[i] = this.data[i].combine(data[i])
                banditPolicy.addArm(this.data[i] as E)
            }
        }
    }

    /**
     * Exports all data to use for external storage. They can be used in a new [UnivariateBandit] instance that
     * continues optimizing through the [importData] function. The order of the returned array must be maintained.
     */
    override fun exportData(): List<E> {
        val list = ArrayList<E>()
        for (i in data.indices)
            list.add(data[i].copy() as E)
        return list
    }

    /**
     * [nbrArms] and [banditPolicy] are mandatory.
     */
    class Builder<E : VarianceEstimator> {
        private var nbrArms: Int? = null
        private lateinit var banditPolicy: BanditPolicy<E>
        private var randomSeed: Int = nanos().toInt()
        private var maximize: Boolean = true
        private var rewards: DataSample = VoidSample

        fun nbrArms(nbrArms: Int) = apply { this.nbrArms = nbrArms }
        fun banditPolicy(banditPolicy: BanditPolicy<E>) = apply { this.banditPolicy = banditPolicy }
        fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        fun parallel() = ParallelUnivariateBandit.Builder(this)
        fun build() = MultiArmedBandit(nbrArms
                ?: throw error("nbrArms must be set"), banditPolicy, randomSeed, maximize, rewards)
    }
}
