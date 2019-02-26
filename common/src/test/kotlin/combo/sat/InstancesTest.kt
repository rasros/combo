package combo.sat

import combo.test.assertContentEquals
import combo.util.IntList
import kotlin.random.Random
import kotlin.test.*

abstract class InstanceTest {

    abstract val factory: InstanceFactory

    @Test
    fun indices() {
        assertEquals(0 until 3, factory.create(3).indices)
    }

    @Test
    fun getDefaultFalse() {
        val instance = factory.create(10)
        for (i in instance.indices) {
            assertFalse(instance[i])
        }
    }

    @Test
    fun size() {
        for (i in 1..10) assertEquals(i, factory.create(i).size)
    }

    @Test
    fun setOne() {
        val instance = factory.create(4)
        instance[2] = true
        assertFalse(instance[0])
        assertFalse(instance[1])
        assertTrue(instance[2])
        assertFalse(instance[3])
    }

    @Test
    fun flip() {
        val instance = factory.create(5)
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
        val instance = factory.create(10)
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
        val instance = factory.create(3).apply { setAll(intArrayOf(1, 2, -3)) }
        assertTrue(instance[0])
        assertTrue(instance[1])
        assertFalse(instance[2])
    }

    @Test
    fun copyEquals() {
        val instance = factory.create(3).apply { setAll(intArrayOf(1, 2, -3)) }
        assertEquals(instance, instance.copy())
        assertEquals(instance.copy(), instance)
    }

    @Test
    fun copy() {
        val instance = factory.create(3).apply { setAll(intArrayOf(1, 2, -3)) }
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
            val instance = factory.create(1 + r.nextInt(1000))
            for (k in 0 until instance.size) instance[k] = r.nextBoolean()
            assertContentEquals(instance.toIntArray(), instance.copy().toIntArray())
        }
    }

    @Test
    fun empty() {
        val instance = factory.create(0)
        assertEquals(0, instance.size)
    }

    @Test
    fun equalsTransitive() {
        val instance1 = factory.create(10)
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
        val instance1 = factory.create(10)
        val instance2 = factory.create(11)
        val instance3 = factory.create(10)
        assertNotEquals(instance1, instance2)
        assertEquals(instance1, instance3)
    }

    @Test
    fun hashCodeSize() {
        val instance1 = factory.create(10)
        val instance2 = factory.create(11)
        val instance3 = factory.create(10)
        assertNotEquals(instance1.hashCode(), instance2.hashCode())
        assertEquals(instance1.hashCode(), instance3.hashCode())
    }

    @Test
    fun equalsSet() {
        val instance1 = factory.create(20)
        val instance2 = factory.create(20)
        assertEquals(instance1, instance2)
        instance1[10] = true
        assertNotEquals(instance1, instance2)
    }

    @Test
    fun hashCodeSetDiffers() {
        val instance1 = factory.create(20)
        val instance2 = factory.create(20)
        assertEquals(instance1.hashCode(), instance2.hashCode())
        instance1[10] = true
        assertNotEquals(instance1.hashCode(), instance2.hashCode())
    }

    @Test
    fun randomHashEquals() {
        // Tests equals/hashCode implementations
        val s = generateSequence {
            factory.create(4).apply {
                for (k in 0 until 4) this[k] = Random.nextBoolean()
            }
        }.take(100).toHashSet()
        assertTrue(s.size <= 16)
    }

    @Test
    fun emptyIterator() {
        val instance = factory.create(10)
        assertEquals(0, instance.iterator().asSequence().count())
    }

    @Test
    fun iterator() {
        val instance = factory.create(10).apply { setAll(3..7) }
        var k = 2
        val itr = instance.iterator()
        for (i in itr) {
            assertEquals(k++, i)
        }
        assertFalse(itr.hasNext())
    }

    @Test
    fun iteratorLarge() {
        val instance = factory.create(100)
        val list = IntList()
        for (i in instance.indices step 5) {
            list.add(i)
            instance[i] = true
        }
        val ints = instance.iterator().asSequence().toList().toIntArray().apply { sort() }
        assertContentEquals(list.toArray(), ints)
    }

    @Test
    fun flipLarge() {
        val instance = factory.create(101)
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
            val instance = factory.create(1 + r.nextInt(1001))
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
        val instance = factory.create(100)
        instance.setBits(10, 32, Int.MIN_VALUE)
        assertEquals(Int.MIN_VALUE, instance.getBits(10, 32))

        instance.setBits(42, 32, -1)
        assertEquals(-1, instance.getBits(42, 32))

        instance.setBits(20, 32, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, instance.getBits(20, 32))
    }

    @Test
    fun bitsSetGetBoundsByte() {
        val instance = factory.create(100)
        instance.setBits(10, 8, Byte.MIN_VALUE.toInt())
        assertEquals(Byte.MIN_VALUE, instance.getBits(10, 8).toByte())

        instance.setBits(40, 8, Byte.MAX_VALUE.toInt())
        assertEquals(Byte.MAX_VALUE, instance.getBits(40, 8).toByte())
    }

    @Test
    fun bitsSetGetArbitrary() {
        val instance = factory.create(10)
        instance.setBits(0, 5, 1)
        assertEquals(1, instance.getBits(0, 5))

        instance.setBits(5, 10, 32)
        assertEquals(32, instance.getBits(5, 8))
    }
}

class SparseBitArrayTest : InstanceTest() {
    override val factory = SparseBitArrayFactory
}

class BitArrayTest : InstanceTest() {
    override val factory = BitArrayFactory
}

