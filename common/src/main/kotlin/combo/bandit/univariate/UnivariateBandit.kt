package combo.bandit.univariate

import combo.bandit.BanditParameters
import combo.bandit.ParallelMode
import combo.math.DataSample
import combo.math.VoidSample

/**
 * A bandit optimizes an online binary decision problem. These bandits are uni-variate,
 * ie. there are is a single variable that changes.
 */
interface UnivariateBandit<D> : BanditParameters {

    /**
     * Select the next bandit to use. Indexed from 0 to [nbrArms].
     */
    fun choose(): Int

    fun update(armIndex: Int, result: Float, weight: Float = 1.0f)

    fun updateAll(armIndices: IntArray, results: FloatArray, weights: FloatArray? = null)

    /**
     * Add historic data to the bandit, this can be used to stop and re-start the bandit. The array must be the same
     * length as [nbrArms].
     *
     * @param data added to the bandit.
     * @param restructure whether the bandit structure should exactly fit that of the imported data. If set to true
     * some data might be lost in the import but can be used to keep multiple parallel bandits in sync. If set to false
     * the merge will only be on existing summary statistics.
     */
    fun importData(data: D, restructure: Boolean = false)

    /**
     * Exports all data to use for external storage. They can be used in a new [UnivariateBandit] instance that
     * continues optimizing through the [importData] function. The order of the returned array must be maintained.
     */
    fun exportData(): D

    /**
     * Creates a bandit with the same underlying data that can be used concurrently with blocking.
     */
    fun concurrent(): UnivariateBandit<D>

    /**
     * Creates a bandit that can use both [choose] and [update] in parallel from multiple threads. Note that the
     * [ParallelUnivariateBandit.processUpdates] function must be called periodically, from e.g. a pool of worker
     * threads.
     */
    fun parallel(batchSize: IntRange = 1..50,
                 mode: ParallelMode = ParallelMode.BLOCKING_SUPPORTED,
                 banditCopies: Int = 2): ParallelUnivariateBandit<D>
}

