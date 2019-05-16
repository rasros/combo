package combo.model

import combo.sat.*
import combo.sat.constraints.*
import combo.util.IntHashSet
import combo.util.IntRangeSet
import combo.util.MAX_VALUE32
import combo.util.collectionOf
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

// TODO doc

@DslMarker
annotation class ModelMarker

class Model(val problem: Problem, val index: VariableIndex) {

    fun toAssignment(instance: Instance) = Assignment(instance, index)
    fun toAssignments(sequence: Sequence<Instance>) = sequence.map { toAssignment(it) }

    companion object {

        @JvmStatic
        @JvmOverloads
        fun model(name: String = Variable.defaultName(), init: Builder.() -> Unit): Model {
            val root = Root(name)
            val builder = Builder(root)
            builder.index.add(root)
            builder.units.add(Int.MAX_VALUE)
            init.invoke(builder)
            return builder.build()
        }

        /**
         * This can be used to decouple the model construction by creating a sub-model that can be added to another
         * model using the [Builder.addModel] method. It can also be used as a model through the [Builder.build] method
         * as expected, this is useful for testing the model in isolation for example.
         */
        @JvmStatic
        @JvmOverloads
        fun builder(name: String = Variable.defaultName(), init: Builder.() -> Unit): Builder {
            val root = Root(name)
            val builder = Builder(root)
            builder.index.add(root)
            init.invoke(builder)
            return builder
        }
    }

