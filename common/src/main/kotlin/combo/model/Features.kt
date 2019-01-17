@file:JvmName("Features")

package combo.model

import combo.sat.*
import combo.util.ConcurrentInteger
import combo.util.EMPTY_INT_ARRAY
import combo.util.Tree
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

private fun defaultName() = "x_${COUNTER.getAndIncrement()}"
private val COUNTER: ConcurrentInteger = ConcurrentInteger()

const val UNIT_FALSE = -1
const val UNIT_TRUE = -2

/**
 * TODO explain
 */
abstract class Feature<T>(val name: String) : Variable() {

    abstract val values: Array<*>
    internal abstract val nbrLiterals: Int

    override val rootFeature get() = this

    /**
     * The mapped literal index of each literal declared. Some literals will be set to [UNIT_TRUE] or [UNIT_FALSE] if
     * they have been removed from the problem.
     */
    internal abstract fun createIndexEntry(indices: IntArray): IndexEntry<T>
}

class FeatureTree(override val value: Feature<*>,
                  override val children: List<FeatureTree> = emptyList()) : Tree<Feature<*>, FeatureTree>

internal interface IndexEntry<T> {

    /**
     * Length of array must be same as [Feature.nbrLiterals]. A negative value means the value is unit.
     */
    val indices: IntArray

    fun valueOf(labeling: Labeling): T?

    fun toLiterals(t: Any?): Literals

    fun isRootUnit(): Boolean = indices[0] >= 0
}

internal data class FeatureMeta<T>(val feature: Feature<T>, val indexEntry: IndexEntry<T>) {
    override fun toString() = "FeatureMeta($feature)"
}

/**
 * TODO explain
 */
@JvmOverloads
//TODO @JsName("flagBoolean")
fun flag(name: String = defaultName()) = Flag(true, name = name)

/**
 * TODO explain
 */
@JvmOverloads
//TODO @JsName("flag")
fun <T> flag(value: T, name: String = defaultName()) = Flag(value, name = name)

class Flag<T>(val value: T, name: String = defaultName()) : Feature<T>(name) {

    override val nbrLiterals get() = 1

    override fun createIndexEntry(indices: IntArray): IndexEntry<T> = FlagIndexEntry(indices)

    private inner class FlagIndexEntry(override val indices: IntArray) : combo.model.IndexEntry<T> {

        private val negLit = if (indices[0] >= 0) intArrayOf(indices[0].toLiteral(false)) else EMPTY_INT_ARRAY
        private val posLit = if (indices[0] >= 0) intArrayOf(indices[0].toLiteral(true)) else EMPTY_INT_ARRAY

        override fun toLiterals(t: Any?): Literals {
            return if (t == null && indices[0] == UNIT_TRUE) throw UnsatisfiableException("Value of ${this@Flag} must not be null.")
            else if (t != null && indices[0] == UNIT_FALSE) throw UnsatisfiableException("Value of ${this@Flag} must be null.")
            else if (t != null && t != value) throw IllegalArgumentException("Value \"$t\" does not match ${this@Flag}.")
            else if (indices[0] >= 0) if (t == null) negLit else posLit
            else EMPTY_INT_ARRAY
        }

        override fun valueOf(labeling: Labeling) =
                if ((indices[0] >= 0 && labeling[indices[0]]) || indices[0] == UNIT_TRUE) value
                else null
    }

    override fun toLiteral(ix: Ix) = ix.toLiteral(true)
    override val variables: Array<out Variable> get() = arrayOf(this)
    override val values: Array<Any> = arrayOf(value as Any)
    override fun toString() = "Flag($name)"
}

/**
 * TODO explain
 */
@JvmOverloads
fun <V> alternative(vararg values: V, name: String = defaultName()) = Alternative(*values, name = name)

@Suppress("UNCHECKED_CAST")
@JvmOverloads
//TODO @JsName("alternative")
fun <V> alternative(values: Iterable<V>, name: String = defaultName()): Alternative<V> =
        Alternative(*(values.toList() as List<Any>).toTypedArray(), name = name) as Alternative<V>

/**
 * TODO explain
 */
@JvmOverloads
fun <V> multiple(vararg values: V, name: String = defaultName()) = Multiple(*values, name = name)

