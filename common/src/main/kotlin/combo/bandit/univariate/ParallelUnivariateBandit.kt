package combo.bandit.univariate

import combo.bandit.ParallelMode
import combo.math.IntPermutation
import combo.util.*
import kotlin.math.min

class ParallelUnivariateBandit<D>(val bandits: Array<ConcurrentUnivariateBandit<D>>,
                                  val batchSize: IntRange,
                                  val mode: ParallelMode) : UnivariateBandit<D>, BanditParameters by bandits[0] {

    private var randomSequence = RandomSequence(randomSeed)

    // Batches of input where each update is within minBatchSize..maxBatchSize
    private val batches: Sink<BatchUpdate>? = when (mode) {
        ParallelMode.NON_BLOCKING -> ConcurrentSink()
        ParallelMode.BLOCKING_SUPPORTED -> BlockingSink()
        ParallelMode.BOUNDED_QUEUE -> null
    }

    private val input: Sink<UpdateEvent> = when (mode) {
        ParallelMode.NON_BLOCKING -> ConcurrentSink()
        ParallelMode.BLOCKING_SUPPORTED -> BlockingSink()
        ParallelMode.BOUNDED_QUEUE -> BoundedBlockingSink(batchSize.endInclusive)
    }

    init {
        require(!batchSize.isEmpty()) { "Batchsize interval should not be empty." }
        require(batchSize.first >= 0)
    }

    tailrec fun processUpdates(mayBlock: Boolean): Int {
        if (batches == null) {
            // Limited delay mode, immediately process all events
            val events = input.drain(if (mayBlock) batchSize.start else -1).toList()
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
                val drain = if (mayBlock) input.drain(batchSize.start)
                else input.drain(-1)
                val buffer = ArrayList<UpdateEvent>()
                var size = 0
                var appended = false
                drain.forEach {
                    size += it.size
                    buffer.add(it)
                    while (size >= batchSize.endInclusive) {
                        appended = true
                        val indices = IntArray(batchSize.endInclusive)
                        val results = FloatArray(batchSize.endInclusive)
                        val weights = FloatArray(batchSize.endInclusive)
                        var k = 0
                        for (i in 0 until buffer.size) {
                            val e = buffer.removeAt(buffer.lastIndex)
                            val remaining = e.collectTo(k, indices, results, weights)
                            k += e.size
                            if (remaining != null) {
                                buffer.add(remaining)
                                break
                            }
                            if (k >= batchSize.endInclusive)
                                break
                        }
                        size -= batchSize.endInclusive
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

    override fun importData(data: D, restructure: Boolean) {
        for (b in bandits)
            b.importData(data, restructure)
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
        if (mode == ParallelMode.NON_BLOCKING && armIndices.size in batchSize)
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

    override fun parallel(batchSize: IntRange, mode: ParallelMode, banditCopies: Int) = this
    override fun concurrent() = this

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
}
