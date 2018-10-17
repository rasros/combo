@file:JvmName("Features")

package combo.model

import combo.sat.*
import combo.util.ConcurrentInteger
import combo.util.Tree
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

private fun defaultName() = "x_${COUNTER.getAndIncrement()}"
private val COUNTER: ConcurrentInteger = ConcurrentInteger()
private val EMPTY_INT_ARRAY = IntArray(0)

const val UNIT_FALSE = -1
const val UNIT_TRUE = -2

/**
 * TODO explain
 */
abstract class Feature<T>(val name: String) : Reference() {

    abstract val values: Array<*>
    abstract val nbrLiterals: Int

    override val rootFeature get() = this

    /**
     * The mapped literal index of each literal declared. Some literals will be set to [UNIT_TRUE] or [UNIT_FALSE] if
     * they have been removed from the problem.
     */
    abstract fun createIndexEntry(indices: IntArray): IndexEntry<T>
}

class FeatureTree(override val value: Feature<*>,
                  override val children: List<FeatureTree> = emptyList()) : Tree<Feature<*>, FeatureTree>

interface IndexEntry<T> {

    /**
     * Length of array must be same as [Feature.nbrLiterals]. A negative value means the value is unit.
     */
    val indices: IntArray

    fun valueOf(labeling: Labeling): T?

    fun toLiterals(t: Any?): Literals

    fun isRootUnit(): Boolean = indices[0] >= 0
}

data class FeatureMeta<T>(val feature: Feature<T>, val indexEntry: IndexEntry<T>) {
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

        private val negLit = if (indices[0] >= 0) intArrayOf(indices[0].asLiteral(false)) else EMPTY_INT_ARRAY
        private val posLit = if (indices[0] >= 0) intArrayOf(indices[0].asLiteral(true)) else EMPTY_INT_ARRAY

        override fun toLiterals(t: Any?): Literals {
            return if (t == null && indices[0] == UNIT_TRUE) throw UnsatisfiableException("Value of ${this@Flag} must not be null.")
            else if (t != null && indices[0] == UNIT_FALSE) throw UnsatisfiableException("Value of ${this@Flag} must be null.")
            else if (t != null && t != value) throw ValidationException("Value \"$t\" does not match ${this@Flag}.")
            else if (indices[0] >= 0) if (t == null) negLit else posLit
            else EMPTY_INT_ARRAY
        }

        override fun valueOf(labeling: Labeling) =
                if ((indices[0] >= 0 && labeling[indices[0]]) || indices[0] == UNIT_TRUE) value
                else null
    }

    override fun toLiteral(ix: Ix) = ix.asLiteral(true)
    override val references: Array<out Reference> get() = arrayOf(this)
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
fun <V> or(vararg values: V, name: String = defaultName()) = Or(*values, name = name)

/**
 * TODO explain
 */
@Suppress("UNCHECKED_CAST")
@JvmOverloads
//TODO @JsName("or")
fun <V> or(values: Iterable<V>, name: String = defaultName()): Or<V> =
        Or(*(values.toList() as List<Any>).toTypedArray(), name = name) as Or<V>

/**
 * TODO explain nbrLiterals != size of values
 */
abstract class Select<V, T>(override val values: Array<out V>, name: String = defaultName()) : Feature<T>(name) {

    override val nbrLiterals get() = 1 + values.size
    override fun toLiteral(ix: Ix) = ix.asLiteral(true)

    fun optionIx(index: Int) = Option(this, index)
    fun option(value: V): Option<V> {
        for (i in values.indices)
            if (values[i] == value) return Option(this, i)
        throw ValidationException("Value missing in feature $name. " +
                "Expected to find $value in ${values.joinToString()}")
    }

    override val references: Array<out Reference>
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
            else throw ValidationException("Value \"$t\" not found in ${this@Select}.")
        }
    }
}

class Option<V> constructor(private val select: Select<V, *>, private val valueIndex: Int) : Reference() {
    override fun toLiteral(ix: Ix) = (ix + valueIndex + 1).asLiteral(true)
    override val rootFeature get() = select
    override val references: Array<out Reference> get() = arrayOf(this)
}

