package combo.model

import kotlin.test.Test
import kotlin.test.assertEquals

class VariableIndexTest {

    @Test
    fun add() {
        val index = VariableIndex()
        val v1 = Flag("a", true)
        index.add(v1)
        assertEquals(1, index.nbrLiterals)
        assertEquals(0, index.indexOf(v1))
        val v2 = Nominal("a", null, 1, 2, 3)
        index.add(v2)
        assertEquals(5, index.nbrLiterals)
        assertEquals(1, index.indexOf(v2))
    }

}
