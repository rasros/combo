package combo.sat

import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class InstancePermutationTest {

    @Test
    fun emptySequence() {
        assertEquals(1, InstancePermutation(0, BitFieldInstanceFactory, Random).asSequence().count())
    }

    @Test
    fun sequenceSize() {
        assertEquals(2.0.pow(3).toInt(), InstancePermutation(3, BitFieldInstanceFactory, Random).asSequence().count())
        assertEquals(2.0.pow(4).toInt(), InstancePermutation(4, BitFieldInstanceFactory, Random).asSequence().count())
    }

    @Test
    fun noRepetition() {
        val list = InstancePermutation(4, BitFieldInstanceFactory, Random).asSequence().toList()
        val set = InstancePermutation(4, BitFieldInstanceFactory, Random).asSequence().toSet()
        assertEquals(set.size, list.size)
    }

    @Test
    fun takeMany() {
        assertEquals(16, InstancePermutation(4, BitFieldInstanceFactory, Random).asSequence().take(1000).toList().size)
    }
}
