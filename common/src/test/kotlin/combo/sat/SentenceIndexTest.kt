package combo.sat

import combo.test.assertContentEquals
import kotlin.test.Test

class SentenceIndexTest {
    @Test
    fun clauseMatch() {
        val index = SentenceIndex(arrayOf(
                Disjunction(intArrayOf(0, 2, 4)),
                Conjunction(intArrayOf(1))), 3)
        assertContentEquals(intArrayOf(0, 1), index.sentencesWith(0))
        assertContentEquals(intArrayOf(0), index.sentencesWith(1))
        assertContentEquals(intArrayOf(0), index.sentencesWith(2))
    }
}
