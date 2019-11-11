package combo.sat.optimizers

import combo.sat.toBoolean
import combo.sat.toIx
import org.jacop.constraints.*
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.constraints.LinearFloat
import org.jacop.floats.constraints.SumFloat
import org.jacop.floats.core.FloatVar
import org.jacop.search.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinaryXeqYTest {

    private fun groundVars(xs: Array<BooleanVar>, x: Int) {
        for (i in 0 until 32) {
            val v = (x shr i) and 1
            xs[i].domain.setDomain(v, v)
        }
    }

    @Test
    fun entailedUncertain1() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store) }
        val y = IntVar(store, -10, 20)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun entailedUncertain2() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store, "$it", 0, 0) }
        val y = IntVar(store, 0, 1)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun entailedUncertain3() {
        val store = Store()
        val xs = Array(10) { BooleanVar(store, "$it") }
        val y = IntVar(store, -1, 1)
        val b = BinaryXeqY(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun groundedSatisfiedFullSize() {
        fun test(x: Int) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            groundVars(xs, x)
            val y = IntVar(store, x, x)
            val b = BinaryXeqY(xs, y)
            assertTrue(b.satisfied())
            assertFalse(b.notSatisfied())
        }
        test(0)
        test(15)
        test(-1)
        test(1)
        test(12401)
        test(82481)
        test(Int.MAX_VALUE)
        test(Int.MIN_VALUE)
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
    fun searchOptimize() {
        fun search(min: Int, max: Int) {
            val store = Store()
            val xs = Array(10) { BooleanVar(store) }
            val y = IntVar(store, min, max)

            store.impose(BinaryXeqY(xs, y))

            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
            }

            val result = search.labeling(store, SimpleSelect(xs, MostConstrainedDynamic(), IndomainMax()), y)
            assertTrue(result)
            assertEquals(min, y.value())
        }
        search(-200, 300)
        search(20, 30)
        search(-1, 1)
        search(0, 100)
    }

    @Test
    fun searchOptimizeReified() {
        fun search(min: Int, max: Int) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            val f = IntVar(store, min, max)
            val x = BooleanVar(store, "x", 0, 1)

            store.impose(IfThen(XeqC(x, 0), And(xs.map { XeqC(it, 0) })))
            store.impose(IfThen(XeqC(x, 1), BinaryXeqY(xs, f)))

            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
            }

            val result = search.labeling(store, SimpleSelect(xs, null, IndomainMin()), f)
            assertTrue(result)
            assertEquals(min, f.min())
        }
        search(-4, 0)
        search(5, 10)
        search(0, 1)
        search(-10, 1)
    }

    @Test
    fun searchSatisfiabilityWithAssumptions() {
        fun test(min: Int, max: Int, literals: IntArray, sat: Boolean) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            val p = IntVar(store, min, max)
            store.impose(BinaryXeqY(xs, p))
            for (lit in literals) {
                if (lit.toBoolean()) {
                    xs[lit.toIx()].setDomain(1, 1)
                } else {
                    xs[lit.toIx()].setDomain(0, 0)
                }
            }

            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
            }
            val result = search.labeling(store, SimpleSelect(xs, null, IndomainMin()))
            assertEquals(sat, result)
        }
        test(-2, -1, intArrayOf(32), true)
        test(-Int.MAX_VALUE, Int.MAX_VALUE, intArrayOf(1, -32, -2), true)
        test(0, Int.MAX_VALUE, intArrayOf(1, 32, -2), false)
        test(-3, 3, intArrayOf(2), true)
        test(-3, 3, intArrayOf(1, 2, 3, -32), false)
    }

    @Test
    fun searchMultipleInts() {
        val store = Store()
        val xs0 = BooleanVar(store)
        val xs1 = Array(3) { BooleanVar(store) }
        val xs2 = Array(8) { BooleanVar(store) }
        val xs3 = Array(8) { BooleanVar(store) }
        val x = arrayOf(xs0) + xs1 + xs2 + xs3

        val p1 = IntVar(store, 0, 5)
        val p2 = IntVar(store, -100, 100)
        val p3 = IntVar(store, 0, 200)

        store.impose(IfThen(XeqC(xs0, 0), And(xs1.map { XeqC(it, 0) })))
        store.impose(IfThen(XeqC(xs0, 1), BinaryXeqY(xs1, p1)))
        store.impose(BinaryXeqY(xs2, p2))
        store.impose(BinaryXeqY(xs3, p3))

        store.impose(LinearInt(arrayOf(p1, p2, p3), intArrayOf(1, 1, -1), ">", 100))

        val p = IntVar(store, -Int.MAX_VALUE, Int.MAX_VALUE)
        store.impose(SumInt(arrayOf(p1, p2, p3), "=", p))


        val search = DepthFirstSearch<BooleanVar>().apply {
            setPrintInfo(false)
        }
        val result = search.labeling(store, SimpleSelect(x, null, IndomainMin()), p)

        assertTrue(result)
        assertTrue(p1.value() > 0)
        assertTrue(p2.value() > 0)
        assertTrue(p3.value() == 0)
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
    fun entailedUncertain1() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        val y = FloatVar(store, -10.0, 20.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun entailedUncertain2() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        val y = FloatVar(store, 0.0, 1.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun groundedSatisfied() {
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
    fun ungroundedSatisfied() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        val y = FloatVar(store, -20.0, -1.0)
        val k = (-5.0f).toRawBits()
        for (i in 20..31) {
            val c = (k shr i) and 1
            xs[i].setDomain(c, c)
        }
        val b = BinaryXeqP(xs, y)
        assertTrue(b.satisfied())
        assertFalse(b.notSatisfied())
    }

    @Test
    fun groundedNotSatisfied() {
        fun test(min: Double, max: Double, p: Float) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            groundVars(xs, p)
            val y = FloatVar(store, min, max)
            val b = BinaryXeqP(xs, y)
            assertFalse(b.satisfied())
            assertTrue(b.notSatisfied())
        }
        test(1.0, 1.0, 0.0f)
        test(-0.0, -0.0, 0.0f)
        test(0.0, 1.0, -0.0f)
        test(-Float.MAX_VALUE.toDouble(), 0.0, Float.MIN_VALUE)
    }

    @Test
    fun ungroundedNotSatisfied1() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        xs[31].domain.setDomain(1, 1)
        val y = FloatVar(store, 0.0, 0.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun ungroundedNotSatisfied2() {
        val store = Store()
        val xs = Array(32) { BooleanVar(store) }
        xs[31].domain.setDomain(0, 0)
        val y = FloatVar(store, -1.0, -0.0)
        val b = BinaryXeqP(xs, y)
        assertFalse(b.satisfied())
        assertTrue(b.notSatisfied())
    }

    @Test
    fun searchOptimize() {
        fun search(min: Float, max: Float) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            val f = FloatVar(store, min.toDouble(), max.toDouble())

            store.impose(BinaryXeqP(xs, f))

            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
            }

            val result = search.labeling(store, SimpleSelect(xs, null, IndomainMin()), f)
            assertTrue(result)
            assertEquals(min.toDouble(), f.value())
        }
        search(-4.75f, 0.0f)
        search(5.0f, 10.0f)
        search(-0.0f, 0.0f)
        search(-10.0f, -1.0f)
    }

    @Test
    fun searchOptimizeReified() {
        fun search(min: Float, max: Float) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            val f = FloatVar(store, min.toDouble(), max.toDouble())
            val x = BooleanVar(store, "x", 0, 1)

            store.impose(IfThen(XeqC(x, 0), And(xs.map { XeqC(it, 0) })))
            store.impose(IfThen(XeqC(x, 1), BinaryXeqP(xs, f)))

            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
            }

            val result = search.labeling(store, SimpleSelect(xs, null, IndomainMin()), f)
            assertTrue(result)
            assertEquals(min.toDouble(), f.min())
        }
        search(-4.75f, 0.0f)
        search(5.0f, 10.0f)
        search(-0.0f, 0.0f)
        search(-10.0f, -1.0f)
    }

    @Test
    fun searchSatisfiabilityWithAssumptions() {
        fun test(min: Float, max: Float, literals: IntArray, sat: Boolean) {
            val store = Store()
            val xs = Array(32) { BooleanVar(store) }
            val p = FloatVar(store, min.toDouble(), max.toDouble())
            store.impose(BinaryXeqP(xs, p))
            for (lit in literals) {
                if (lit.toBoolean()) {
                    xs[lit.toIx()].setDomain(1, 1)
                } else {
                    xs[lit.toIx()].setDomain(0, 0)
                }
            }

            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
            }
            val result = search.labeling(store, SimpleSelect(xs, null, IndomainMin()))
            assertEquals(sat, result)
        }
        test(-1.2f, -1.1f, intArrayOf(32), true)
        test(-Float.MAX_VALUE, Float.MAX_VALUE, intArrayOf(1, -32, -2), true)
        test(0.0f, Float.MAX_VALUE, intArrayOf(1, 32, -2), false)
        test(-3.14f, 3.14f, intArrayOf(1, 2, 3, 4, 20, 21, 22, -30), true)
        test(-3.14f, 3.14f, (1..22).toList().toIntArray() + intArrayOf(30, 31), false)
    }

    @Test
    fun searchMultipleFloatsFeasibility() {
        val store = Store()
        val xs0 = BooleanVar(store)
        val xs1 = Array(32) { BooleanVar(store) }
        val xs2 = Array(32) { BooleanVar(store) }
        val xs3 = Array(32) { BooleanVar(store) }
        val x = arrayOf(xs0) + xs1 + xs2 + xs3

        val p1 = FloatVar(store, 0.0, 5.0)
        val p2 = FloatVar(store, -100.0, 100.0)
        val p3 = FloatVar(store, 0.0, 200.0)

        store.impose(IfThen(XeqC(xs0, 0), And(xs1.map { XeqC(it, 0) })))
        store.impose(IfThen(XeqC(xs0, 1), BinaryXeqP(xs1, p1)))
        store.impose(BinaryXeqP(xs2, p2))
        store.impose(BinaryXeqP(xs3, p3))
        store.impose(XeqC(xs0, 1))

        store.impose(LinearFloat(arrayOf(p1, p2, p3), doubleArrayOf(1.0, 1.0, -1.0), ">", 100.0))

        val p = FloatVar(store, -Double.MAX_VALUE, Double.MAX_VALUE)
        store.impose(SumFloat(arrayOf(p1, p2, p3), "=", p))


        val search = DepthFirstSearch<BooleanVar>().apply {
            setPrintInfo(false)
        }
        val result = search.labeling(store, SimpleSelect(x, MostConstrainedDynamic(), IndomainMin()))

        assertTrue(result)
        assertTrue(p1.value() >= 0.0)
        assertTrue(p2.value() >= 0.0)
        assertTrue(p3.value() >= 0.0)
    }
}