/**
 * TODO explain
 */
@Suppress("UNCHECKED_CAST")
@JvmOverloads
//TODO @JsName("or")
fun <V> multiple(values: Iterable<V>, name: String = defaultName()): Multiple<V> =
        Multiple(*(values.toList() as List<Any>).toTypedArray(), name = name) as Multiple<V>

/**
 * TODO explain nbrLiterals != size of values
 */
abstract class Select<V, T>(override val values: Array<out V>, name: String = defaultName()) : Feature<T>(name) {

    override val nbrLiterals get() = 1 + values.size
    override fun toLiteral(ix: Ix) = ix.toLiteral(true)

    fun optionIx(index: Int) = Option(this, index)
    fun option(value: V): Option<V> {
        for (i in values.indices)
            if (values[i] == value) return Option(this, i)
        throw IllegalArgumentException("Value missing in feature $name. " +
                "Expected to find $value in ${values.joinToString()}")
    }

    override val variables: Array<out Variable>
        get() = (sequenceOf(this) + values.asSequence().mapIndexed { ix, _ -> optionIx(ix) }).toList().toTypedArray()


    protected inner class NullIndexEntry<S> : IndexEntry<S> {
        override val indices = IntArray(nbrLiterals) { UNIT_FALSE }
        override fun valueOf(labeling: Labeling): S? = null
        override fun toLiterals(t: Any?): IntArray {
            if (t != null) throw UnsatisfiableException("Value of ${this@Select} must always be null.")
            return EMPTY_INT_ARRAY
        }
    }

    abstract inner class SelectIndexEntry<T>(final override val indices: IntArray) : IndexEntry<T> {
        private val indicesMap: Map<Any?, Int> = indices.withIndex().associate {
            if (it.index > 0)
                Pair(values[it.index - 1], it.index)
            else {
                Pair(null, it.index)
            }
        }

        protected fun valueIx(t: Any): Int {
            val ix: Int? = indicesMap[t]
            if (ix != null)
                return when {
                    indices[ix] >= 0 -> ix
                    indices[ix] == UNIT_TRUE -> -1
                    else -> throw UnsatisfiableException("Value of ${this@Select} cannot be \"$t\" due to conflicts.")
                }
            else throw IllegalArgumentException("Value \"$t\" not found in ${this@Select}.")
        }
    }
}

class Option<V> constructor(private val select: Select<V, *>, private val valueIndex: Int) : Variable() {
    override fun toLiteral(ix: Ix) = (ix + valueIndex + 1).toLiteral(true)
    override val rootFeature get() = select
    override val variables: Array<out Variable> get() = arrayOf(this)
}

class Multiple<V>(vararg values: V, name: String = defaultName()) : Select<V, Set<V>>(values, name) {

    override fun toConstraints(ri: ReferenceIndex): Array<Constraint> {
        val alternatives = values.map { option(it) }.toTypedArray()
        if (alternatives.isEmpty()) return arrayOf(Tautology)
        if (alternatives.size == 1) return (this equivalent alternatives[0]).toConstraints(ri)
        return arrayOf(Reified(ri.indexOf(this).toLiteral(true), or(*alternatives).toClause(ri)))
    }

    override fun createIndexEntry(indices: IntArray): IndexEntry<Set<V>> {
        if (indices[0] == UNIT_FALSE) return NullIndexEntry()
        return MultipleIndexEntry(indices)
    }

    override fun toString() = "Multiple($name)"

