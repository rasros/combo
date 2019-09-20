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
     * The unique underlying variable, in [Not] for example the canonical variable is the negated variable.
     */
    val canonicalVariable: Variable<*>

    /**
     * Collects literal in dimacs format into [set], this is used when generating assumptions.
     */
    fun collectLiterals(index: VariableIndex, set: IntHashSet)
}

/**
 * Ref is an unused literal that is intended to be used for easier setting of assumptions.
 */
class Ref(override val name: String, val scope: VariableIndex? = null) : Literal {

    override val canonicalVariable: Variable<*> get() = throw UnsupportedOperationException()

    private abstract inner class RefLiteral : Literal {
        override val name: String get() = this@Ref.name
        override val canonicalVariable: Variable<*> get() = throw UnsupportedOperationException()
    }

    private abstract inner class RefValue : RefLiteral(), Value {
        override fun toLiteral(rootIndex: VariableIndex) =
                ((scope ?: rootIndex)[name] as Value).toLiteral(rootIndex)
    }

    operator fun not(): Value = object : RefValue() {
        override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
            ((scope ?: index)[name] as Value).not().collectLiterals(index, set)
        }
    }

    fun bitValue(ix: Int): Value = object : RefValue() {
        override fun toLiteral(rootIndex: VariableIndex) =
                ((scope ?: rootIndex)[name] as BitsVar).value(ix).toLiteral(rootIndex)
    }

    fun floatValue(value: Float): Literal = object : RefLiteral() {
        override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
            ((scope ?: index)[name] as FloatVar).value(value).collectLiterals(index, set)
        }
    }

    fun intValue(value: Int): Literal = object : RefLiteral() {
        override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
            ((scope ?: index)[name] as IntVar).value(value).collectLiterals(index, set)
        }
    }

    fun <T> option(value: T): Value = object : RefValue() {
        @Suppress("UNCHECKED_CAST")
        override fun toLiteral(rootIndex: VariableIndex) =
                ((scope ?: rootIndex)[name] as Select<T, *>).option(value).toLiteral(rootIndex)
    }

    fun optionAt(ix: Int): Value = object : RefValue() {
        override fun toLiteral(rootIndex: VariableIndex) =
                ((scope ?: rootIndex)[name] as Select<*, *>).optionAt(ix).toLiteral(rootIndex)
    }

    override fun collectLiterals(index: VariableIndex, set: IntHashSet) {
        (scope ?: index)[name].collectLiterals(index, set)
    }
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
    override val canonicalVariable: Variable<*> get() = negated.canonicalVariable
    override operator fun not() = negated
    override fun toLiteral(rootIndex: VariableIndex) = !negated.toLiteral(rootIndex)
    override fun toString(): String = "Not($negated)"
}