class Or<V>(vararg values: V, name: String = defaultName()) : Select<V, Set<V>>(values, name) {

    override fun toSentences(ri: ReferenceIndex): Array<Sentence> {
        val alternatives = values.map { option(it) }.toTypedArray()
        if (alternatives.isEmpty()) return arrayOf(Tautology)
        if (alternatives.size == 1) return (this equivalent alternatives[0]).toSentences(ri)
        return arrayOf(Reified(ri.indexOf(this).asLiteral(true), or(*alternatives).toClause(ri)))
    }

    override fun createIndexEntry(indices: IntArray): IndexEntry<Set<V>> {
        if (indices[0] == UNIT_FALSE) return NullIndexEntry()
        return OrIndexEntry(indices)
    }

    override fun toString() = "Or($name)"

    private inner class OrIndexEntry(indices: IntArray) : SelectIndexEntry<Set<V>>(indices) {

        private val rootFalse = if (indices[0] >= 0) intArrayOf(indices[0].asLiteral(false)) else EMPTY_INT_ARRAY

        override fun valueOf(labeling: Labeling) =
                if (indices[0] >= 0 && !labeling[indices[0]]) null
                else LinkedHashSet<V>().apply {
                    for (i in 1 until this@OrIndexEntry.indices.size)
                        if (this@OrIndexEntry.indices[i] >= 0 && labeling[this@OrIndexEntry.indices[i]] || this@OrIndexEntry.indices[i] == UNIT_TRUE) add(values[i - 1])
                    if (this@OrIndexEntry.indices[0] == UNIT_TRUE && isEmpty()) throw ValidationException(
                            "Inconsistent labeling, should have something set for ${this@Or}.")
                }

        override fun toLiterals(t: Any?): IntArray {
            if (t == null) {
                if (indices[0] == UNIT_TRUE) throw UnsatisfiableException("Value of ${this@Or} can not be null.")
                else return rootFalse
            }
            val col = t as? Collection<*>
                    ?: throw ValidationException("Value of ${this@Or} must be a collection but got $t.")
            if (col.isEmpty())
                throw UnsatisfiableException("Collection for ${this@Or} can not be empty.")
            val arr = IntArray(col.size)
            var k = 0
            for (v in col) {
                val id = valueIx(v ?: throw ValidationException("Value"))
                if (id > 0) arr[k++] = indices[id].asLiteral(true)
            }
            if (k < arr.size) return arr.sliceArray(0 until k)
            return arr.apply { sort() }
        }
    }
}

class Alternative<V>(vararg values: V, name: String = defaultName()) : Select<V, V>(values, name) {

    override fun toString() = "Alternative($name)"

    override fun toSentences(ri: ReferenceIndex): Array<Sentence> {
        val alternatives = values.map { option(it) }.toTypedArray()
        if (alternatives.isEmpty()) return arrayOf(Tautology)
        if (alternatives.size == 1) return (this equivalent alternatives[0]).toSentences(ri)
        return arrayOf<Sentence>(Reified(ri.indexOf(this).asLiteral(true), or(*alternatives).toClause(ri))) +
                excludes(*alternatives).toSentences(ri)
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
            if (it == 0) if (ids[0] >= 0) intArrayOf(ids[0].asLiteral(false)) else EMPTY_INT_ARRAY
            else if (ids[it] >= 0) intArrayOf(ids[it].asLiteral(true))
            else EMPTY_INT_ARRAY
        }

        override fun valueOf(labeling: Labeling): V? {
            if (indices[0] >= 0 && !labeling[indices[0]]) return null
            for (i in 1 until indices.size)
                if (indices[i] >= 0 && labeling[indices[i]]) return values[i - 1]
            throw ValidationException("Inconsistent labeling, should have something set for ${this@Alternative}.")
        }

        override fun toLiterals(t: Any?): IntArray {
            return if (t == null) {
                if (indices[0] == UNIT_TRUE) throw ValidationException("Value of ${this@Alternative} must not be null.")
                else lits[0]
            } else {
                val id = valueIx(t)
                if (id > 0) lits[id]
                else EMPTY_INT_ARRAY
            }
        }
    }
}
