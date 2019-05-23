@file:JvmName("Literals")

package combo.sat

import combo.util.IntList
import kotlin.jvm.JvmName
import kotlin.math.absoluteValue

/**
 * A literal is a variable with truth and false values. The index is left bit shifted one step and the truth value
 * is stored in the last bit.
 */
typealias Literal = Int

fun Int.toLiteral(truth: Boolean): Literal = if (truth) this + 1 else -(this + 1)
fun Literal.toBoolean() = this > 0
fun Literal.toIx(): Int = this.absoluteValue - 1
fun Literal.offset(offset: Int) = (toIx() + offset).toLiteral(toBoolean())
operator fun Literal.not(): Literal = -this

typealias Literals = IntArray

operator fun Instance.contains(literal: Literal): Boolean = literal(literal.toIx()) == literal
fun Instance.literal(ix: Int) = ix.toLiteral(this[ix])

fun Instance.toLiterals(): Literals {
    val list = IntList()
    val itr:IntIterator = TODO()//iterator()
    while (itr.hasNext()) list.add(itr.nextInt().toLiteral(true))
    list.toArray()
    return list.toArray()
}

fun MutableInstance.set(literal: Literal) = set(literal.toIx(), literal.toBoolean())
fun MutableInstance.setAll(literals: Literals) = literals.forEach { set(it) }
fun MutableInstance.setAll(literals: Iterable<Literal>) = literals.forEach { set(it) }

