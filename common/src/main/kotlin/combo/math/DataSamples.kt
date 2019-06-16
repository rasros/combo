@file:JvmName("DataSamples")

package combo.math

import combo.util.EMPTY_FLOAT_ARRAY
import combo.util.EMPTY_LONG_ARRAY
import kotlin.jvm.JvmName

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

    fun acceptAll(values: FloatArray, weights: FloatArray? = null) {
        for (i in values.indices) {
            if (weights == null) accept(values[i])
            else accept(values[i], weights[i])
        }
    }

    /**
     * Returns available data points or estimates.
     */
    fun values(): FloatArray

    /**
     * Label names (x-axis) to use for a plot.
     */
    fun labels(): LongArray

    fun copy(): DataSample
    val nbrSamples: Long
}

object VoidSample : DataSample {
    override fun accept(value: Float) {}
    override fun values() = EMPTY_FLOAT_ARRAY
    override fun labels() = EMPTY_LONG_ARRAY
    override val nbrSamples = 0L
    override fun copy() = this
}

/**
 * Stores all data seen.
 */
class FullSample : DataSample {

    private var data = ArrayList<Float>()

    override fun accept(value: Float) {
        data.add(value)
    }

    override val nbrSamples
        get() = data.size.toLong()

    override fun values() = data.toFloatArray()
    override fun labels() = LongArray(data.size) { it.toLong() }

    override fun copy() = FullSample().also {
        it.data = ArrayList(data)
    }
}

/**
 * Keeps a fixed size history where each element grows in size.
 * @param maxSize size of data, must be even.
 */
class BucketSample(maxSize: Int = 10) : DataSample {

    init {
        if (maxSize % 2 != 0) throw IllegalArgumentException("Max size must be even, got $maxSize.")
    }

    private val data = Array(maxSize) { RunningMean() }

    var nbrBuckets = 0
        private set

    var samplesPerBucket = 1
        private set

    override var nbrSamples: Long = 0L
        private set

    override fun accept(value: Float) {
        if (samplesPerBucket.toLong() * data.size.toLong() == nbrSamples) {
            for (i in 0 until data.size step 2)
                data[i / 2] = data[i].combine(data[i + 1])
            nbrBuckets = data.size / 2
            for (i in nbrBuckets until data.size)
                data[i] = RunningMean()
            samplesPerBucket *= 2
        }

        data[nbrBuckets].accept(value)
        nbrSamples++
        if (nbrSamples != 0L && nbrSamples % samplesPerBucket.toLong() == 0L) nbrBuckets++
    }

    override fun values() = FloatArray(nbrBuckets) { data[it].mean }

    override fun labels() = LongArray(nbrBuckets) { samplesPerBucket.toLong() * (it + 1) }

    override fun copy() = BucketSample(data.size).also {
        for (i in 0 until nbrBuckets)
            it.data[i] = data[i].copy()
        it.nbrBuckets = nbrBuckets
        it.samplesPerBucket = samplesPerBucket
        it.nbrSamples = nbrSamples
    }
}