    @ModelMarker
    class Builder private constructor(val value: Value,
                                      val index: VariableIndex,
                                      private val constraints: MutableList<Constraint>,
                                      @JvmSynthetic internal val units: IntHashSet) : Value by value {

        constructor(variable: Variable<*>) : this(variable, VariableIndex(variable.name), ArrayList(), IntHashSet())

        fun build(): Model {
            val problem = Problem(constraints.toTypedArray(), index.nbrVariables)
            val reduced = problem.unitPropagation(units, true)
            units.remove(Int.MAX_VALUE)
            val finalConstraints = if (units.size > 0) reduced + Conjunction(collectionOf(*units.toArray()))
            else reduced
            return Model(Problem(finalConstraints, index.nbrVariables), index)
        }

        @JvmOverloads
        fun bool(name: String = Variable.defaultName()) = flag(name, true)

        @JvmOverloads
        fun <V> flag(name: String = Variable.defaultName(), value: V) = Flag(name, value).also {
            addVariable(it)
        }

        @JvmOverloads
        fun <V> optionalAlternative(name: String = Variable.defaultName(), vararg values: V) = alternativeHelper(name, false, values)

        @JvmOverloads
        fun <V> alternative(name: String = Variable.defaultName(), vararg values: V) = alternativeHelper(name, true, values)

        private fun <V> alternativeHelper(name: String = Variable.defaultName(), mandatory: Boolean = false, values: Array<out V>) =
                Alternative(name, mandatory, value, *values).also {
                    require(values.isNotEmpty())
                    addVariable(it)
                    val firstOption = it.optionIx(0).toLiteral(index)
                    val optionSet = IntRangeSet(firstOption, firstOption + it.values.size - 1)
                    constraint { (if (mandatory) this@Builder.value else it) reifiedEquivalent Disjunction(optionSet) }
                    constraint { Cardinality(optionSet, 1, Relation.LE) }
                }

        @JvmOverloads
        fun <V> multiple(name: String = Variable.defaultName(), vararg values: V) = multipleHelper(name, true, values)

        @JvmOverloads
        fun <V> optionalMultiple(name: String = Variable.defaultName(), vararg values: V) = multipleHelper(name, false, values)

        private fun <V> multipleHelper(name: String, mandatory: Boolean, values: Array<out V>) =
                Multiple(name, mandatory, value, *values).also {
                    require(values.isNotEmpty())
                    addVariable(it)
                    val firstOption = it.optionIx(0).toLiteral(index)
                    val optionSet = IntRangeSet(firstOption, firstOption + it.values.size - 1)
                    constraint { (if (mandatory) this@Builder.value else it) reifiedEquivalent Disjunction(optionSet) }
                }

        @JvmOverloads
        fun optionalInt(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE,
                        max: Int = Int.MAX_VALUE) = intHelper(name, false, min, max)

        @JvmOverloads
        fun int(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE,
                max: Int = Int.MAX_VALUE) = intHelper(name, true, min, max)

        private fun intHelper(name: String, mandatory: Boolean, min: Int, max: Int) =
                IntVar(name, mandatory, value, min, max).also {
                    addVariable(it)
                    val ix = index.indexOf(it)
                    val parent = if (mandatory) value else it
                    val offset = if (mandatory) 0 else 1
                    val zeros = IntRangeSet((ix + it.nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
                    constraint { !parent reifiedImplies Conjunction(zeros) }
                    constraint { parent reifiedImplies IntBounds(ix + offset, min, max, it.nbrLiterals - offset) }
                }

        @JvmOverloads
        fun optionalFloat(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32,
                          max: Float = MAX_VALUE32) = floatHelper(name, false, min, max)

        @JvmOverloads
        fun float(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32,
                  max: Float = MAX_VALUE32) = floatHelper(name, true, min, max)

        private fun floatHelper(name: String, mandatory: Boolean, min: Float, max: Float) =
                FloatVar(name, mandatory, value, min, max).also {
                    addVariable(it)
                    val ix = index.indexOf(it)
                    val parent = if (mandatory) value else it
                    val offset = if (mandatory) 0 else 1
                    val zeros = IntRangeSet((ix + it.nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
                    constraint { !parent reifiedImplies Conjunction(zeros) }
                    constraint { parent reifiedImplies FloatBounds(ix + offset, min, max) }
                }

        fun bits(name: String = Variable.defaultName(), nbrBits: Int) = bitsHelper(name, true, nbrBits)
        fun optionalBits(name: String = Variable.defaultName(), nbrBits: Int) = bitsHelper(name, false, nbrBits)

        private fun bitsHelper(name: String, mandatory: Boolean, nbrBits: Int) =
                BitsVar(name, mandatory, value, nbrBits).also {
                    addVariable(it)
                    val ix = index.indexOf(it)
                    val parent = if (mandatory) value else it
                    val offset = if (mandatory) 0 else 1
                    val zeros = IntRangeSet((ix + it.nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
                    constraint { !parent reifiedImplies Conjunction(zeros) }
                }

        /**
         * Adds an optional sub-model. Every variable in the new model will be controlled by a [bool] named [name]. The
         * scope name will also be [name].
         */
        @JvmOverloads
        fun model(name: String = Variable.defaultName(), init: Builder.() -> Unit) = model(bool(name), name, init)

        /**
         * A sub model allows organizing groups of variables in a logical hierarchy. Every variable 'v' under this will
         * have the constraint that v => reifiedValue. In this way, variables below this will only be true if the
         * reifiedValue is true.
         */
        @JvmOverloads
        fun model(reifiedValue: Value, scopeName: String = reifiedValue.name, init: Builder.() -> Unit): Builder {
            require(index.contains(reifiedValue.canonicalVariable)) { "${reifiedValue.canonicalVariable} not found in model." }
            val builder = Builder(reifiedValue, index.addChildScope(scopeName), constraints, units)
            if (!index.inScope(reifiedValue.canonicalVariable.name))
                constraint { reifiedValue implies this@Builder.value }
            init.invoke(builder)
            return builder
        }

        /**
         * Add a constraint through [ConstraintBuilder]. Note that only one constraint can be added.
         */
        fun constraint(init: ConstraintBuilder.() -> Expression) {
            val builder = ConstraintBuilder(index)
            val exp = init.invoke(builder)
            when (exp) {
                is Literal -> {
                    val set = IntHashSet()
                    exp.toAssumption(index, set)
                    addConstraint(Conjunction(collectionOf(*set.toArray())))
                }
                is CNF -> exp.disjunctions.forEach { addConstraint(it) }
                is Constraint -> addConstraint(exp)
                else -> throw UnsupportedOperationException(
                        "Custom types of expression cannot be added as constraints in this way.")
            }
        }

        fun addVariable(variable: Variable<*>) {
            require(variable.nbrLiterals > 0)
            index.add(variable)
            if (!variable.mandatory) constraint { variable implies this@Builder.value }
        }

        /**
         * Adds a separate [model] as a sub model to this model. This will copy all constraints in the other root model
         * with an offset and add them to this model. All variables from root will be added unmodified.
         */
        fun addModel(builder: Builder) {
            val scopeQueue = ArrayList<VariableIndex>()
            val parents = ArrayList<VariableIndex>()
            scopeQueue.add(builder.index)
            parents.add(this.index)

            val offset = index.nbrVariables

            while (scopeQueue.isNotEmpty()) {
                val nextScope = scopeQueue.removeAt(0)
                val parent = parents.removeAt(0)
                val scope = parent.addChildScope(nextScope.scopeName)
                for (variable in nextScope.scopeVariables) scope.add(variable)
                val childScopes = nextScope.children
                val pre = scopeQueue.size
                scopeQueue.addAll(childScopes)
                parents.addAll(Array(scopeQueue.size - pre) { scope })
            }

            for (constraint in builder.constraints) {
                var c = constraint
                if (Int.MAX_VALUE in c.literals || !Int.MAX_VALUE in c.literals) {
                    c = c.remap(Int.MAX_VALUE.toIx(), Int.MAX_VALUE.offset(-offset - 1))
                    c = c.offset(offset)
                    c = c.remap(Int.MAX_VALUE.toIx(), value.toLiteral(index).toIx())
                } else c = c.offset(offset)
                addConstraint(c)
            }
        }

        /**
         * Add a constraint to the model.
         * @param unitPropagation perform unit propagation simplification based on the current unit literals in the
         * model. This might be turned off if the constraint is represented lazily as a huge [IntRangeSet] and
         * performing unit propagation will add a hole to the range which will expand the set to a concrete [IntHashSet].
         */
        @JvmOverloads
        fun addConstraint(constraint: Constraint, unitPropagation: Boolean = true) {
            var prop = constraint
            if (unitPropagation) {
                units.forEach {
                    prop = prop.unitPropagation(it)
                    when (prop) {
                        is Tautology -> return
                        is Empty -> throw UnsatisfiableException("Unsatisfiable by unit propagation.")
                    }
                }
            }
            if (prop.isUnit())
                units.addAll(prop.unitLiterals())
            constraints.add(prop)
        }

        override fun toString() = "Builder($name)"
    }

    override fun toString() = "Model(${index.scopeName})"
}

