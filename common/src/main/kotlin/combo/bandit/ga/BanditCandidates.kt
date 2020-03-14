package combo.bandit.ga

import combo.bandit.univariate.BanditPolicy
import combo.ga.Candidates
import combo.math.VarianceEstimator
import combo.sat.Instance
import combo.util.IntHashSet
import combo.util.isEmpty
import kotlin.math.max
import kotlin.math.min

/**
 * All state of the search is kept here. The score is defined as mean for genetic operators that use score directly.
 */
class BanditCandidates(instances: Array<Instance>,
                       val minSamples: Float,
                       val maximize: Boolean,
                       val banditPolicy: BanditPolicy) : Candidates {

    override var instances = instances
        private set
    var origins: LongArray = LongArray(instances.size)
        private set
    private var oldestOrigin: Long = 0L
    override var oldestCandidate: Int = 0
        private set

    private var minMean = Float.POSITIVE_INFINITY
    private var maxMean = Float.NEGATIVE_INFINITY

    override val bestScore: Float get() = if (maximize) -maxMean else minMean
    override val worstScore: Float get() = if (maximize) -minMean else maxMean

    override val nbrCandidates: Int get() = instances.size
    override val nbrVariables: Int get() = instances[0].size

    var step = 0L
        private set

    val estimators: MutableMap<Instance, VarianceEstimator> = HashMap()
    private val duplications: MutableMap<Instance, IntHashSet> = HashMap()

    init {
        for (i in instances.indices)
            indexCandidate(i) { banditPolicy.baseData() }
    }

    override fun score(ix: Int, elimination: Boolean): Float {
        val e = estimators[instances[ix]]!!
        val mean = e.mean
        val score = if (elimination && e.nbrWeightedSamples < minSamples) Float.POSITIVE_INFINITY
        else mean
        return if (maximize) -score else score
    }

    fun estimator(position: Int): VarianceEstimator? = estimators[instances[position]]

    /**
     * Updates the estimator of an instance with new data.
     */
    fun update(instance: Instance, result: Float, weight: Float) {
        val e = estimators[instance] ?: return
        val needUpdateMinMax = (e.mean == maxMean && result < e.mean) || (e.mean == minMean && result > e.mean)
        banditPolicy.update(e, result, weight)
        val newScore = e.mean
        maxMean = max(maxMean, newScore)
        minMean = min(minMean, newScore)
        step++
        if (needUpdateMinMax) calculateMinMax()
    }

    /**
     * @return candidate if it is unique
     */
    fun replaceCandidate(position: Int, instance: Instance): VarianceEstimator? {
        val oldInstance = instances[position]
        instances[position] = instance
        origins[position] = step
        val last = unindexCandidate(position, oldInstance)
        indexCandidate(position) { banditPolicy.baseData() }
        return last
    }

    fun isDuplicated(instance: Instance) = duplications[instance]?.size == 1

    private inline fun indexCandidate(position: Int, data: () -> VarianceEstimator) {
        @Suppress("UNCHECKED_CAST") val e = estimators.getOrPut(instances[position]) {
            duplications[instances[position]] = IntHashSet(nullValue = -1)
            data()
        }
        // dont need to update oldestCandidate, since we are just adding new here
        duplications[instances[position]]!!.add(position)
        maxMean = max(maxMean, e.mean)
        minMean = min(minMean, e.mean)
    }

    private fun unindexCandidate(position: Int, oldInstance: Instance): VarianceEstimator? {
        var last: VarianceEstimator? = null
        val set = duplications[oldInstance]
        if (set != null) {
            set.remove(position)
            if (set.isEmpty()) {
                last = estimators.remove(oldInstance)
                duplications.remove(oldInstance)
            }
        }
        if (oldestCandidate == position) calculateOldest()
        if (last != null && (last.mean == minMean || last.mean == maxMean)) calculateMinMax()
        return last
    }

    fun calculateOldest() {
        oldestOrigin = Long.MAX_VALUE
        for (i in origins.indices) {
            if (origins[i] < oldestOrigin) {
                oldestOrigin = origins[i]
                oldestCandidate = i
                if (oldestOrigin == 0L) break
            }
        }
    }

    fun calculateMinMax() {
        minMean = Float.POSITIVE_INFINITY
        maxMean = Float.NEGATIVE_INFINITY
        for (e in estimators.values) {
            minMean = min(minMean, e.mean)
            maxMean = max(maxMean, e.mean)
        }
    }
}
