@file:JvmName("Literals")

package combo.sat

import kotlin.jvm.JvmName

import kotlin.math.abs

/**
 * An [Ix] represents a variable index, starting from 0 to the number of variables.
 */
typealias Ix = Int

/**
 * A literal is a variable with truth and false values. The [Ix] is left bit shifted one step and the truth value
 * is stored in the last bit.
 */
typealias Literal = Int

fun Ix.toLiteral(truth: Boolean): Literal = this shl 1 xor if (truth) 0 else 1
fun Literal.toBoolean() = this and 1 == 0
fun Literal.toIx(): Ix = this shr 1
operator fun Literal.not(): Literal = this xor 1

fun Literal.toDimacs() = (toIx() + 1) * if (toBoolean()) 1 else -1
fun Literal.fromDimacs() = (abs(this) - 1).toLiteral(this >= 0)

typealias Literals = IntArray

fun Boolean.toInt() = if (this) 1 else 0

