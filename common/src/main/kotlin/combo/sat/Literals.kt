package combo.sat

import combo.model.ValidationException
import kotlin.math.abs

typealias Ix = Int
typealias Literal = Int

fun Ix.asLiteral(truth: Boolean): Literal = this shl 1 xor if (truth) 0 else 1
fun Literal.asBoolean() = this and 1 == 0
fun Literal.asIx(): Ix = this shr 1
operator fun Literal.not(): Literal = this xor 1
fun Literal.asDimacs() = (asIx() + 1) * if (asBoolean()) 1 else -1
fun Literal.fromDimacs() = (abs(this) - 1).asLiteral(this >= 0)

typealias Literals = IntArray

fun Literals.validate() {
    for (i in 1 until this.size) {
        if (this[i - 1].asIx() >= this[i].asIx()) {
            throw ValidationException("Unordered clause.", literal = this[i]);
        }
    }
}

fun Boolean.toInt() = if (this) 1 else 0

