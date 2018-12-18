package combo.sat

import combo.math.LongPermutation
import combo.util.ConcurrentLong
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random

class LabelingPermutation<T : MutableLabeling> private constructor(val builder: LabelingBuilder<T>, rng: Random, private val nbrVariables: Int) {

    companion object {
        @JvmStatic
        @JvmOverloads
        fun sequence(nbrVariables: Int, rng: Random = Random.Default) = sequence(nbrVariables, BitFieldLabelingBuilder(), rng)

        @JvmStatic
        @JvmOverloads
        fun <T : MutableLabeling> sequence(nbrVariables: Int, builder: LabelingBuilder<T>, rng: Random = Random.Default): Sequence<T> {
            val limit = 2.0.pow(nbrVariables).toInt()
            val r = LabelingPermutation(builder, rng, nbrVariables)
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

    fun next(): T {
        val labeling = builder.build(nbrVariables)
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
