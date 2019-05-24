@file:JvmName("DataSamples")

package combo.math

import combo.util.EMPTY_FLOAT_ARRAY
import combo.util.assert
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.math.round
import kotlin.random.Random

fun <S : DataSample> Sequence<Number>.sample(s: S): S {
    forEach { s.accept(it.toFloat()) }
    return s
}

/**
 * Samples a stream of numbers.
 */
interface DataSample {
    fun accept(value: Float)
    fun accept(value: Float, weight: Float) = accept(value)

    /**
     * Returns available data points or estimates.
     */
    fun toArray(): FloatArray

    val nbrSamples: Long
}

object VoidSample : DataSample {
    override fun accept(value: Float) {}
    override fun toArray() = EMPTY_FLOAT_ARRAY
    override val nbrSamples = 0L
}

/**
 * Contains the latest numbers in the stream.
 */
class WindowSample(size: Int) : DataSample {
    private val window = FloatArray(size)
    private var pointer: Int = 0

    override var nbrSamples: Long = 0L
        private set

    override fun accept(value: Float) {
        nbrSamples++
        window[pointer] = value
        pointer = (++pointer % window.size)
    }

    override fun toArray(): FloatArray {
        return if (nbrSamples < window.size) window.sliceArray(0 until nbrSamples.toInt())
        else with(window) { sliceArray(pointer until size) + sliceArray(0 until pointer) }
    }

    val size = window.size
}

/**
 * Stores all data seen.
 */
class FullSample : DataSample {
    private var data = ArrayList<Float>()
    var size: Int = 0
        private set

    override fun accept(value: Float) {
        data.add(value)
        size++
    }

    override val nbrSamples
        get() = size.toLong()

    override fun toArray() = data.toFloatArray()
}

/**
 * Aggregates data into buckets, where each bucket has a fixed size.
 */
class BucketsSample(val samplesPerBucket: Int) : DataSample {
    private var history = ArrayList<VarianceEstimator>(samplesPerBucket)
    override var nbrSamples = 0L
        private set

    init {
        assert(samplesPerBucket > 0)
        history.add(RunningVariance())
    }

    override fun accept(value: Float) {
        history[size - 1].accept(value)
        if (history[size - 1].nbrSamples >= samplesPerBucket) {
            history.add(RunningVariance())
        }
        nbrSamples++
    }

    override fun toArray(): FloatArray {
        val result = ArrayList<Float>()
        history.asSequence()
                .filter { it.nbrSamples.toInt() == samplesPerBucket }
                .forEach { result.add(it.mean) }
        return result.toFloatArray()
    }

    val size get() = history.size
}

/**
 * Keeps a fixed size cache of seen data. Each new data point has a random chance of replacing an old data.
 */
class ReservoirSample(size: Int, private val rng: Random) : DataSample {
    private val data = FloatArray(size)
    override var nbrSamples = 0L
        private set

    override fun accept(value: Float) {
        if (nbrSamples < data.size) {
            data[nbrSamples++.toInt()] = value
        } else {
            val pf = rng.nextFloat() * nbrSamples++
            val p = round(pf).toInt()
            if (p < data.size) {
                data[p] = value
            }
        }
    }

    override fun toArray(): FloatArray {
        return if (nbrSamples < size) data.sliceArray(0 until nbrSamples.toInt())
        else data
    }

    val size = data.size
}

/**
 * Keeps a fixed size history where each element grows in size.
 * @param maxSize size of data, must be even.
 */
class GrowingDataSample @JvmOverloads constructor(maxSize: Int = 10) : DataSample {

    init {
        if (maxSize % 2 != 0) throw IllegalArgumentException("Max size must be even, got $maxSize.")
    }

    private val data = Array(maxSize) { RunningMean() }
    var size = 0
        private set

    var samplesPerBucket = 1
        private set

    override var nbrSamples: Long = 0L
        private set

    override fun accept(value: Float) {
        if (samplesPerBucket.toLong() * data.size.toLong() == nbrSamples) {
            for (i in 0 until data.size step 2)
                data[i / 2] = data[i].combine(data[i + 1])
            size = data.size / 2
            for (i in size until data.size)
                data[i] = RunningMean()
            samplesPerBucket *= 2
        }

        data[size].accept(value)
        nbrSamples++
        if (nbrSamples != 0L && nbrSamples % samplesPerBucket.toLong() == 0L) size++
    }

    override fun toArray() = FloatArray(size) { data[it].mean }
    fun slots() = IntArray(size) { samplesPerBucket * (it + 1) }
}
