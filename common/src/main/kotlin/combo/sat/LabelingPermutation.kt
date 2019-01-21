package combo.sat

import combo.math.LongPermutation
import combo.util.ConcurrentLong
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random

class LabelingPermutation private constructor(val factory: LabelingFactory, rng: Random, private val nbrVariables: Int) {

    companion object {
        fun sequence(nbrVariables: Int, rng: Random) = sequence(nbrVariables, BitFieldLabelingFactory, rng)

        fun sequence(nbrVariables: Int, factory: LabelingFactory, rng: Random): Sequence<MutableLabeling> {
            val limit = 2.0.pow(nbrVariables).toInt()
            val r = LabelingPermutation(factory, rng, nbrVariables)
            return generateSequence { r.next() }.take(limit)
        }
    }

    private val permutation: Array<LongPermutation>
    private var count: ConcurrentLong = ConcurrentLong(0)

    init {
        val size = ceil(nbrVariables / 64.0).toInt()
        val last = 2.0.pow(nbrVariables % 64.0).toLong()
        this.permutation = Array(size) { i ->
            if (i == size - 1) LongPermutation(last, rng) else LongPermutation(Long.MAX_VALUE, rng)
        }
    }

    fun next(): MutableLabeling {
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
