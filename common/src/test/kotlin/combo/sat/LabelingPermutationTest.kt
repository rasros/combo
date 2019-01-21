package combo.sat

import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelingPermutationTest {

    @Test
    fun emptySequence() {
        assertEquals(1, LabelingPermutation.sequence(0, Random).count())
    }

    @Test
    fun sequenceSize() {
        assertEquals(2.0.pow(3).toInt(), LabelingPermutation.sequence(3, BitFieldLabelingFactory, Random.Default).count())
        assertEquals(2.0.pow(4).toInt(), LabelingPermutation.sequence(4, BitFieldLabelingFactory, Random.Default).count())
    }

    @Test
    fun noRepetition() {
        val list = LabelingPermutation.sequence(4, BitFieldLabelingFactory, Random.Default).toList()
        val set = LabelingPermutation.sequence(4, BitFieldLabelingFactory, Random.Default).toSet()
        assertEquals(set.size, list.size)
    }

    @Test
    fun takeMany() {
        assertEquals(16, LabelingPermutation.sequence(4, Random).take(1000).toList().size)
    }
}
