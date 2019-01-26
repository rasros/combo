package combo.sat

import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelingPermutationTest {

    @Test
    fun emptySequence() {
        assertEquals(1, LabelingPermutation(0, BitFieldLabelingFactory, Random).asSequence().count())
    }

    @Test
    fun sequenceSize() {
        assertEquals(2.0.pow(3).toInt(), LabelingPermutation(3, BitFieldLabelingFactory, Random).asSequence().count())
        assertEquals(2.0.pow(4).toInt(), LabelingPermutation(4, BitFieldLabelingFactory, Random).asSequence().count())
    }

    @Test
    fun noRepetition() {
        val list = LabelingPermutation(4, BitFieldLabelingFactory, Random).asSequence().toList()
        val set = LabelingPermutation(4, BitFieldLabelingFactory, Random).asSequence().toSet()
        assertEquals(set.size, list.size)
    }

    @Test
    fun takeMany() {
        assertEquals(16, LabelingPermutation(4, BitFieldLabelingFactory, Random).asSequence().take(1000).toList().size)
    }
}
