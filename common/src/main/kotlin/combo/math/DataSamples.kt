@file:JvmName("DataSamples")

package combo.math

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.math.round
import kotlin.random.Random

fun <S : DataSample> Sequence<Number>.sample(s: S): S {
    forEach { s.accept(it.toDouble()) }
    return s
}

class Percentile(data: DoubleArray) {
    val data: DoubleArray = data.sortedArray()
    fun percentile(p: Double) = data[round((data.size - 1) * p).toInt()]
    fun quartile(i: Int) = percentile(0.25 * i)
    val median get() = percentile(0.5)
}

/**
 * Samples a stream of numbers.
 */
interface DataSample {
    fun accept(value: Double)

    fun acceptAll(values: DoubleArray) {
        for (value in values)
            accept(value)
    }

    /**
     * Returns all data points sampled.
     */
    fun collect(): DoubleArray

    val nbrSamples: Long
}

/**
 * Contains the latest numbers in the stream.
 */
class WindowSample(size: Int) : DataSample {
    private val window = DoubleArray(size)
    private var pointer: Int = 0

    override var nbrSamples: Long = 0L
        private set

    override fun accept(value: Double) {
        nbrSamples++
        window[pointer] = value
        pointer = (++pointer % window.size)
    }

    override fun collect(): DoubleArray {
        return if (nbrSamples < window.size) window.sliceArray(0 until nbrSamples.toInt())
        else with(window) { sliceArray(pointer until size) + sliceArray(0 until pointer) }
    }

    val size = window.size
}

/**
 * Stores all data seen.
 */
class FullSample : DataSample {
    private var data = ArrayList<Double>()
    var size: Int = 0
        private set

    override fun accept(value: Double) {
        data.add(value)
        size++
    }

    override val nbrSamples
        get() = size.toLong()

    override fun collect() = data.toDoubleArray()
}

/**
 * Aggregates data into buckets, where each bucket has a fixed size.
 */
class BucketsSample(val samplesPerBucket: Int) : DataSample {
    private var history = ArrayList<VarianceStatistic>(samplesPerBucket)
    override var nbrSamples = 0L
        private set

    init {
        require(samplesPerBucket > 0)
        history.add(RunningVariance())
    }

    override fun accept(value: Double) {
        history[size - 1].accept(value)
        if (history[size - 1].nbrSamples >= samplesPerBucket) {
            history.add(RunningVariance())
        }
        nbrSamples++
    }

    override fun collect(): DoubleArray {
        val result = ArrayList<Double>()
        history.asSequence()
                .filter { it.nbrSamples.toInt() == samplesPerBucket }
                .forEach { result.add(it.mean) }
        return result.toDoubleArray()
    }

    val size get() = history.size
}

/**
 * Keeps a fixed size cache of seen data. Each new data point has a random chance of replacing an old data.
 */
class ReservoirSample(size: Int, private val rng: Random) : DataSample {
    private val data = DoubleArray(size)
    override var nbrSamples = 0L
        private set

    override fun accept(value: Double) {
        if (nbrSamples < data.size) {
            data[nbrSamples++.toInt()] = value
        } else {
            val pf = rng.nextDouble() * nbrSamples++
            val p = round(pf).toInt()
            if (p < data.size) {
                data[p] = value
            }
        }
    }

    override fun collect(): DoubleArray {
        return if (nbrSamples < size) data.sliceArray(0 until nbrSamples.toInt())
        else data
    }

    val size = data.size
}

/**
 * Keeps a fixed size history where each element grows in size.
 * @param maxSize size of data, must be even.
 */
class GrowingDataSample @JvmOverloads constructor(maxSize: Int) : DataSample {

    init {
        if (maxSize % 2 != 0) throw IllegalArgumentException("Max size must be even, got $maxSize.")
    }

    private val data = Array(maxSize) { RunningVariance() }
    var size = 0
        private set

    var samplesPerBucket = 1
        private set

    override var nbrSamples: Long = 0L
        private set

    override fun accept(value: Double) {
        if (samplesPerBucket.toLong() * data.size.toLong() == nbrSamples) {
            for (i in 0 until data.size step 2)
                data[i / 2] = data[i].combine(data[i + 1])
            size = data.size / 2
            for (i in size until data.size)
                data[i] = RunningVariance()
            samplesPerBucket *= 2
        }

        data[size].accept(value)
        nbrSamples++
        if (nbrSamples != 0L && nbrSamples % samplesPerBucket.toLong() == 0L) size++
    }

    override fun collect() = DoubleArray(size) { data[it].mean }
    fun slots() = IntArray(size) { samplesPerBucket * (it + 1) }
}
