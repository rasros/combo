package combo.bandit

import combo.math.DataSample
import combo.math.IntPermutation
import combo.sat.Instance
import combo.util.*
import kotlin.math.min

/**
 * Bandit that can be used in parallel. The [processUpdates] method must be called periodically.
 * @param bandits Each bandit is protected by a lock that will be tested for in sequence in choose.
 * @param batchSize Updates will be grouped into batches with this size to avoid contention.
 * @param mode Type of parallelization attempted.
 */
open class ParallelBandit<D : BanditData> protected constructor(val bandits: Array<Bandit<D>>,
                                                                val batchSize: IntRange,
                                                                val mode: ParallelMode) : Bandit<D> {

    protected var randomSequence = RandomSequence(randomSeed)

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

    override val rewards: DataSample
        get() {
            while (true) {
                for (i in IntPermutation(bandits.size, randomSequence.next())) {
                    val b = bandits[i] as ConcurrentBandit<D>
                    val locked = b.lock.readLock().tryLock()
                    if (!locked) continue
                    try {
                        return b.rewards.copy()
                    } finally {
                        b.lock.readLock().unlock()
                    }
                }
            }
        }

    /**
     * Handle updates added through [update] or [updateAll]
     * @param mayBlock true if the caller should block until there are updates to handle
     * @return number of data points processed
     */
    @Suppress("UNCHECKED_CAST")
    tailrec fun processUpdates(mayBlock: Boolean): Int {
        if (batches == null) {
            // Limited delay mode, immediately process all events
            val events = input.drain(if (mayBlock) batchSize.first else -1).toList()
            if (events.isEmpty()) return 0
            val instances = arrayOfNulls<Instance>(events.size)
            val results = FloatArray(events.size)
            val weights = FloatArray(events.size)
            for (i in 0 until events.size) {
                val e = events[i] as SingleUpdate
                instances[i] = e.instance
                results[i] = e.result
                weights[i] = e.weight
            }
            for (b in bandits)
                b.updateAll(instances as Array<Instance>, results, weights)
            return events.size
        } else {
            // First try if there is a back-log of batches
            val batch = batches.remove()
            if (batch != null) {
                for (b in bandits)
                    b.updateAll(batch.instances, batch.results, batch.weights)
                return batch.instances.size
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
                        val instances = arrayOfNulls<Instance>(batchSize.last)
                        val results = FloatArray(batchSize.last)
                        val weights = FloatArray(batchSize.last)
                        var k = 0
                        for (i in 0 until buffer.size) {
                            val e = buffer.removeAt(buffer.lastIndex)
                            val remaining = e.collectTo(k, instances as Array<Instance>, results, weights)
                            k += e.size
                            if (remaining != null) {
                                buffer.add(remaining)
                                break
                            }
                            if (k >= batchSize.last)
                                break
                        }
                        size -= batchSize.last
                        batches.offer(BatchUpdate(instances as Array<Instance>, results, weights))
                    }
                }
                if (buffer.isNotEmpty()) {
                    appended = true
                    val instances = arrayOfNulls<Instance>(size)
                    val results = FloatArray(size)
                    val weights = FloatArray(size)
                    var k = 0
                    for (e in buffer) {
                        e.collectTo(k, instances as Array<Instance>, results, weights)
                        k += e.size
                    }
                    batches.offer(BatchUpdate(instances as Array<Instance>, results, weights))
                }
                return if (appended) processUpdates(mayBlock)
                else 0
            }
        }
    }

    override fun importData(data: D) {
        for (b in bandits)
            b.importData(data)
    }

    override fun choose(assumptions: IntCollection): Instance? {
        while (true) {
            for (i in IntPermutation(bandits.size, randomSequence.next())) {
                val b = bandits[i] as ConcurrentBandit<D>
                val locked = b.lock.readLock().tryLock()
                if (!locked) continue
                try {
                    return b.choose(assumptions)
                } finally {
                    b.lock.readLock().unlock()
                }
            }
        }
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        while (true) {
            for (i in IntPermutation(bandits.size, randomSequence.next())) {
                val b = bandits[i] as ConcurrentBandit<D>
                val locked = b.lock.readLock().tryLock()
                if (!locked) continue
                try {
                    return b.chooseOrThrow(assumptions)
                } finally {
                    b.lock.readLock().unlock()
                }
            }
        }
    }

    override fun exportData(): D = bandits[0].exportData()

    override fun updateAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
        require(instances.size == results.size) { "Arrays must be same length." }
        if (weights != null) require(weights.size == results.size) { "Arrays must be same length." }
        if (instances.isEmpty()) return
        val batchUpdate = BatchUpdate(instances, results, weights)
        if (mode == ParallelMode.NON_LOCKING && instances.size in batchSize)
            batches!!.add(batchUpdate)
        else if (batches == null)
            for (i in instances.indices)
                update(instances[i], results[i], weights?.get(i) ?: 1.0f)
        else
            input.add(batchUpdate)
    }

    override fun update(instance: Instance, result: Float, weight: Float) {
        input.add(SingleUpdate(instance, result, weight))
    }

    private interface UpdateEvent {
        fun collectTo(offset: Int, instances: Array<Instance>, results: FloatArray, weights: FloatArray): UpdateEvent?
        val size: Int
    }

    private class SingleUpdate(val instance: Instance, val result: Float, val weight: Float) : UpdateEvent {
        override fun collectTo(offset: Int, instances: Array<Instance>, results: FloatArray, weights: FloatArray): UpdateEvent? {
            instances[offset] = instance
            results[offset] = result
            weights[offset] = weight
            return null
        }

        override val size: Int get() = 1
    }

    private class BatchUpdate(val instances: Array<Instance>, val results: FloatArray, val weights: FloatArray?) : UpdateEvent {
        override fun collectTo(offset: Int, instances: Array<Instance>, results: FloatArray, weights: FloatArray): UpdateEvent? {
            val n = this.instances.size
            val m = min(this.instances.size, instances.size - offset)
            this.instances.copyInto(instances, offset, 0, m)
            this.results.copyInto(results, offset, 0, m)
            if (this.weights != null)
                this.weights.copyInto(weights, offset, 0, m)
            else
                for (i in 0 until m)
                    weights[i + offset] = 1.0f
            return if (n > m) {
                BatchUpdate(this.instances.copyOfRange(m, n),
                        this.results.copyOfRange(m, n),
                        this.weights?.copyOfRange(m, n))
            } else null
        }

        override val size: Int = instances.size
    }

    open class Builder<D : BanditData>(protected open val baseBuilder: BanditBuilder<D>) {
        protected var copies: Int = 2
        protected var mode: ParallelMode = ParallelMode.LOCKING
        protected var batchSize: IntRange = 1..50
        protected var assumptionsLock: Boolean = false

        /** Each bandit is protected by a lock that will be tested for in sequence in choose. */
        open fun copies(copies: Int) = apply { this.copies = copies }

        /** Type of parallelization attempted. */
        open fun mode(mode: ParallelMode) = apply { this.mode = mode }

        /** Updates will be grouped into batches with this size to avoid contention. */
        open fun batchSize(batchSize: IntRange) = apply { this.batchSize = batchSize }

        /** Whether assumptions needs locking. Typically set by algorithm specific builder. */
        open fun assumptionsLock(assumptionsLock: Boolean) = apply { this.assumptionsLock = assumptionsLock }

        open fun build(): ParallelBandit<D> {
            val base = baseBuilder.build()
            val array = Array<Bandit<D>>(copies) {
                val bandit = if (it == 0) base
                else baseBuilder.rewards(base.rewards.copy()).build()
                if (assumptionsLock) ConcurrentAssumptionBandit(bandit) else ConcurrentBandit(bandit)
            }
            return ParallelBandit(array, batchSize, mode)
        }
    }

    protected open class ConcurrentBandit<D : BanditData>(open val base: Bandit<D>) : Bandit<D> by base {
        val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()
        override fun choose(assumptions: IntCollection) = lock.read { base.choose(assumptions) }
        override fun chooseOrThrow(assumptions: IntCollection) = lock.read { base.chooseOrThrow(assumptions) }
        override fun update(instance: Instance, result: Float, weight: Float) = lock.write { base.update(instance, result, weight) }
        override fun updateAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) = lock.write { base.updateAll(instances, results, weights) }
        override fun importData(data: D) = lock.write { base.importData(data) }
        override fun exportData() = lock.read { base.exportData() }
    }

    protected class ConcurrentAssumptionBandit<D : BanditData>(base: Bandit<D>) : ConcurrentBandit<D>(base) {
        override fun choose(assumptions: IntCollection) =
                if (assumptions.isNotEmpty()) lock.write { base.choose(assumptions) }
                else lock.read { base.choose(assumptions) }

        override fun chooseOrThrow(assumptions: IntCollection) =
                if (assumptions.isNotEmpty()) lock.write { base.chooseOrThrow(assumptions) }
                else lock.read { base.chooseOrThrow(assumptions) }
    }
}

