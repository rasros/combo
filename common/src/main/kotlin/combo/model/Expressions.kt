package combo.model

import combo.sat.Expression
import combo.sat.UnsatisfiableException
import combo.sat.not
import combo.util.IntHashSet

/**
 * A logic expression can be used as part of a 0th order logic expression in [combo.model.ConstraintBuilder].
 */
interface Proposition : Expression {
    operator fun not(): Proposition
}

interface Literal : Expression {
    val name: String
    val canonicalVariable: Variable<*>
    fun toAssumption(index: VariableIndex, set: IntHashSet)
}

interface Value : Literal, Proposition {
    fun toLiteral(index: VariableIndex): Int
    override fun not(): Value = Not(this)
    override fun toAssumption(index: VariableIndex, set: IntHashSet) {
        when (val value = toLiteral(index)) {
            Int.MAX_VALUE -> return
            -Int.MAX_VALUE -> throw UnsatisfiableException()
            else -> set.add(value)
        }
    }
}

class Not(private val negated: Value) : Value {
    override val name: String get() = negated.name
    override val canonicalVariable: Variable<*> get() = negated.canonicalVariable
    override operator fun not() = negated
    override fun toLiteral(index: VariableIndex) = !negated.toLiteral(index)
    override fun toString(): String = "Not($negated)"
}
