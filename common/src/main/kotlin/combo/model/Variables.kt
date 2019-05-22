package combo.model

import combo.sat.Instance
import combo.sat.getBits
import combo.sat.toLiteral
import combo.util.AtomicInt

/**
 * This class represents the decision variable in the combinatorial optimization problem. They must
 * be registered in the [Model] to be used. The easiest way of constructing them are through the various
 * methods in [Model.Builder], such as [Model.Builder.flag] or [Model.Builder.alternative] which will also add the
 * required constraints.
 */
abstract class Variable<out T>(override val name: String) : Value {

    companion object {
        fun defaultName() = "${"$"}x_${COUNTER.inc()}"
        private val COUNTER: AtomicInt = AtomicInt()
    }

    override fun toLiteral(index: VariableIndex) =
            if (reifiedValue == this) index.indexOf(this).toLiteral(true)
            else reifiedValue.toLiteral(index)

    abstract val reifiedValue: Value
    override val canonicalVariable: Variable<T> get() = this
    abstract val nbrLiterals: Int
    open val mandatory: Boolean get() = reifiedValue != this

    abstract fun valueOf(instance: Instance, index: VariableIndex): T?
}

/**
 * This is used for the top variable of the variable hierarchy. It does not take up any space in the optimization
 * problem.
 */
class Root(name: String) : Variable<Unit>(name) {
    override val nbrLiterals get() = 0
    override fun valueOf(instance: Instance, index: VariableIndex) {}
    override fun toLiteral(index: VariableIndex) = Int.MAX_VALUE
    override fun toString() = "Root($name)"
    override val reifiedValue get() = this
    override val mandatory: Boolean get() = true
}

/**
 * This is the simplest type of [Variable] that will either be a constant value when the corresponding binary value is
 * 1 or null otherwise. A [Flag] is named after feature flags, because they wrap a [value].
 */
class Flag<T> constructor(name: String, val value: T) : Variable<T>(name) {
    override val nbrLiterals: Int get() = 1
    override fun toString() = "Flag($name)"
    override fun valueOf(instance: Instance, index: VariableIndex): T? {
        val ix = index.indexOf(this)
        return if (instance[ix]) value else null
    }

    override val reifiedValue: Value get() = this
    override val mandatory: Boolean get() = false
}

/**
 * A [Select] can be either [Alternative] or [Multiple], depending on whether the options in the [values] are mutually
 * exclusive or not. For example, selecting a number of displayed items for a GUI item would be best served as an
 * [Alternative] because there can only a single number at a time.
 */
sealed class Select<V, T> constructor(name: String, mandatory: Boolean, parent: Value, val values: Array<out V>)
    : Variable<T>(name) {

    override val nbrLiterals: Int = values.size + if (mandatory) 0 else 1
    override val reifiedValue: Value = if (mandatory) parent else this

    fun options(): Array<Option<V>> = Array(values.size) { optionIx(it) }

    inline fun optionBy(selector: (V) -> Boolean): Option<V> {
        for (i in values.indices)
            if (selector.invoke(values[i])) return Option(this, i)
        throw IllegalArgumentException("Selector could not find value in variable $name.")
    }

    fun optionIx(index: Int) = Option(this, index)

    fun option(value: V): Option<V> {
        for (i in values.indices)
            if (values[i] == value) return Option(this, i)
        throw IllegalArgumentException("Value missing in variable $name. " +
                "Expected to find $value in ${values.joinToString()}")
    }
}

/**
 * If a specific option in the [Select.values] array need to be used in a constraint, then use this to get a reference
 * to the corresponding optimization variable.
 */
class Option<V> constructor(override val canonicalVariable: Select<V, *>, val valueIndex: Int) : Value {

    init {
        require(valueIndex in canonicalVariable.values.indices) {
            "Option with index=$valueIndex is out of bound with $name."
        }
    }

    val value get() = canonicalVariable.values[valueIndex]
    override fun toLiteral(index: VariableIndex) = (index.indexOf(canonicalVariable) + valueIndex
            + if (canonicalVariable.mandatory) 0 else 1).toLiteral(true)

    override fun toString() = "Option($name=$value)"
    override val name: String get() = canonicalVariable.name
}

class Multiple<V> constructor(name: String, mandatory: Boolean, parent: Value, vararg values: V)
    : Select<V, Set<V>>(name, mandatory, parent, values) {

    override fun valueOf(instance: Instance, index: VariableIndex): Set<V>? {
        val ix = index.indexOf(this)
        if (!mandatory && !instance[ix]) return null

        val ret = HashSet<V>()
        var offset = if (mandatory) 0 else 1
        val nbrInts = (values.size shr 5) + if (values.size and 0x1F > 0) 1 else 0

        for (i in 0 until nbrInts) {
            val nbrBits = if (i == nbrInts - 1) values.size and 0x1F else 32
            var k = instance.getBits(ix + offset, nbrBits)
            var j = 0
            while (k != 0) {
                if (k and 1 == 1) ret.add(values[(i shl 5) + j])
                j++
                k = k ushr 1
            }
            offset += nbrBits
        }
        if (!mandatory && instance[ix] && ret.isEmpty())
            throw IllegalStateException("Inconsistent instance, should have something set for $this.")
        else if (ret.isEmpty())
            return null
        return ret
    }

    override fun toString() = "Multiple($name)"
}

class Alternative<V> constructor(name: String, mandatory: Boolean, parent: Value, vararg values: V)
    : Select<V, V>(name, mandatory, parent, values) {

    override fun valueOf(instance: Instance, index: VariableIndex): V? {
        val ix = index.indexOf(this)
        if (!mandatory && !instance[ix]) return null

        var offset = if (mandatory) 0 else 1
        val nbrInts = (values.size shr 5) + if (values.size and 0x1F > 0) 1 else 0

        for (i in 0 until nbrInts) {
            val nbrBits = if (i == nbrInts - 1) values.size and 0x1F else 32
            var k = instance.getBits(ix + offset, nbrBits)
            var j = 0
            while (k != 0) {
                if (k and 1 == 1) return values[(i shl 5) + j]
                j++
                k = k ushr 1
            }
            offset += nbrBits
        }
        if (!mandatory && instance[ix])
            throw IllegalStateException("Inconsistent variable, should have something set for $this.")
        return null
    }

    override fun toString() = "Alternative($name)"
}