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
 * @param V the type that sub options are parameterized by, [Nothing] for [Flag].
 * @param T the type that is returned, often same as [V].
 */
abstract class Variable<in V, out T>(override val name: String) : Value {

    companion object {
        fun defaultName() = "${"$"}x_${COUNTER.getAndIncrement()}"
        private val COUNTER: AtomicInt = AtomicInt()
    }

    override fun toLiteral(rootIndex: VariableIndex) =
            if (reifiedValue == this) rootIndex.indexOf(this).toLiteral(true)
            else reifiedValue.toLiteral(rootIndex)

    /**
     * The reified value is the value that governs whether the variable is set, it is usually itself or [Root].
     */
    abstract val reifiedValue: Value
    override val canonicalVariable: Variable<V, T> get() = this
    abstract val nbrLiterals: Int

    abstract fun value(value: V): Literal

    /**
     *  If a variable is declared mandatory it will always be set to some value when the parent model is set.
     *  As such it till not have a [reifiedValue]
     */
    val mandatory: Boolean get() = reifiedValue != this

    abstract fun valueOf(instance: Instance, rootIndex: Int): T?
}

/**
 * This is used for the top variable of the variable hierarchy. It does not take up any space in the optimization
 * problem.
 */
class Root(name: String) : Variable<Nothing, Unit>(name) {
    override val nbrLiterals get() = 0
    override fun valueOf(instance: Instance, rootIndex: Int) {}
    override fun toLiteral(rootIndex: VariableIndex) = Int.MAX_VALUE
    override fun toString() = "Root($name)"
    override val reifiedValue: Value get() = this
    override fun value(value: Nothing) = throw UnsupportedOperationException("Cannot be called.")
}

/**
 * This is the simplest type of [Variable] that will either be a constant value when the corresponding binary value is
 * 1 or null otherwise. A [Flag] is named after feature flags, because they wrap a [value].
 */
class Flag<out T> constructor(name: String, val value: T) : Variable<Nothing, T>(name) {

    override val nbrLiterals: Int get() = 1
    override fun toString() = "Flag($name)"
    override fun valueOf(instance: Instance, rootIndex: Int): T? {
        return if (instance[rootIndex]) value else null
    }
    override fun value(value: Nothing) = throw UnsupportedOperationException("Cannot be called.")
    override val reifiedValue: Value get() = this
}

/**
 * A [Select] can be either [Nominal], [Ordinal], or [Multiple], depending on whether the options in the [values]
 * are mutually exclusive or not. For example, selecting a number of displayed items for a GUI item would be best served
 * as an [Nominal] because there can only a single number at a time.
 */
sealed class Select<V, out T> constructor(name: String, parent: Value?, values: Array<out V>)
    : Variable<V, T>(name) {

    override val reifiedValue = parent ?: this
    override val nbrLiterals: Int = values.size + if (mandatory) 0 else 1
    val values: Array<out Option<V>> = Array(values.size) { Option(this, it, values[it]) }

    override fun value(value: V): Option<V> {
        for (i in values.indices)
            if (values[i] == value) return values[i]
        throw IllegalArgumentException("Value missing in variable $name. " +
                "Expected to find $value in ${values.joinToString()}")
    }
}

/**
 * If a specific option in the [Select.values] array need to be used in a constraint, then use this to get a reference
 * to the corresponding optimization variable.
 */
class Option<out V> constructor(override val canonicalVariable: Select<out V, *>, val valueIndex: Int, val value: V) : Value {

    init {
        require(valueIndex in canonicalVariable.values.indices) {
            "Option with index=$valueIndex is out of bound with $name."
        }
    }

    override fun toLiteral(rootIndex: VariableIndex) = (rootIndex.indexOf(canonicalVariable) + valueIndex
            + if (canonicalVariable.mandatory) 0 else 1).toLiteral(true)

    override fun toString() = "Option($name=$value)"
    override val name: String get() = canonicalVariable.name
}

class Multiple<V> constructor(name: String, parent: Value?, vararg values: V)
    : Select<V, List<V>>(name, parent, values) {

    override fun valueOf(instance: Instance, rootIndex: Int): List<V>? {
        if (!mandatory && !instance[rootIndex]) return null
        val ret = ArrayList<V>()
        val offset = rootIndex + (if (mandatory) 0 else 1)
        var i = 0
        while (i < values.size) {
            val value = instance.getFirst(offset + i, offset + values.size)
            if (value < 0) break
            i += value
            ret.add(values[i].value)
            i++
        }
        if (!mandatory && instance[rootIndex] && ret.isEmpty())
            throw IllegalStateException("Inconsistent instance, should have something set for $this.")
        else if (ret.isEmpty())
            return null
        else return ret
    }

    override fun toString() = "Multiple($name)"
}

class Nominal<V> constructor(name: String, parent: Value?, vararg values: V)
    : Select<V, V>(name, parent, values) {

    override fun valueOf(instance: Instance, rootIndex: Int): V? {
        if (!mandatory && !instance[rootIndex]) return null
        val offset = if (mandatory) 0 else 1
        val value = instance.getFirst(rootIndex + offset, rootIndex + offset + values.size)
        return if (value < 0) {
            if (mandatory) null
            else throw IllegalStateException("Inconsistent variable, should have something set for $this.")
        } else values[value].value
    }

    override fun toString() = "Nominal($name)"
}

