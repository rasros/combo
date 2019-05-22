package combo.sat

import combo.test.assertContentEquals
import combo.util.key
import combo.util.value
import kotlin.random.Random
import kotlin.test.*

abstract class InstanceTest {

    abstract val builder: InstanceBuilder

    @Test
    fun indices() {
        assertEquals(0 until 3, builder.create(3).indices)
    }

    @Test
    fun getDefaultFalse() {
        val instance = builder.create(10)
        for (i in instance.indices) {
            assertFalse(instance[i])
        }
    }

    @Test
    fun size() {
        for (i in 1..10) assertEquals(i, builder.create(i).size)
    }

    @Test
    fun setOne() {
        val instance = builder.create(4)
        instance[2] = true
        assertFalse(instance[0])
        assertFalse(instance[1])
        assertTrue(instance[2])
        assertFalse(instance[3])
    }

    @Test
    fun flip() {
        val instance = builder.create(5)
        for (i in instance.indices) assertFalse(instance[i])
        instance.flip(1)
        assertFalse(instance[0])
        assertTrue(instance[1])
        assertFalse(instance[2])
        assertFalse(instance[3])
        assertFalse(instance[4])
        instance.flip(1)
        for (i in instance.indices) assertFalse(instance[i])
    }

    @Test
    fun setMany() {
        val instance = builder.create(10)
        assertFalse(instance[4])
        instance[4] = true
        assertTrue(instance[4])
        instance[4] = true
        assertTrue(instance[4])
        instance[4] = false
        assertFalse(instance[4])
        instance[5] = false
        assertFalse(instance[5])
    }

    @Test
    fun setAll() {
        val instance = builder.create(3).apply { setAll(intArrayOf(1, 2, -3)) }
        assertTrue(instance[0])
        assertTrue(instance[1])
        assertFalse(instance[2])
    }

    @Test
    fun copyEquals() {
        val instance = builder.create(3).apply { setAll(intArrayOf(1, 2, -3)) }
        assertEquals(instance, instance.copy())
        assertEquals(instance.copy(), instance)
    }

    @Test
    fun copy() {
        val instance = builder.create(3).apply { setAll(intArrayOf(1, 2, -3)) }
        val instance2 = instance.copy()
        instance2[0] = false
        assertNotEquals(instance, instance2)
        assertTrue(instance[0])
        assertFalse(instance2[0])
    }

    @Test
    fun copyRandom() {
        val r = Random(1023)
        for (i in 1..50) {
            val instance = builder.create(1 + r.nextInt(1000))
            for (k in 0 until instance.size) instance[k] = r.nextBoolean()
            assertContentEquals(instance.toIntArray(), instance.copy().toIntArray())
        }
    }

    @Test
    fun empty() {
        val instance = builder.create(0)
        assertEquals(0, instance.size)
    }

    @Test
    fun equalsTransitive() {
        val instance1 = builder.create(10)
        for (k in 0 until instance1.size) instance1[k] = Random.nextBoolean()
        val instance2 = instance1.copy()
        assertEquals(instance1, instance2)
        assertEquals(instance2, instance1)
        instance2[0] = !instance2[0]
        assertNotEquals(instance1, instance2)
        assertNotEquals(instance2, instance1)
    }

    @Test
    fun equalsSize() {
        val instance1 = builder.create(10)
        val instance2 = builder.create(11)
        val instance3 = builder.create(10)
        assertNotEquals(instance1, instance2)
        assertEquals(instance1, instance3)
    }

    @Test
    fun hashCodeSize() {
        val instance1 = builder.create(10)
        val instance2 = builder.create(11)
        val instance3 = builder.create(10)
        assertNotEquals(instance1.hashCode(), instance2.hashCode())
        assertEquals(instance1.hashCode(), instance3.hashCode())
    }

    @Test
    fun equalsSet() {
        val instance1 = builder.create(20)
        val instance2 = builder.create(20)
        assertEquals(instance1, instance2)
        instance1[10] = true
        assertNotEquals(instance1, instance2)
    }

    @Test
    fun hashCodeSetDiffers() {
        val instance1 = builder.create(20)
        val instance2 = builder.create(20)
        assertEquals(instance1.hashCode(), instance2.hashCode())
        instance1[10] = true
        assertNotEquals(instance1.hashCode(), instance2.hashCode())
    }

    @Test
    fun randomHashEquals() {
        // Tests equals/hashCode implementations
        val s = generateSequence {
            builder.create(4).apply {
                for (k in 0 until 4) this[k] = Random.nextBoolean()
            }
        }.take(100).toHashSet()
        assertTrue(s.size <= 16)
    }

