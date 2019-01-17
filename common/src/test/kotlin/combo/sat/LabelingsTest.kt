package combo.sat

import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.*

abstract class LabelingTest {

    abstract val factory: LabelingFactory

    @Test
    fun indices() {
        assertEquals(0 until 3, factory.create(3).indices)
    }

    @Test
    fun getDefaultFalse() {
        val l = factory.create(10)
        for (i in l.indices) {
            assertFalse(l[i])
        }
    }

    @Test
    fun size() {
        for (i in 1..10) assertEquals(i, factory.create(i).size)
    }

    @Test
    fun get() {
        val l = factory.create(4)
        l[2] = true
        assertFalse(l[0])
        assertFalse(l[1])
        assertTrue(l[2])
        assertFalse(l[3])
    }

    @Test
    fun flip() {
        val l = factory.create(5)
        for (i in l.indices) assertFalse(l[i])
        l.flip(1)
        assertFalse(l[0])
        assertTrue(l[1])
        assertFalse(l[2])
        assertFalse(l[3])
        assertFalse(l[4])
        l.flip(1)
        for (i in l.indices) assertFalse(l[i])
    }

    @Test
    fun set() {
        val l = factory.create(10)
        assertFalse(l[4])
        l[4] = true
        assertTrue(l[4])
        l[4] = true
        assertTrue(l[4])
        l[4] = false
        assertFalse(l[4])
        l[5] = false
        assertFalse(l[5])
    }

    @Test
    fun setAll() {
        val l = factory.create(3).apply { setAll(intArrayOf(0, 2, 5)) }
        assertTrue(l[0])
        assertTrue(l[1])
        assertFalse(l[2])
    }

    @Test
    fun copyEquals() {
        val l = factory.create(3).apply { setAll(intArrayOf(0, 2, 5)) }
        assertEquals(l, l.copy())
        assertEquals(l.copy(), l)
    }

    @Test
    fun copy() {
        val l = factory.create(3).apply { setAll(intArrayOf(0, 2, 5)) }
        val l2 = l.copy()
        l2[0] = false
        assertNotEquals(l, l2)
        assertTrue(l[0])
        assertFalse(l2[0])
    }

    @Test
    fun copyRandom() {
        val r = Random(1023)
        for (i in 1..50) {
            val l = factory.create(1 + r.nextInt(1000))
            for (k in 0 until l.size) l[k] = r.nextBoolean()
            assertContentEquals(l.toIntArray(), l.copy().toIntArray())
        }
    }

    @Test
    fun empty() {
        val l = factory.create(0)
        assertEquals(0, l.size)
    }

    @Test
    fun equalsTransitive() {
        val l1 = factory.create(10)
        for (k in 0 until l1.size) l1[k] = Random.nextBoolean()
        val l2 = l1.copy()
        assertEquals(l1, l2)
        assertEquals(l2, l1)
        l2[0] = !l2[0]
        assertNotEquals(l1, l2)
        assertNotEquals(l2, l1)
    }

    @Test
    fun equalsSize() {
        val l1 = factory.create(10)
        val l2 = factory.create(11)
        val l3 = factory.create(10)
        assertNotEquals(l1, l2)
        assertEquals(l1, l3)
    }

    @Test
    fun hashCodeSize() {
        val l1 = factory.create(10)
        val l2 = factory.create(11)
        val l3 = factory.create(10)
        assertNotEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1.hashCode(), l3.hashCode())
    }

    @Test
    fun equalsSet() {
        val l1 = factory.create(20)
        val l2 = factory.create(20)
        assertEquals(l1, l2)
        l1[10] = true
        assertNotEquals(l1, l2)
    }

    @Test
    fun hashCodeSet() {
        val l1 = factory.create(20)
        val l2 = factory.create(20)
        assertEquals(l1.hashCode(), l2.hashCode())
        l1[10] = true
        assertNotEquals(l1.hashCode(), l2.hashCode())
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
    fun iterator() {
        val l = factory.create(10).apply { setAll((0 until 20 step 2).toList().toIntArray()) }
        val allValues = l.iterator().asSequence().toSet()
        assertEquals(10, allValues.size)
        for (lit in l) {
            assertTrue(lit.toBoolean())
        }
    }

    @Test
    fun truthIterator() {
        val l = factory.create(10).apply { setAll((0 until 20 step 4).toList().toIntArray()) }
        val allValues = l.truthIterator().asSequence().toList()
        assertEquals(5, allValues.size)
        for (lit in l.truthIterator()) {
            assertTrue(lit.toBoolean())
        }
    }

    @Test
    fun flipLarge() {
        val l = factory.create(101)
        for (i in l.indices) assertFalse(l[i])
        for (i in l.indices step 3) l.flip(i)
        for (i in l.indices) if (i.rem(3) == 0) assertTrue(l[i]) else assertFalse(l[i])
        for (i in l.indices step 3) l.flip(i)
        for (i in l.indices) assertFalse(l[i])
    }

    @Test
    fun flipRandom() {
        for (i in 1..10) {
            val r = Random.Default
            val l = factory.create(1 + r.nextInt(1001))
            for (j in 1..100) {
                val id = r.nextInt(l.size)
                val b1 = l[id]
                l.flip(id)
                assertNotEquals(b1, l[id])
            }
        }
    }
}

class ByteArrayLabelingTest : LabelingTest() {
    override val factory = ByteArrayLabelingFactory
}

class IntSetLabelingTest : LabelingTest() {
    override val factory = IntSetLabelingFactory
}

class BitFieldLabelingTest : LabelingTest() {
    override val factory = BitFieldLabelingFactory
}

