@file:JvmName("Variables")

package combo.model

import combo.sat.*
import combo.util.AtomicInt
import kotlin.jvm.JvmName

/**
 * This class represents the decision variable in the combinatorial optimization problem. They must
 * be registered in the [Model] to be used. The easiest way of constructing them are through the various
 * methods in [Model.Builder], such as [Model.Builder.flag] or [Model.Builder.nominal] which will also add the
 * required constraints.
 * TODO refactor to remove parentValue
 */
abstract class Variable<out T>(override val name: String) : Value {

    companion object {
        fun defaultName() = "${"$"}x_${COUNTER.getAndIncrement()}"
        private val COUNTER: AtomicInt = AtomicInt()
    }

    override fun toLiteral(rootIndex: VariableIndex) =
            if (parentValue == this) rootIndex.indexOf(this).toLiteral(true)
            else parentValue.toLiteral(rootIndex)

    abstract val parentValue: Value
    override val canonicalVariable: Variable<T> get() = this
    abstract val nbrLiterals: Int
    open val mandatory: Boolean get() = parentValue != this

    abstract fun valueOf(instance: Instance, rootIndex: Int): T?

    abstract val defaultEncoder: Encoder<*>
    abstract fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex): VectorMapping
}

fun reifiedLiteral(scopedIndex: VariableIndex) =
        if (scopedIndex.isRoot) 0
        else scopedIndex.reifiedValue.toLiteral(scopedIndex)

/**
 * This is used for the top variable of the variable hierarchy. It does not take up any space in the optimization
 * problem.
 */
class Root(name: String) : Variable<Unit>(name) {
    override val nbrLiterals get() = 0
    override fun valueOf(instance: Instance, rootIndex: Int) {}
    override fun toLiteral(rootIndex: VariableIndex) = Int.MAX_VALUE
    override fun toString() = "Root($name)"
    override val parentValue get() = this
    override val mandatory: Boolean get() = true
    override val defaultEncoder: Encoder<*> get() = BitsEncoder
    override fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex) =
            throw UnsupportedOperationException()
}

/**
 * This is the simplest type of [Variable] that will either be a constant value when the corresponding binary value is
 * 1 or null otherwise. A [Flag] is named after feature flags, because they wrap a [value].
 */
class Flag<out T> constructor(name: String, val value: T) : Variable<T>(name) {
    override val nbrLiterals: Int get() = 1
    override fun toString() = "Flag($name)"
    override fun valueOf(instance: Instance, rootIndex: Int): T? {
        return if (instance[rootIndex]) value else null
    }

    override val parentValue: Value get() = this
    override val mandatory: Boolean get() = false
    override val defaultEncoder: Encoder<*> get() = BitsEncoder
    override fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex) = object : VectorMapping {
        override val binaryIx: Int get() = binaryIx
        override val vectorIx: Int get() = vectorIx
        override val binarySize: Int get() = 1
        override val reifiedLiteral: Int get() = reifiedLiteral(scopedIndex)
        override val indicatorVariable: Boolean get() = false
        override fun toString() = "FlagMapping($name)"
    }
}

/**
 * A [Select] can be either [Nominal], [Ordinal], or [Multiple], depending on whether the options in the [values]
 * are mutually exclusive or not. For example, selecting a number of displayed items for a GUI item would be best served
 * as an [Nominal] because there can only a single number at a time.
 */
sealed class Select<V, out T> constructor(name: String, mandatory: Boolean, parent: Value, val values: Array<out V>)
    : Variable<T>(name) {

    override val nbrLiterals: Int = values.size + if (mandatory) 0 else 1
    override val parentValue: Value = if (mandatory) parent else this

    fun options(): Array<out Option<V>> = Array(values.size) { optionAt(it) }

    fun optionAt(index: Int) = Option(this, index)

    fun option(value: V): Option<V> {
        for (i in values.indices)
            if (values[i] == value) return Option(this, i)
        throw IllegalArgumentException("Value missing in variable $name. " +
                "Expected to find $value in ${values.joinToString()}")
    }

    override fun defaultMapping(binaryIx: Int, vectorIx: Int, scopedIndex: VariableIndex) = object : VectorMapping {
        override val binaryIx: Int get() = binaryIx
        override val vectorIx: Int get() = vectorIx
        override val binarySize: Int get() = nbrLiterals
        override val reifiedLiteral: Int get() = if (indicatorVariable) binaryIx.toLiteral(true) else reifiedLiteral(scopedIndex)
        override val indicatorVariable: Boolean get() = !mandatory
        override fun toString() = "SelectMapping($name)"
    }
}

/**
 * If a specific option in the [Select.values] array need to be used in a constraint, then use this to get a reference
 * to the corresponding optimization variable.
 */
class Option<out V> constructor(override val canonicalVariable: Select<out V, *>, val valueIndex: Int) : Value {

    init {
        require(valueIndex in canonicalVariable.values.indices) {
            "Option with index=$valueIndex is out of bound with $name."
        }
    }

    val value get() = canonicalVariable.values[valueIndex]
    override fun toLiteral(rootIndex: VariableIndex) = (rootIndex.indexOf(canonicalVariable) + valueIndex
            + if (canonicalVariable.mandatory) 0 else 1).toLiteral(true)

    override fun toString() = "Option($name=$value)"
    override val name: String get() = canonicalVariable.name
}

class Multiple<V> constructor(name: String, mandatory: Boolean, parent: Value, vararg values: V)
    : Select<V, List<V>>(name, mandatory, parent, values) {

    override fun valueOf(instance: Instance, rootIndex: Int): List<V>? {
        if (!mandatory && !instance[rootIndex]) return null
        val ret = ArrayList<V>()
        val offset = rootIndex + (if (mandatory) 0 else 1)
        var i = 0
        while (i < values.size) {
            val value = instance.getFirst(offset + i, offset + values.size)
            if (value < 0) break
            i += value
            ret.add(values[i])
            i++
        }
        if (!mandatory && instance[rootIndex] && ret.isEmpty())
            throw IllegalStateException("Inconsistent instance, should have something set for $this.")
        else if (ret.isEmpty())
            return null
        else return ret
    }

    override fun toString() = "Multiple($name)"
    override val defaultEncoder: Encoder<*> get() = BitsEncoder
}

class Nominal<V> constructor(name: String, mandatory: Boolean, parent: Value, vararg values: V)
    : Select<V, V>(name, mandatory, parent, values) {

    override fun valueOf(instance: Instance, rootIndex: Int): V? {
        if (!mandatory && !instance[rootIndex]) return null
        val offset = if (mandatory) 0 else 1
        val value = instance.getFirst(rootIndex + offset, rootIndex + offset + values.size)
        return if (value < 0) {
            if (mandatory) null
            else throw IllegalStateException("Inconsistent variable, should have something set for $this.")
        } else values[value]
    }

    override fun toString() = "Nominal($name)"
    override val defaultEncoder: Encoder<*> get() = NominalEncoder
}

