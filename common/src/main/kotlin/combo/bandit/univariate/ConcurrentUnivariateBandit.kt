package combo.bandit.univariate

import combo.math.DataSample
import combo.util.ReadWriteLock
import combo.util.ReentrantReadWriteLock
import combo.util.read
import combo.util.write

/**
 * Protects the base methods in [UnivariateBandit] behind a read/write lock. All data will be protected behind the same
 * lock so there will be lots of contention. [ParallelUnivariateBandit] can provide speedup at some cost to rewards.
 */
class ConcurrentUnivariateBandit<D>(val base: UnivariateBandit<D>, val lock: ReadWriteLock = ReentrantReadWriteLock())
    : UnivariateBandit<D> by base {

    fun tryChoose(): Int {
        val locked = lock.readLock().tryLock()
        if (!locked) return -1
        try {
            return base.choose()
        } finally {
            lock.readLock().unlock()
        }
    }

    override fun choose() = lock.read { base.choose() }
    override fun update(armIndex: Int, result: Float, weight: Float) =
            lock.write { base.update(armIndex, result, weight) }

    override fun updateAll(armIndices: IntArray, results: FloatArray, weights: FloatArray?) =
            lock.write { base.updateAll(armIndices, results, weights) }

    override fun importData(data: D, restructure: Boolean) = lock.write { base.importData(data, restructure) }
    override fun exportData() = lock.read { base.exportData() }
    override var randomSeed: Int
        get() = lock.read { base.randomSeed }
        set(value) {
            lock.write { base.randomSeed = value }
        }
    override var maximize: Boolean
        get() = lock.read { base.maximize }
        set(value) {
            lock.write { base.maximize = value }
        }
    override var rewards: DataSample
        get() = lock.read { base.rewards.copy() }
        set(value) {
            lock.write { base.rewards = value }
        }

    override fun concurrent() = this
}
