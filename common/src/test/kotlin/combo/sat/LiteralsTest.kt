package combo.sat

import kotlin.test.*

class LiteralsTest {

    @Test
    fun toLiteral() {
        assertEquals(0, 0.toLiteral(true))
        assertEquals(1, 0.toLiteral(false))

        assertEquals(20, 10.toLiteral(true))
        assertEquals(21, 10.toLiteral(false))
    }

    @Test
    fun toId() {
        assertEquals(0, 0.toIx())
        assertEquals(0, 1.toIx())
        assertEquals(3, 7.toIx())
        assertEquals(4, 8.toIx())
        assertEquals(4, 9.toIx())
    }

    @Test
    fun toBoolean() {
        assertTrue(0.toBoolean())
        assertFalse(1.toBoolean())
        assertTrue(6.toBoolean())
        assertFalse(7.toBoolean())
    }

    @Test
    fun not() {
        assertEquals(1, !0)
        assertEquals(0, !1)
        assertEquals(11, !10)
        assertEquals(10, !11)
        assertEquals(11, !(!11))
    }

    @Test
    fun toDimacs() {
        assertEquals(1, 0.toDimacs())
        assertEquals(-1, 1.toDimacs())
        assertEquals(11, 20.toDimacs())
        assertEquals(-11, 21.toDimacs())
    }

    @Test
    fun fromDimacs() {
        assertEquals(0, 1.fromDimacs())
        assertEquals(1, (-1).fromDimacs())
        assertEquals(28, 15.fromDimacs())
        assertEquals(29, (-15).fromDimacs())
    }

    @Test
    fun fromAndToDimacs() {
        for (i in 0 until 100) {
            assertEquals(i, i.toDimacs().fromDimacs())
            assertEquals(i, i.fromDimacs().toDimacs())
        }
    }
}
