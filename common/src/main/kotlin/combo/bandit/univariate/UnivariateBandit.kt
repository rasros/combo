package combo.bandit.univariate

import combo.bandit.BanditParameters

/**
 * A bandit optimizes an online decision problem, the bandit problem. The problem is to find the best arm of the
 * available arms.
 */
interface UnivariateBandit<D> : BanditParameters {

    /**
     * Select the next bandit to use.
     */
    fun choose(): Int

    fun update(armIndex: Int, result: Float, weight: Float = 1.0f)

    fun updateAll(armIndices: IntArray, results: FloatArray, weights: FloatArray? = null)

    /**
     * Add historic data to the bandit, this can be used to stop and re-start the bandit.
     *
     * @param data added to the bandit.
     * @param replace whether the bandit structure should replace old data with new.
     */
    fun importData(data: D, replace: Boolean = false)

    /**
     * Exports all data to use for external storage. They can be used in a new [UnivariateBandit] instance that
     * continues optimizing through the [importData] function. The order of the returned array must be maintained.
     */
    fun exportData(): D
}
