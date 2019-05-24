package combo.bandit.ga

import combo.ga.Candidates
import combo.math.VarianceEstimator
import combo.sat.Instance
import combo.sat.MutableInstance
import combo.util.IntHashSet
import combo.util.isEmpty

/**
 * All state of the search is kept here. The score is defined as mean for genetic operators that use score directly.
 */
class BanditCandidates<E : VarianceEstimator>(override val instances: Array<MutableInstance>,
                                              val baseData: E) : Candidates {

    private val origins: LongArray = LongArray(instances.size)
    private var oldestOrigin: Long = 0L
    override var oldestCandidate: Int = 0
        private set

    override var minScore: Float = baseData.mean
        private set
    override var maxScore: Float = baseData.mean
        private set

    override val nbrCandidates: Int
        get() = instances.size
    override val nbrVariables: Int
        get() = instances[0].size

    var nbrUpdates = 0L
        private set

    val estimators: MutableMap<Instance, E> = HashMap()
    val duplications: MutableMap<Instance, IntHashSet> = HashMap()

    init {
        @Suppress("UNCHECKED_CAST")
        for (i in instances.indices)
            addCandidate(i, baseData.copy() as E)
    }

    /**
     * @return false if there are any duplicates left
     */
    fun replaceCandidate(position: Int, instance: Instance, @Suppress("UNCHECKED_CAST") data: E = baseData.copy() as E): E? {
        var last: E? = null
        val oldInstance = instances[position]
        val set = duplications[oldInstance]
        if (set != null) {
            set.remove(position)
            if (set.isEmpty()) {
                last = estimators.remove(oldInstance)
                duplications.remove(oldInstance)
            }
        }
        instances[position] = instance as MutableInstance
        addCandidate(position, data)
        origins[position] = nbrUpdates
        if (oldestCandidate == position) {
            oldestOrigin = Long.MAX_VALUE
            for (i in origins.indices) {
                if (origins[i] < oldestOrigin) {
                    oldestOrigin = origins[i]
                    oldestCandidate = i
                    if (oldestOrigin == 0L) break
                }
            }
        }
        return last
    }

    private fun addCandidate(position: Int, data: E) {
        estimators.getOrPut(instances[position]) {
            duplications[instances[position]] = IntHashSet(nullValue = -1)
            data
        }
        duplications[instances[position]]!!.add(position)
    }

    override fun score(ix: Int) = estimators[instances[ix]]?.mean ?: Float.NEGATIVE_INFINITY

    fun update(newScore: Float) {
        nbrUpdates++
        if (newScore > maxScore || maxScore.isNaN()) maxScore = newScore
        if (newScore < minScore || minScore.isNaN()) minScore = newScore
    }
}