    private inner class MultipleIndexEntry(indices: IntArray) : SelectIndexEntry<Set<V>>(indices) {

        private val rootFalse = if (indices[0] >= 0) intArrayOf(indices[0].toLiteral(false)) else EMPTY_INT_ARRAY

        override fun valueOf(labeling: Labeling) =
                if (indices[0] >= 0 && !labeling[indices[0]]) null
                else LinkedHashSet<V>().apply {
                    for (i in 1 until this@MultipleIndexEntry.indices.size)
                        if (this@MultipleIndexEntry.indices[i] >= 0 && labeling[this@MultipleIndexEntry.indices[i]] || this@MultipleIndexEntry.indices[i] == UNIT_TRUE) add(values[i - 1])
                    if (this@MultipleIndexEntry.indices[0] == UNIT_TRUE && isEmpty()) throw IllegalStateException(
                            "Inconsistent labeling, should have something set for ${this@Multiple}.")
                }

        override fun toLiterals(t: Any?): IntArray {
            if (t == null) {
                if (indices[0] == UNIT_TRUE) throw UnsatisfiableException("Value of ${this@Multiple} can not be null.")
                else return rootFalse
            }
            val col = t as? Collection<*>
                    ?: throw IllegalArgumentException("Value of ${this@Multiple} must be a collection but got $t.")
            if (col.isEmpty())
                throw UnsatisfiableException("Collection for ${this@Multiple} can not be empty.")
            // TODO should use labeling truthIterator to loop instead
            val arr = IntArray(col.size)
            var k = 0
            for (v in col) {
                val id = valueIx(v ?: throw NullPointerException("Value is null in list $k"))
                if (id > 0) arr[k++] = indices[id].toLiteral(true)
            }
            if (k < arr.size) return arr.sliceArray(0 until k)
            return arr.apply { sort() }
        }
    }
}

class Alternative<V>(vararg values: V, name: String = defaultName()) : Select<V, V>(values, name) {

    override fun toString() = "Alternative($name)"

    override fun toConstraints(ri: ReferenceIndex): Array<Constraint> {
        val alternatives = values.map { option(it) }.toTypedArray()
        if (alternatives.isEmpty()) return arrayOf(Tautology)
        if (alternatives.size == 1) return (this equivalent alternatives[0]).toConstraints(ri)
        return arrayOf<Constraint>(Reified(ri.indexOf(this).toLiteral(true), or(*alternatives).toClause(ri))) +
                excludes(*alternatives).toConstraints(ri)
    }

    override fun createIndexEntry(indices: IntArray): IndexEntry<V> {
        if (indices[0] == UNIT_FALSE) return NullIndexEntry()
        val unitValueIx = indices.asSequence().lastIndexOf(UNIT_TRUE)
        if (unitValueIx > 0) return UnitIndexEntry(unitValueIx - 1)
        return AlternativeIndexEntry(indices)
    }

    private inner class UnitIndexEntry(val valueIx: Ix) : IndexEntry<V> {
        override val indices = IntArray(nbrLiterals) {
            if (it == 0 || it - 1 == valueIx) UNIT_TRUE else UNIT_FALSE
        }

        override fun valueOf(labeling: Labeling) = values[valueIx]
        override fun toLiterals(t: Any?): IntArray {
            if (t != values[valueIx]) throw UnsatisfiableException(
                    "Value of ${this@Alternative} must be \"${values[valueIx]}\", got \"$t\".")
            return EMPTY_INT_ARRAY
        }
    }

    private inner class AlternativeIndexEntry(ids: IntArray) : SelectIndexEntry<V>(ids) {
        /**
         * Index 0 of lits contains values for when all alternatives are off. The other indices contain the literals
         * for when each alternative is on respectively.
         */
        private val lits = Array(ids.size) {
            if (it == 0) if (ids[0] >= 0) intArrayOf(ids[0].toLiteral(false)) else EMPTY_INT_ARRAY
            else if (ids[it] >= 0) intArrayOf(ids[it].toLiteral(true))
            else EMPTY_INT_ARRAY
        }

        override fun valueOf(labeling: Labeling): V? {
            if (indices[0] >= 0 && !labeling[indices[0]]) return null
            // TODO should use labeling truthIterator to loop instead
            for (i in 1 until indices.size)
                if (indices[i] >= 0 && labeling[indices[i]]) return values[i - 1]
            throw IllegalStateException("Inconsistent labeling, should have something set for ${this@Alternative}.")
        }

        override fun toLiterals(t: Any?): IntArray {
            return if (t == null) {
                if (indices[0] == UNIT_TRUE) throw IllegalArgumentException("Value of ${this@Alternative} must not be null.")
                else lits[0]
            } else {
                val id = valueIx(t)
                if (id > 0) lits[id]
                else EMPTY_INT_ARRAY
            }
        }
    }
}