    @Test
    fun emptyIterator() {
        val instance = builder.create(10)
        assertEquals(0, instance.iterator().asSequence().count())
    }

    @Test
    fun iterator() {
        val instance = builder.create(10)
        instance[2] = true
        instance[9] = true
        val result = instance.iterator().asSequence().toList().toIntArray().apply { sort() }
        assertContentEquals(intArrayOf(2, 9), result)
    }

    @Test
    fun iteratorLarge() {
        val instance = builder.create(100)
        for (i in instance.indices step 5)
            instance[i] = true
        val result = instance.iterator().asSequence().toList().toIntArray().apply { sort() }
        assertContentEquals((0..95 step 5).toList().toIntArray(), result)
    }

    @Test
    fun iteratorLargeHoled() {
        val instance = builder.create(200)
        instance[195] = true
        instance[85] = true
        val result = instance.iterator().asSequence().toList().toIntArray().apply { sort() }
        assertContentEquals(intArrayOf(85, 195), result)
    }

    @Test
    fun iteratorLastElement() {
        val instance = builder.create(99)
        instance[31] = true
        instance[98] = true
        val result = instance.iterator().asSequence().toList().toIntArray().apply { sort() }
        assertContentEquals(intArrayOf(31, 98), result)
    }

    @Test
    fun iteratorAllOnes() {
        val instance = builder.create(227)
        for (i in instance.indices) instance[i] = true
        val result = instance.iterator().asSequence().toList().toIntArray().apply { sort() }
        assertContentEquals((0 until 227).toList().toIntArray(), result)
    }

    @Test
    fun flipLarge() {
        val instance = builder.create(101)
        for (i in instance.indices) assertFalse(instance[i])
        for (i in instance.indices step 3) instance.flip(i)
        for (i in instance.indices) if (i.rem(3) == 0) assertTrue(instance[i]) else assertFalse(instance[i])
        for (i in instance.indices step 3) instance.flip(i)
        for (i in instance.indices) assertFalse(instance[i])
    }

    @Test
    fun flipRandom() {
        for (i in 1..10) {
            val r = Random.Default
            val instance = builder.create(1 + r.nextInt(1001))
            for (j in 1..100) {
                val id = r.nextInt(instance.size)
                val b1 = instance[id]
                instance.flip(id)
                assertNotEquals(b1, instance[id])
            }
        }
    }

    @Test
    fun bitsSetGetBounds() {
        val instance = builder.create(100)
        instance.setBits(50, 32, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, instance.getBits(50, 32))

        instance.setBits(42, 32, -1)
        assertEquals(-1, instance.getBits(42, 32))

        instance.setBits(20, 32, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, instance.getBits(20, 32))
    }

    @Test
    fun bitsSetGetBoundsByte() {
        val instance = builder.create(100)
        instance.setBits(10, 8, 0b10000000)
        assertEquals(Byte.MIN_VALUE, instance.getBits(10, 8).toByte())

        instance.setBits(40, 8, Byte.MAX_VALUE.toInt())
        assertEquals(Byte.MAX_VALUE, instance.getBits(40, 8).toByte())
    }

    @Test
    fun bitsSetGetArbitrary() {
        val instance = builder.create(13)
        instance.setBits(0, 5, 1)
        assertEquals(1, instance.getBits(0, 5))

        instance.setBits(5, 8, 255)
        assertEquals(255, instance.getBits(5, 8))
    }

    @Test
    fun setBitsTmp() {
        val instance = builder.create(64)
        instance.setBits(30, 4, 5)
    }

    @Test
    fun bitsSetGetManyInts() {
        val instance = builder.create(200)
        for (i in 0..168) {
            val value = Random.nextInt()
            instance.setBits(i, 32, value)
            assertEquals(value, instance.getBits(i, 32))
        }
    }

    @Test
    fun bitsSetGetManyShorts() {
        val instance = builder.create(200)
        val rng = Random(0)
        for (i in 0..184) {
            for (j in instance.indices) instance[j] = false
            val value = rng.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            instance.setSignedInt(i, 16, value)
            assertEquals(value, instance.getSignedInt(i, 16), "$i")
        }
    }

    @Test
    fun getSetFloat() {
        val instance = builder.create(32)
        for (i in 1..10) {
            val rnd = Float.fromBits(Random.nextBits(32))
            instance.setFloat(0, rnd)
            assertEquals(rnd, instance.getFloat(0))
        }
    }