class ParallelPredictionBandit<D : BanditData>(bandits: Array<Bandit<D>>, batchSize: IntRange, mode: ParallelMode)
    : ParallelBandit<D>(bandits, batchSize, mode), PredictionBandit<D> {

    override fun update(instance: Instance, result: Float, weight: Float) {
        super<ParallelBandit>.update(instance, result, weight)
    }

    override fun predict(instance: Instance): Float {
        while (true) {
            for (i in IntPermutation(bandits.size, randomSequence.next())) {
                val b = bandits[i] as ConcurrentPredictionBandit<D>
                val locked = b.lock.readLock().tryLock()
                if (!locked) continue
                try {
                    return b.predict(instance)
                } finally {
                    b.lock.readLock().unlock()
                }
            }
        }
    }

    override fun train(instance: Instance, result: Float, weight: Float) {
        for (b in bandits)
            (b as ConcurrentPredictionBandit<D>).train(instance, result, weight)
    }

    override val trainAbsError: DataSample
        get() = (bandits[0] as PredictionBandit<D>).trainAbsError

    override val testAbsError: DataSample
        get() = (bandits[0] as PredictionBandit<D>).testAbsError

    class Builder<D : BanditData>(override val baseBuilder: PredictionBanditBuilder<D>) : ParallelBandit.Builder<D>(baseBuilder) {
        override fun build(): ParallelPredictionBandit<D> {
            val base = baseBuilder.build()
            val array = Array<Bandit<D>>(copies) {
                val bandit = if (it == 0) base
                else baseBuilder.rewards(base.rewards.copy()).build()
                if (assumptionsLock) ConcurrentPredictionAssumptionBandit(bandit)
                else ConcurrentPredictionBandit(bandit)
            }
            return ParallelPredictionBandit(array, batchSize, mode)
        }

        override fun copies(copies: Int) = apply { super.copies(copies) }
        override fun mode(mode: ParallelMode) = apply { super.mode(mode) }
        override fun batchSize(batchSize: IntRange) = apply { super.batchSize(batchSize) }
        override fun assumptionsLock(assumptionsLock: Boolean) = apply { super.assumptionsLock(assumptionsLock) }
    }

    private open class ConcurrentPredictionBandit<D : BanditData>(override val base: PredictionBandit<D>) : ConcurrentBandit<D>(base), PredictionBandit<D> {
        override fun choose(assumptions: IntCollection) = lock.read { base.choose(assumptions) }
        override fun chooseOrThrow(assumptions: IntCollection) = lock.read { base.chooseOrThrow(assumptions) }
        override fun update(instance: Instance, result: Float, weight: Float) =
                lock.write { base.update(instance, result, weight) }

        override fun predict(instance: Instance) = lock.read { base.predict(instance) }

        override fun train(instance: Instance, result: Float, weight: Float) =
                lock.write { base.train(instance, result, weight) }

        override fun updateAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) =
                lock.write { base.updateAll(instances, results, weights) }

        override fun importData(data: D) = lock.write { base.importData(data) }
        override fun exportData() = lock.read { base.exportData() }
        override val trainAbsError: DataSample get() = base.trainAbsError
        override val testAbsError: DataSample get() = base.testAbsError
    }

    private class ConcurrentPredictionAssumptionBandit<D : BanditData>(base: PredictionBandit<D>) : ConcurrentPredictionBandit<D>(base) {
        override fun choose(assumptions: IntCollection) =
                if (assumptions.isNotEmpty()) lock.write { base.choose(assumptions) }
                else lock.read { base.choose(assumptions) }

        override fun chooseOrThrow(assumptions: IntCollection) =
                if (assumptions.isNotEmpty()) lock.write { base.chooseOrThrow(assumptions) }
                else lock.read { base.chooseOrThrow(assumptions) }
    }
}
