package combo.sat

import combo.test.assertContentEquals
import kotlin.random.Random
import kotlin.test.*

class ByteArrayLabelingTest : LabelingTest<ByteArrayLabeling>() {
    override val builder = ByteArrayLabelingBuilder()
}

class IntSetLabelingTest : LabelingTest<IntSetLabeling>() {
    override val builder = IntSetLabelingBuilder()
}

class BitFieldLabelingTest : LabelingTest<BitFieldLabeling>() {
    override val builder = BitFieldLabelingBuilder()
}

abstract class LabelingTest<T : MutableLabeling> {

    abstract val builder: LabelingBuilder<T>

    @Test
    fun indices() {
        assertEquals(0 until 3, builder.build(3).indices)
    }

    @Test
    fun getDefaultFalse() {
        val l = builder.build(10)
        for (i in l.indices) {
            assertFalse(l[i])
        }
    }

    @Test
    fun size() {
        for (i in 1..10) assertEquals(i, builder.build(i).size)
    }

    @Test
    fun get() {
        val l = builder.build(4)
        l[2] = true
        assertFalse(l[0])
        assertFalse(l[1])
        assertTrue(l[2])
        assertFalse(l[3])
    }

    @Test
    fun flip() {
        val l = builder.build(5)
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
        val l = builder.build(10)
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
        val l = builder.build(3).apply { setAll(intArrayOf(0, 2, 5)) }
        assertTrue(l[0])
        assertTrue(l[1])
        assertFalse(l[2])
    }

    @Test
    fun copy() {
        val l = builder.build(3).apply { setAll(intArrayOf(0, 2, 5)) }
        assertEquals(l, l.copy())
    }

    @Test
    fun copyRandom() {
        val r = Random(1023)
        for (i in 1..50) {
            val l = builder.generate(1 + r.nextInt(1000), r)
            assertContentEquals(l.toIntArray(), l.copy().toIntArray())
        }
    }

    @Test
    fun empty() {
        val l = builder.build(0)
        assertEquals(0, l.size)
    }

    @Test
    fun emptyRandom() {
        val l = builder.generate(0, Random.Default)
        assertEquals(0, l.size)
    }

    @Test
    fun randomDifferent() {
        val rand1 = builder.generate(50, Random(0))
        val rand2 = builder.generate(50, Random(0))
        val rand3 = builder.generate(50, Random(1))
        for (i in 0 until 50)
            assertEquals(rand1[i], rand2[i])
        for (i in 0 until 50)
            if (rand1[i] != rand3[i])
                return
        fail("Same")
    }

    @Test
    fun equalsSize() {
        val l1 = builder.build(10)
        val l2 = builder.build(11)
        val l3 = builder.build(10)
        assertNotEquals(l1, l2)
        assertEquals(l1, l3)
    }

    @Test
    fun hashCodeSize() {
        val l1 = builder.build(10)
        val l2 = builder.build(11)
        val l3 = builder.build(10)
        assertNotEquals(l1.hashCode(), l2.hashCode())
        assertEquals(l1.hashCode(), l3.hashCode())
    }

    @Test
    fun equalsSet() {
        val l1 = builder.build(20)
        val l2 = builder.build(20)
        assertEquals(l1, l2)
        l1[10] = true
        assertFalse(l1 == l2)
    }

    @Test
    fun hashCodeSet() {
        val l1 = builder.build(20)
        val l2 = builder.build(20)
        assertEquals(l1.hashCode(), l2.hashCode())
        l1[10] = true
        assertNotEquals(l1.hashCode(), l2.hashCode())
    }

    @Test
    fun randomHashEquals() {
        // Tests equals/hashCode implementations
        val r = Random.Default
        val s = generateSequence {
            builder.generate(4, r)
        }.take(100).toHashSet()
        assertTrue(s.size <= 16)
    }

    @Test
    fun builderSetAll() {
        val l = builder.build(100, intArrayOf(4, 16, 100, 120, 122))
        assertTrue(l[4.asIx()])
        assertTrue(l[16.asIx()])
        assertTrue(l[100.asIx()])
        assertTrue(l[120.asIx()])
        assertTrue(l[122.asIx()])
        assertFalse(l[0])
        assertFalse(l[70])

        l.flip(4.asIx())
        assertFalse(l[4.asIx()])
    }

    @Test
    fun iterator() {
        val l = builder.build(10, (0 until 20 step 2).toList().toIntArray())
        val allValues = l.iterator().asSequence().toSet()
        assertEquals(10, allValues.size)
        for (lit in l) {
            assertTrue(lit.asBoolean())
        }
    }

    @Test
    fun truthIterator() {
        val l = builder.build(10, (0 until 20 step 4).toList().toIntArray())
        val allValues = l.truthIterator().asSequence().toList()
        assertEquals(5, allValues.size)
        for (lit in l.truthIterator()) {
            assertTrue(lit.asBoolean())
        }
    }

    @Test
    fun flipLarge() {
        val l = builder.build(101)
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
            val l = builder.build(1 + r.nextInt(1001))
            for (j in 1..100) {
                val id = r.nextInt(l.size)
                val b1 = l[id]
                l.flip(id)
                assertNotEquals(b1, l[id])
            }
        }
    }
}

