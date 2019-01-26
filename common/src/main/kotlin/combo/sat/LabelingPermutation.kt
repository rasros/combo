package combo.sat

import combo.math.LongPermutation
import combo.util.ConcurrentLong
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random

/**
 * This class iterates over a labeling in a random order without repetitions.
 */
class LabelingPermutation constructor(private val nbrVariables: Int, val factory: LabelingFactory, rng: Random) : Iterator<Labeling> {

    private val permutation: Array<LongPermutation>
    private var count: ConcurrentLong = ConcurrentLong(0)

    init {
        val size = ceil(nbrVariables / 64.0).toInt()
        val last = 2.0.pow(nbrVariables % 64.0).toLong()
        this.permutation = Array(size) { i ->
            if (i == size - 1) LongPermutation(last, rng) else LongPermutation(Long.MAX_VALUE, rng)
        }
    }

    private val limit = 2.0.pow(nbrVariables).toLong()

    override fun hasNext() = count.get() < limit

    override fun next(): MutableLabeling {
        val labeling = factory.create(nbrVariables)
        val c = count.getAndIncrement()
        for ((i, perm) in permutation.withIndex()) {
            val mask = perm.encode(c)
            for (j in 0 until 64) {
                if (i * 64 + j >= nbrVariables) break
                labeling[i * 64 + j] = (mask and (1L shl j)) != 0L
            }
        }
        return labeling
    }
}
