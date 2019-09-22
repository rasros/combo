package combo.model

import combo.sat.Expression
import combo.sat.UnsatisfiableException
import combo.sat.not
import combo.util.IntHashSet

/**
 * A logic expression can be used as part of a 0th order logic expression in [combo.model.ConstraintBuilder].
 * In addition to being an expression, it can be negated.
 */
interface Proposition : Expression {
    operator fun not(): Proposition
}

/**
 * A literal is either a literal value of a variable or the indicator variable of a multi-valued variable.
 */
interface Literal : Expression {
    val name: String

    /**
     * The underlying variable, in [Not] for example the canonical variable is the negated variable.
     */
    val canonicalVariable: Variable<*, *>

    /**
     * Collects literal in dimacs format into [set], this is used when generating assumptions.
     */
    fun collectLiterals(index: VariableIndex, set: IntHashSet)
}

/**
 * A value is both a literal and a proposition, it can be negated. For example, all Variable, but not CNF and Int/Float
 * literals.
 */
interface Value : Literal, Proposition {
    fun toLiteral(rootIndex: VariableIndex): Int
    override fun not(): Value = Not(this)
    override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
        when (val value = toLiteral(index)) {
            Int.MAX_VALUE -> return
            -Int.MAX_VALUE -> throw UnsatisfiableException()
            else -> set.add(value)
        }
    }
}

class Not(private val negated: Value) : Value {
    override val name: String get() = negated.name
    override val canonicalVariable: Variable<*, *> get() = negated.canonicalVariable
    override operator fun not() = negated
    override fun toLiteral(rootIndex: VariableIndex) = !negated.toLiteral(rootIndex)
    override fun toString(): String = "Not($negated)"
}
