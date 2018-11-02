package combo.ga

import combo.math.Rng
import combo.model.ModelTest
import combo.sat.BitFieldLabelingBuilder
import combo.sat.Disjunction
import combo.sat.Problem
import combo.sat.smallSat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomInitializerTest {
    @Test
    fun generateSimple() {
        val init = RandomInitializer()
        val l = init.generate(ModelTest.m1.problem, BitFieldLabelingBuilder(), Rng(0))
        assertEquals(smallSat.nbrVariables, l.size)
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
            val l = init.generate(p, BitFieldLabelingBuilder(), Rng())
            assertEquals(p.nbrVariables, l.size)
            assertTrue(p.satisfies(l))
        }
    }
}
