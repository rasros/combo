@file:JvmName("Literals")

package combo.sat

import combo.util.IntArrayList
import kotlin.jvm.JvmName
import kotlin.math.absoluteValue

fun Int.toLiteral(truth: Boolean): Int = if (truth) this + 1 else -(this + 1)
fun Int.toBoolean() = this > 0
fun Int.toIx(): Int = this.absoluteValue - 1
fun Int.offset(offset: Int) = (toIx() + offset).toLiteral(toBoolean())
operator fun Int.not(): Int = -this

fun Instance.literal(ix: Int) = ix.toLiteral(this.isSet(ix))

fun Instance.toLiterals(): IntArray {
    val list = IntArrayList()
    val itr = iterator()
    while (itr.hasNext()) list.add(itr.nextInt().toLiteral(true))
    list.toArray()
    return list.toArray()
}

fun Instance.set(literal: Int) = set(literal.toIx(), literal.toBoolean())
fun Instance.setAll(literals: IntArray) = literals.forEach { set(it) }
fun Instance.setAll(literals: Iterable<Int>) = literals.forEach { set(it) }

