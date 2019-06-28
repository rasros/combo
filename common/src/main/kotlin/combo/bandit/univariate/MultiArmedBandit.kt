package combo.bandit.univariate

import combo.bandit.BanditParameters
import combo.bandit.ParallelMode
import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.math.VoidSample
import combo.util.AtomicLong
import combo.util.RandomSequence
import combo.util.nanos
import kotlin.jvm.JvmOverloads

class MultiArmedBanditParameters<E : VarianceEstimator>(
        val nbrArms: Int,
        val banditPolicy: BanditPolicy<E>,
        override val randomSeed: Int = nanos().toInt(),
        override val maximize: Boolean = true,
        override val rewards: DataSample = VoidSample) : BanditParameters

/**
 * A bandit optimizes an online binary decision problem. These bandits are uni-variate,
 * ie. there are is a single variable that changes.
 */
@Suppress("UNCHECKED_CAST")
class MultiArmedBandit<E : VarianceEstimator>(private val parameters: MultiArmedBanditParameters<E>) : UnivariateBandit<List<E>>, BanditParameters by parameters {

    @JvmOverloads
    constructor(nbrArms: Int,
                banditPolicy: BanditPolicy<E>,
                randomSeed: Int = nanos().toInt(),
                maximize: Boolean = true,
                rewards: DataSample = VoidSample
    ) : this(MultiArmedBanditParameters(nbrArms, banditPolicy, randomSeed, maximize, rewards))

    init {
        require(nbrArms > 0)
    }

    private val randomSequence = RandomSequence(randomSeed)
    private val step = AtomicLong()
    private var data: Array<VarianceEstimator> = Array(nbrArms) { banditPolicy.baseData().also { banditPolicy.addArm(it) } }

    val nbrArms get() = parameters.nbrArms
    val banditPolicy get() = parameters.banditPolicy

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
     * Add historic data to the bandit, this can be used to stop and re-start the bandit. The array must be the same
     * length as [nbrArms].
     */
    override fun importData(data: List<E>, restructure: Boolean) {
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
    override fun exportData(): List<E> {
        val list = ArrayList<E>()
        for (i in data.indices)
            list.add(data[i].copy() as E)
        return list
    }

    override fun concurrent() = ConcurrentUnivariateBandit(this)

    override fun parallel(batchSize: IntRange, mode: ParallelMode, banditCopies: Int): ParallelUnivariateBandit<List<E>> {
        val bandits = Array(banditCopies) {
            MultiArmedBandit(nbrArms, banditPolicy.copy()).concurrent()
        }
        val export = exportData()
        bandits.forEach { it.importData(export) }
        return ParallelUnivariateBandit(bandits, batchSize, mode)
    }
}
