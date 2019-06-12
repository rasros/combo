package combo.sat.solvers

import org.jacop.constraints.SumBool
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.core.FloatVar
import org.jacop.search.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinaryXeqYTest {

    @Test
    fun entailed1() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store) }
        val y = IntVar(store, -10, 20)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun entailed2() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store, "$it", 0, 0) }
        val y = IntVar(store, 0, 1)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun entailed3() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store, "$it") }
        val y = IntVar(store, -1, 1)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun satisfied1() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store, "$it", 0, 0) }
        val y = IntVar(store, 0, 0)
        val b = BinaryXeqY(xs, y)
        assertTrue(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun satisfied2() {
        val store = Store()
        val xs = Array(3) { BooleanVar(store, "$it", 1, 1) }
        val y = IntVar(store, -1, -1)
        val b = BinaryXeqY(xs, y)
        assertTrue(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun satisfied3() {
        val store = Store()
        val xs = arrayOf(BooleanVar(store, "1", 0, 0), BooleanVar(store, "2", 1, 1), BooleanVar(store, "3", 1, 1))
        val y = IntVar(store, -2, -2)
        val b = BinaryXeqY(xs, y)
        assertTrue(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun satisfied4() {
        val store = Store()
        val xs = arrayOf(BooleanVar(store, "1", 0, 0), BooleanVar(store, "2", 0, 0), BooleanVar(store, "3", 1, 1))
        val y = IntVar(store, -4, -4)
        val b = BinaryXeqY(xs, y)
        assertTrue(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun notSatisfied1() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store, "$it", 0, 0) }
        val y = IntVar(store, 1, 1)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun notSatisfied2() {
        val store = Store()
        val xs = arrayOf(BooleanVar(store, "1", 0, 0), BooleanVar(store, "2", 0, 0), BooleanVar(store, "3", 1, 1))
        val y = IntVar(store, 1, 1)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun search() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store) }
        val y = IntVar(store, -300, 200)

        store.impose(BinaryXeqY(xs, y))
        store.impose(SumBool(xs, "=", IntVar(store, 1, 1)))

        val search = DepthFirstSearch<BooleanVar>().apply {
            setPrintInfo(false)
        }

        val result = search.labeling(store, SimpleSelect(xs, MostConstrainedDynamic(), IndomainMax()), y)
        assertTrue(result)
        assertEquals(1, y.value())
    }
}

class BinaryXeqPTest {

    private fun groundVars(xs: Array<BooleanVar>, p: Float) {
        val x = p.toRawBits()
        for (i in 0 until 32) {
            val v = (x shr i) and 1
            xs[i].domain.setDomain(v, v)
        }
    }

    @Test
    fun entailed1() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        val y = FloatVar(store, -10.0, 20.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun entailed2() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        val y = FloatVar(store, 0.0, 1.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun satisfied() {
        fun test(p: Float) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            groundVars(xs, p)
            val y = FloatVar(store, p.toDouble(), p.toDouble())
            val b = BinaryXeqP(xs, y)
            assertTrue(b.satisfied())
            assertFalse(b.notSatisfied())
        }
        test(0.0f)
        test(-0.0f)
        test(0.15625f)
        test(-1.0f)
        test(1.0f)
        test(12401.01201f)
        test(-82481.92f)
        test(Float.MAX_VALUE)
        test(-Float.MAX_VALUE)
        test(Float.MIN_VALUE)
        test(-Float.MIN_VALUE)
    }

    @Test
    fun notSatisfied1() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        groundVars(xs, 0.0f)
        val y = FloatVar(store, 1.0, 1.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun notSatisfied2() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        groundVars(xs, -0.0f)
        val y = FloatVar(store, 0.0, 0.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun notSatisfied3() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        xs[31].domain.setDomain(1, 1)
        val y = FloatVar(store, 0.0, 0.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun notSatisfied4() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        xs[31].domain.setDomain(0, 0)
        val y = FloatVar(store, -1.0, -0.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun search() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        val p = FloatVar(store, -4.75, 0.0)

        store.impose(BinaryXeqP(xs, p))

        val search = DepthFirstSearch<BooleanVar>().apply {
            setPrintInfo(false)
        }

        val result = search.labeling(store, SimpleSelect(xs, null, IndomainMin()), p)
        assertTrue(result)
        assertEquals(-4.75, p.value())
    }
}
