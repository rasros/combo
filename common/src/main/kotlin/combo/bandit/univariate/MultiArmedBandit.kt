package combo.bandit.univariate

import combo.bandit.ParallelMode
import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.math.VoidSample
import combo.util.AtomicLong
import combo.util.nanos
import kotlin.random.Random

/**
 * A bandit optimizes an online binary decision problem. These bandits are uni-variate,
 * ie. there are is a single variable that changes.
 */
@Suppress("UNCHECKED_CAST")
class MultiArmedBandit<E : VarianceEstimator>(val nbrArms: Int, val banditPolicy: BanditPolicy<E>) : UnivariateBandit<Array<E>> {

    init {
        require(nbrArms > 0)
    }

    /**
     * Set the random seed to a specific value to have a reproducible algorithm.
     */
    override var randomSeed: Int = nanos().toInt()
        set(value) {
            field = value
            this.rng = Random(value)
        }
    private var rng = Random(randomSeed)

    /**
     * Whether the bandit should maximize or minimize the total rewards.
     */
    override var maximize: Boolean = true

    /**
     * A sample of the total rewards obtained, for use in analysis and debugging.
     */
    override var rewards: DataSample = VoidSample

    private val step = AtomicLong()

    private var data: Array<VarianceEstimator> = Array(nbrArms) { banditPolicy.baseData().also { banditPolicy.addArm(it) } }

    /**
     * Select the next bandit to use. Indexed from 0 to [nbrArms].
     */
    override fun choose(): Int {
        val t = step.getAndIncrement()
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
     * Add historic data to the bandit, this can be used to stop and re-start the bandit. The array must be the same
     * length as [nbrArms].
     */
    override fun importData(data: Array<E>, restructure: Boolean) {
        if (restructure) {
            for (i in 0 until nbrArms) {
                banditPolicy.removeArm(this.data[i] as E)
            }
            this.data = Array(data.size) { e ->
                data[e].also { banditPolicy.addArm(it) }
            }
        } else {
            require(data.size == nbrArms) { "Inconsistent array length with number of arms." }
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
    override fun exportData(): Array<E> {
        return Array(data.size) { data[it].copy() } as Array<E>
    }

    override fun concurrent() = ConcurrentUnivariateBandit(this)

    override fun parallel(batchSize: IntRange, mode: ParallelMode, banditCopies: Int): ParallelUnivariateBandit<Array<E>> {
        val bandits = Array(banditCopies) {
            MultiArmedBandit(nbrArms, banditPolicy.copy()).concurrent()
        }
        val export = exportData()
        bandits.forEach { it.importData(export) }
        return ParallelUnivariateBandit(bandits, batchSize, mode)
    }
}
