package combo.ga

import combo.model.ModelTest
import combo.sat.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomInitializerTest {
    @Test
    fun generateSimple() {
        val init = RandomInitializer()
        val l = init.generate(ModelTest.small1.problem, BitFieldLabelingBuilder(), Random.Default)
        assertEquals(ModelTest.small1.problem.nbrVariables, l.size)
    }
}

class LookaheadInitializerTest {
    @Test
    fun generateSimple() {
        val p = Problem(arrayOf(
                Disjunction(intArrayOf(0, 3)),
                Disjunction(intArrayOf(0, 5))
        ), 3)
        val init = LookaheadInitializer(p)
        for (i in 1..10) {
            val l = init.generate(p, BitFieldLabelingBuilder(), Random.Default)
            assertEquals(p.nbrVariables, l.size)
            assertTrue(p.satisfies(l))
        }
    }
}
