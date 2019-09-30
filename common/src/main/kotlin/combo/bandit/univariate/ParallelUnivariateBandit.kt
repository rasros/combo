package combo.bandit.univariate

import combo.bandit.ParallelMode
import combo.math.DataSample
import combo.math.IntPermutation
import combo.math.VarianceEstimator
import combo.util.*
import kotlin.math.min

/**
 * Univariate bandit that can be used in parallel. The [processUpdates] method must be called periodically.
 */
class ParallelUnivariateBandit<D>(val bandits: Array<ConcurrentUnivariateBandit<D>>,
                                  val batchSize: IntRange,
                                  val mode: ParallelMode) : UnivariateBandit<D> {

    private var randomSequence = RandomSequence(randomSeed)

    // Batches of input where each update is within minBatchSize..maxBatchSize
    private val batches: Sink<BatchUpdate>? = when (mode) {
        ParallelMode.NON_LOCKING -> NonBlockingSink()
        ParallelMode.LOCKING -> LockingSink()
        ParallelMode.BLOCKING -> null
    }

    private val input: Sink<UpdateEvent> = when (mode) {
        ParallelMode.NON_LOCKING -> NonBlockingSink()
        ParallelMode.LOCKING -> LockingSink()
        ParallelMode.BLOCKING -> BlockingSink(batchSize.last)
    }

    init {
        require(!batchSize.isEmpty()) { "Batchsize interval should not be empty." }
        require(batchSize.first >= 0)
    }

    override val randomSeed: Int get() = bandits[0].randomSeed
    override val maximize: Boolean get() = bandits[0].maximize
    override val rewards: DataSample get() = bandits[0].rewards

    /**
     * Handle updates added through [update] or [updateAll]
     * @param mayBlock true if the caller should block until there are updates to handle
     * @return number of data points processed
     */
    tailrec fun processUpdates(mayBlock: Boolean): Int {
        if (batches == null) {
            // Limited delay mode, immediately process all events
            val events = input.drain(if (mayBlock) batchSize.first else -1).toList()
            if (events.isEmpty()) return 0
            val indices = IntArray(events.size)
            val results = FloatArray(events.size)
            val weights = FloatArray(events.size)
            for (i in 0 until events.size) {
                val e = events[i] as SingleUpdate
                indices[i] = e.armIndex
                results[i] = e.result
                weights[i] = e.weight
            }
            for (b in bandits)
                b.updateAll(indices, results, weights)
            return events.size
        } else {
            // First try if there is a back-log of batches
            val batch = batches.remove()
            if (batch != null) {
                for (b in bandits)
                    b.updateAll(batch.armIndices, batch.results, batch.weights)
                return batch.armIndices.size
            } else {

                // Otherwise fetch from input
                val drain = if (mayBlock) input.drain(batchSize.first)
                else input.drain(-1)
                val buffer = ArrayList<UpdateEvent>()
                var size = 0
                var appended = false
                drain.forEach {
                    size += it.size
                    buffer.add(it)
                    while (size >= batchSize.last) {
                        appended = true
                        val indices = IntArray(batchSize.last)
                        val results = FloatArray(batchSize.last)
                        val weights = FloatArray(batchSize.last)
                        var k = 0
                        for (i in 0 until buffer.size) {
                            val e = buffer.removeAt(buffer.lastIndex)
                            val remaining = e.collectTo(k, indices, results, weights)
                            k += e.size
                            if (remaining != null) {
                                buffer.add(remaining)
                                break
                            }
                            if (k >= batchSize.last)
                                break
                        }
                        size -= batchSize.last
                        batches.offer(BatchUpdate(indices, results, weights))
                    }
                }
                if (buffer.isNotEmpty()) {
                    appended = true
                    val indices = IntArray(size)
                    val results = FloatArray(size)
                    val weights = FloatArray(size)
                    var k = 0
                    for (e in buffer) {
                        e.collectTo(k, indices, results, weights)
                        k += e.size
                    }
                    batches.offer(BatchUpdate(indices, results, weights))
                }
                return if (appended) processUpdates(mayBlock)
                else 0
            }
        }
    }

    override fun importData(data: D, replace: Boolean) {
        for (b in bandits)
            b.importData(data, replace)
    }

    override fun choose(): Int {
        val perm = IntPermutation(bandits.size, randomSequence.next())
        while (true) {
            for (i in 0 until bandits.size) {
                val a = bandits[perm.encode(i)].tryChoose()
                if (a >= 0) return a
            }
        }
    }

    override fun exportData(): D = bandits[0].exportData()

    override fun updateAll(armIndices: IntArray, results: FloatArray, weights: FloatArray?) {
        require(armIndices.size == results.size) { "Arrays must be same length." }
        if (weights != null) require(weights.size == results.size) { "Arrays must be same length." }
        if (armIndices.isEmpty()) return
        val batchUpdate = BatchUpdate(armIndices, results, weights)
        if (mode == ParallelMode.NON_LOCKING && armIndices.size in batchSize)
            batches!!.add(batchUpdate)
        else if (batches == null)
            for (i in armIndices.indices)
                update(armIndices[i], results[i], weights?.get(i) ?: 1.0f)
        else
            input.add(batchUpdate)
    }

    override fun update(armIndex: Int, result: Float, weight: Float) {
        input.add(SingleUpdate(armIndex, result, weight))
    }

    private interface UpdateEvent {
        fun collectTo(offset: Int, armIndices: IntArray, results: FloatArray, weights: FloatArray): UpdateEvent?
        val size: Int
    }

    private class SingleUpdate(val armIndex: Int, val result: Float, val weight: Float) : UpdateEvent {
        override fun collectTo(offset: Int, armIndices: IntArray, results: FloatArray, weights: FloatArray): UpdateEvent? {
            armIndices[offset] = armIndex
            results[offset] = result
            weights[offset] = weight
            return null
        }

        override val size: Int get() = 1
    }

    private class BatchUpdate(val armIndices: IntArray, val results: FloatArray, val weights: FloatArray?) : UpdateEvent {
        override fun collectTo(offset: Int, armIndices: IntArray, results: FloatArray, weights: FloatArray): UpdateEvent? {
            val n = this.armIndices.size
            val m = min(this.armIndices.size, armIndices.size - offset)
            this.armIndices.copyInto(armIndices, offset, 0, m)
            this.results.copyInto(results, offset, 0, m)
            if (this.weights != null)
                this.weights.copyInto(weights, offset, 0, m)
            else
                for (i in 0 until m)
                    weights[i + offset] = 1.0f
            return if (n > m) {
                BatchUpdate(this.armIndices.copyOfRange(m, n),
                        this.results.copyOfRange(m, n),
                        this.weights?.copyOfRange(m, n))
            } else null
        }

        override val size: Int = armIndices.size
    }

    class Builder<E : VarianceEstimator>(private val baseBuilder: MultiArmedBandit.Builder<E>) {
        private var copies: Int = 2
        private var mode: ParallelMode = ParallelMode.LOCKING
        private var batchSize: IntRange = 1..50

        fun copies(copies: Int) = apply { this.copies = copies }
        fun mode(mode: ParallelMode) = apply { this.mode = mode }
        fun batchSize(batchSize: IntRange) = apply { this.batchSize = batchSize }
        fun nbrArms(nbrArms: Int) = apply { baseBuilder.nbrArms(nbrArms) }
        fun banditPolicy(banditPolicy: BanditPolicy<E>) = apply { baseBuilder.banditPolicy(banditPolicy) }
        fun randomSeed(randomSeed: Int) = apply { baseBuilder.randomSeed(randomSeed) }
        fun maximize(maximize: Boolean) = apply { baseBuilder.maximize(maximize) }
        fun rewards(rewards: DataSample) = apply { baseBuilder.rewards(rewards) }
        fun build(): ParallelUnivariateBandit<List<E>> {
            val base = baseBuilder.build()
            val array = Array(copies) {
                if (it == 0) ConcurrentUnivariateBandit(base)
                else ConcurrentUnivariateBandit(baseBuilder.rewards(base.rewards.copy()).build())
            }
            return ParallelUnivariateBandit(array, batchSize, mode)
        }
    }
}

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
    override fun update(armIndex: Int, result: Float, weight: Float) = lock.write { base.update(armIndex, result, weight) }
    override fun updateAll(armIndices: IntArray, results: FloatArray, weights: FloatArray?) = lock.write { base.updateAll(armIndices, results, weights) }
    override fun importData(data: D, replace: Boolean) = lock.write { base.importData(data, replace) }
    override fun exportData() = lock.read { base.exportData() }
}
