package combo.math

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class DataSamplesTest {
    @Test
    fun emptyReservoirSample() {
        assertEquals(0, ReservoirSample(10, Random).toArray().size)
    }

    @Test
    fun reservoirSampleFull() {
        val s = ReservoirSample(10, Random)
        for (i in 1..10)
            s.accept(i.toDouble())
        val collect = s.toArray()
        for (i in 0 until 10)
            assertEquals(i.toDouble() + 1.0, collect[i])
    }

    @Test
    fun reservoirSampleUnderSize() {
        val s = ReservoirSample(100, Random)
        for (i in 1..10)
            s.accept(i.toDouble())
        val collect = s.toArray()
        for (i in 0 until 10)
            assertEquals(i.toDouble() + 1.0, collect[i])
        assertEquals(10, collect.size)
    }

    @Test
    fun reservoirSampleOverSize() {
        val s = ReservoirSample(20, Random)
        for (i in 1..100)
            s.accept(i.toDouble())
        val collect = s.toArray()
        assertEquals(20, collect.size)
    }

    @Test
    fun emptyFullSample() {
        assertEquals(0, FullSample().toArray().size)
    }

    @Test
    fun fullSample() {
        val s = FullSample()
        for (i in 1..100)
            s.accept(i.toDouble())
        val collect = s.toArray()
        assertEquals(100, collect.size)
        for (i in 1..100)
            s.accept(i.toDouble())
    }

    @Test
    fun bucketsSampleSizeOne() {
        val s = BucketsSample(1)
        for (i in 1..100)
            s.accept(i.toDouble())
        val data = s.toArray()
        for (i in 1..100)
            assertEquals(i.toDouble(), data[i - 1])
    }

    @Test
    fun bucketsBig() {
        val s = BucketsSample(10)
        for (i in 1..200)
            s.accept(i.toDouble())
        val data = s.toArray()
        assertEquals(20, data.size)
        for (i in 0 until 20)
            assertEquals(5.5 + i * 10.0, data[i])
    }

    @Test
    fun emptyBucketsSample() {
        assertEquals(0, BucketsSample(10).toArray().size)
    }

    @Test
    fun windowSampleFullSize() {
        val s = WindowSample(20)
        for (i in 1..20)
            s.accept(i.toDouble())
        val data = s.toArray()
        assertEquals(20, data.size)
        for (i in 1..20)
            assertEquals(i.toDouble(), data[i - 1])
    }

    @Test
    fun windowSampleReUse() {
        val s = WindowSample(20)
        for (i in 1..94)
            s.accept(i.toDouble())
        s.toArray()
        s.accept(1.0)
        val data = s.toArray()
        assertEquals(1.0, data[data.size - 1])
    }

    @Test
    fun emptyWindowSample() {
        assertEquals(0, WindowSample(10).toArray().size)
    }
}
