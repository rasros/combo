package combo.sat

import kotlin.test.*

class LiteralsTest {

    @Test
    fun toLiteral() {
        assertEquals(0, 0.asLiteral(true))
        assertEquals(1, 0.asLiteral(false))

        assertEquals(20, 10.asLiteral(true))
        assertEquals(21, 10.asLiteral(false))
    }

    @Test
    fun toId() {
        assertEquals(0, 0.asIx())
        assertEquals(0, 1.asIx())
        assertEquals(3, 7.asIx())
        assertEquals(4, 8.asIx())
        assertEquals(4, 9.asIx())
    }

    @Test
    fun toBoolean() {
        assertTrue(0.asBoolean())
        assertFalse(1.asBoolean())
        assertTrue(6.asBoolean())
        assertFalse(7.asBoolean())
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
    fun validateOk() {
        intArrayOf(0, 2, 6, 9).validate()
    }

    @Test
    fun validateFailDuplicated() {
        assertFailsWith(ValidationException::class) {
            intArrayOf(0, 1).validate()
        }
    }

    @Test
    fun validateUnorderedFail() {
        assertFailsWith(ValidationException::class) {
            intArrayOf(2, 0).validate()
        }
    }

    @Test
    fun toDimacs() {
        assertEquals(1, 0.asDimacs())
        assertEquals(-1, 1.asDimacs())
        assertEquals(11, 20.asDimacs())
        assertEquals(-11, 21.asDimacs())
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
            assertEquals(i, i.asDimacs().fromDimacs())
            assertEquals(i, i.fromDimacs().asDimacs())
        }
    }
}