    @Test
    fun bitsIsolated() {
        val instance = builder.create(65)
        for (i in instance.indices) instance[i] = true
        instance.setBits(63, 2, 1)
        for (i in 0 until 63) assertTrue(instance[i])
        assertEquals(1, instance.getBits(63, 2))

        instance.setBits(50, 15, 32767)
        for (i in 0 until 63) assertTrue(instance[i])
        instance.setBits(50, 15, 0)
        for (i in 0 until 50) assertTrue(instance[i])
        for (i in 50 until 65) assertFalse(instance[i])
    }

    @Test
    fun bitsIsolatedEven() {
        val instance = builder.create(96)
        instance.setBits(0, 32, -1)
        instance.setBits(32, 32, 0)
        assertEquals(-1, instance.getBits(0, 32))
        instance.setBits(64, 32, -1)
        assertEquals(0, instance.getBits(32, 32))
    }

    @Test
    fun bitsIsolatedUneven() {
        val instance = builder.create(118)
        instance.setBits(54, 32, -1)
        instance.setBits(22, 32, 0)
        instance.setBits(86, 32, 0)
        assertEquals(-1, instance.getBits(54, 32))
        instance.setBits(54, 32, -1)
        assertEquals(0, instance.getBits(22, 32))
        assertEquals(0, instance.getBits(86, 32))

        instance.setBits(54, 32, 0)
        instance.setBits(22, 32, -1)
        instance.setBits(86, 32, -1)
        assertEquals(0, instance.getBits(54, 32))
        assertEquals(-1, instance.getBits(22, 32))
        assertEquals(-1, instance.getBits(86, 32))
    }

    @Test
    fun getSetSignedBits10() {
        val instance = builder.create(10)

        instance.setSignedInt(0, 10, 8)
        assertEquals(8, instance.getSignedInt(0, 10))

        instance.setSignedInt(0, 10, -2)
        assertEquals(-2, instance.getSignedInt(0, 10))
    }

    @Test
    fun getSetSignedBits32() {
        val instance = builder.create(32)

        instance.setSignedInt(0, 32, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, instance.getSignedInt(0, 32))

        instance.setSignedInt(0, 32, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, instance.getSignedInt(0, 32))

        instance.setSignedInt(0, 32, -1)
        assertEquals(-1, instance.getSignedInt(0, 32))
    }

    @Test
    fun wordSize() {
        assertEquals(2, builder.create(60).wordSize)
        assertEquals(1, builder.create(32).wordSize)
        assertEquals(0, builder.create(0).wordSize)
        assertEquals(2, builder.create(64).wordSize)
        assertEquals(3, builder.create(65).wordSize)
    }

    @Test
    fun wordIterator() {
        val instance1 = builder.create(100)
        instance1[10] = true
        instance1[50] = true
        val list = instance1.wordIterator().asSequence().toList().sortedBy { it.key() }
        assertEquals(1024 /*2^10*/, list[0].value())
        assertEquals(262144 /*2^18=2^(50-32)*/, list[1].value())
        assertEquals(0, list[0].key())
        assertEquals(1, list[1].key())
    }

    @Test
    fun orInstance() {
        val inst1 = builder.create(60)
        val inst2 = builder.create(60)
        inst1[10] = true
        inst1[30] = true
        inst2[20] = true
        val inst3 = inst1.copy().apply { or(inst2) }
        val inst4 = inst2.copy().apply { or(inst1) }
        assertEquals(inst3, inst4)

        assertTrue(inst3[10])
        assertTrue(inst3[20])
        assertTrue(inst3[30])

        assertTrue(inst4[10])
        assertTrue(inst4[20])
        assertTrue(inst4[30])
        assertEquals(3, inst4.iterator().asSequence().count())
    }

    @Test
    fun andInstance() {
        val inst1 = builder.create(60)
        val inst2 = builder.create(60)
        inst1[10] = true
        inst1[30] = true
        inst1[40] = true
        inst1[60] = true
        inst2[10] = true
        inst2[20] = true
        inst2[30] = true
        val inst3 = inst1.copy().apply { and(inst2) }
        val inst4 = inst2.copy().apply { and(inst1) }

        assertEquals(inst3, inst4)

        assertTrue(inst3[10])
        assertFalse(inst3[20])
        assertTrue(inst3[30])
        assertFalse(inst3[40])
        assertFalse(inst3[60])

        assertEquals(2, inst4.iterator().asSequence().count())
    }
}

class SparseBitArrayTest : InstanceTest() {
    override val builder = SparseBitArrayBuilder
}

class BitArrayTest : InstanceTest() {
    override val builder = BitArrayBuilder
}

