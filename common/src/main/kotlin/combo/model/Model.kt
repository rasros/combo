package combo.model

import combo.sat.*
import combo.sat.constraints.*
import combo.util.IntHashSet
import combo.util.IntRangeCollection
import combo.util.MAX_VALUE32
import combo.util.collectionOf
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

// TODO doc

@DslMarker
annotation class ModelMarker

class Model(val problem: Problem, val index: VariableIndex, val scope: Scope) {

    fun denseAssignment(vararg literals: Literal) = Assignment(BitArray(problem.nbrVariables), index, scope, literals)
    fun sparseAssignment(vararg literals: Literal) = Assignment(SparseBitArray(problem.nbrVariables), index, scope, literals)
    fun toAssignment(instance: Instance) = Assignment(instance, index, scope)
    fun toAssignments(sequence: Sequence<Instance>) = sequence.map { toAssignment(it) }

    operator fun get(name: String): Variable<*, *> = scope[name]
    operator fun <V> get(name: String, value: V) = scope.find<Variable<V, *>>(name)?.value(value)
            ?: throw NoSuchElementException("Scope contains no variable with name $name.")

    companion object {

        @JvmStatic
        @JvmOverloads
        fun model(name: String = Variable.defaultName(), init: Builder.() -> Unit): Model {
            val root = Root(name)
            val builder = Builder(root)
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
            init.invoke(builder)
            return builder
        }
    }

    @ModelMarker
    class Builder private constructor(val index: VariableIndex,
                                      val scope: Scope,
                                      private val constraints: MutableList<Constraint>,
                                      private val auxiliary: MutableSet<Variable<*, *>>,
                                      @JvmSynthetic internal val units: IntHashSet)
        : Value by scope.reifiedValue {


        constructor(variable: Variable<*, *>) : this(VariableIndex(), Scope(variable), ArrayList(), HashSet(), IntHashSet())

        fun build(): Model {

            val constraints = let {
                val problem = Problem(index.nbrVariables, constraints.toTypedArray())
                val reduced = problem.unitPropagation(units, true)
                units.remove(Int.MAX_VALUE)
                if (units.size > 0) reduced + Conjunction(collectionOf(*units.toArray()))
                else reduced
            }

            val auxiliary = let {
                val set = IntHashSet(nullValue = -1)
                for (v in auxiliary) {
                    val ix = index.indexOf(v)
                    for (j in 0 until v.nbrLiterals)
                        set.add(ix + j)
                }
                set
            }

            return Model(Problem(index.nbrVariables, constraints, auxiliary), index, scope)
        }

        @JvmOverloads
        fun bool(name: String = Variable.defaultName()) = flag(name, true)

        @JvmOverloads
        fun <V> flag(name: String = Variable.defaultName(), value: V) = Flag(name, value).also {
            addVariable(it)
        }

        @JvmOverloads
        fun <V> optionalNominal(name: String = Variable.defaultName(), vararg values: V) = nominalHelper(name, false, values)

        @JvmOverloads
        fun <V> nominal(name: String = Variable.defaultName(), vararg values: V) = nominalHelper(name, true, values)

        private fun <V> nominalHelper(name: String = Variable.defaultName(), mandatory: Boolean = false, values: Array<out V>) =
                Nominal(name, if (mandatory) scope.reifiedValue else null, *values).also {
                    require(values.isNotEmpty())
                    addVariable(it)
                    val firstOption = it.values[0].toLiteral(index)
                    val optionSet = IntRangeCollection(firstOption, firstOption + it.values.size - 1)
                    constraint { it.reifiedValue reifiedEquivalent Disjunction(optionSet) }
                    constraint { Cardinality(optionSet, 1, Relation.LE) }
                }

        @JvmOverloads
        fun <V> multiple(name: String = Variable.defaultName(), vararg values: V) = multipleHelper(name, true, values)

        @JvmOverloads
        fun <V> optionalMultiple(name: String = Variable.defaultName(), vararg values: V) = multipleHelper(name, false, values)

        private fun <V> multipleHelper(name: String, mandatory: Boolean, values: Array<out V>) =
                Multiple(name, if (mandatory) scope.reifiedValue else null, *values).also {
                    require(values.isNotEmpty())
                    addVariable(it)
                    val firstOption = it.values[0].toLiteral(index)
                    val optionSet = IntRangeCollection(firstOption, firstOption + it.values.size - 1)
                    constraint { it.reifiedValue reifiedEquivalent Disjunction(optionSet) }
                }

        @JvmOverloads
        fun optionalInt(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE,
                        max: Int = Int.MAX_VALUE) = intHelper(name, false, min, max)

        @JvmOverloads
        fun int(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE,
                max: Int = Int.MAX_VALUE) = intHelper(name, true, min, max)

        private fun intHelper(name: String, mandatory: Boolean, min: Int, max: Int) =
                IntVar(name, if (mandatory) scope.reifiedValue else null, min, max).also {
                    addVariable(it)
                    val ix = index.indexOf(it)
                    val offset = if (mandatory) 0 else 1
                    val zeros = IntRangeCollection((ix + it.nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
                    constraint { !it.reifiedValue reifiedImplies Conjunction(zeros) }
                    constraint { it.reifiedValue reifiedImplies IntBounds(ix + offset, min, max, it.nbrLiterals - offset) }
                }

        @JvmOverloads
        fun optionalFloat(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32,
                          max: Float = MAX_VALUE32) = floatHelper(name, false, min, max)

        @JvmOverloads
        fun float(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32,
                  max: Float = MAX_VALUE32) = floatHelper(name, true, min, max)

        private fun floatHelper(name: String, mandatory: Boolean, min: Float, max: Float) =
                FloatVar(name, if (mandatory) scope.reifiedValue else null, min, max).also {
                    addVariable(it)
                    val ix = index.indexOf(it)
                    val offset = if (mandatory) 0 else 1
                    val zeros = IntRangeCollection((ix + it.nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
                    constraint { !it.reifiedValue reifiedImplies Conjunction(zeros) }
                    constraint { it.reifiedValue reifiedImplies FloatBounds(ix + offset, min, max) }
                }

        fun bits(name: String = Variable.defaultName(), nbrBits: Int) = bitsHelper(name, true, nbrBits)
        fun optionalBits(name: String = Variable.defaultName(), nbrBits: Int) = bitsHelper(name, false, nbrBits)

        private fun bitsHelper(name: String, mandatory: Boolean, nbrBits: Int) =
                BitsVar(name, if (mandatory) scope.reifiedValue else null, nbrBits).also {
                    addVariable(it)
                    val ix = index.indexOf(it)
                    val offset = if (mandatory) 0 else 1
                    val zeros = IntRangeCollection((ix + it.nbrLiterals - 1).toLiteral(false), (ix + offset).toLiteral(false))
                    constraint { !it.reifiedValue reifiedImplies Conjunction(zeros) }
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
            val builder = Builder(index, scope.addChildScope(scopeName, reifiedValue), constraints, auxiliary, units)
            if (!scope.inScope(reifiedValue.canonicalVariable.name))
                constraint { reifiedValue implies this@Builder.scope.reifiedValue }
            init.invoke(builder)
            return builder
        }

        /**
         * Add a constraint through [ConstraintBuilder]. Note that only one constraint can be added.
         */
        fun constraint(init: ConstraintBuilder.() -> Expression) {
            val builder = ConstraintBuilder(scope, index)
            val exp = init.invoke(builder)
            when (exp) {
                is Literal -> {
                    val set = IntHashSet()
                    exp.collectLiterals(index, set)
                    addConstraint(Conjunction(collectionOf(*set.toArray())))
                }
                is CNF -> exp.disjunctions.forEach { addConstraint(it) }
                is Constraint -> addConstraint(exp)
                else -> throw UnsupportedOperationException(
                        "Custom types of expression cannot be added as constraints in this way.")
            }
        }

        fun addVariable(variable: Variable<*, *>) {
            require(variable.nbrLiterals > 0)
            index.add(variable)
            scope.add(variable)
            if (!variable.mandatory) constraint { variable implies this@Builder.scope.reifiedValue }
        }

        /**
         * Adds a separate [model] as a sub model to this model. This will copy all constraints in the other root model
         * with an offset and add them to this model. All variables from root will be added unmodified.
         */
        fun addModel(builder: Builder) {
            val scopeQueue = ArrayList<Scope>()
            val parents = ArrayList<Scope>()
            scopeQueue.add(builder.scope)
            parents.add(this.scope)

            val offset = index.nbrVariables

            while (scopeQueue.isNotEmpty()) {
                val nextScope = scopeQueue.removeAt(0)
                val parent = parents.removeAt(0)
                val scope = parent.addChildScope(nextScope.scopeName, nextScope.reifiedValue)
                for (variable in nextScope.scopeVariables) {
                    index.add(variable)
                    scope.add(variable)
                }
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
                    c = c.remap(Int.MAX_VALUE.toIx(), scope.reifiedValue.toLiteral(index).toIx())
                } else c = c.offset(offset)
                addConstraint(c)
            }

            this.auxiliary.addAll(builder.auxiliary)
        }

        /**
         * Add a constraint to the model.
         * @param unitPropagation perform unit propagation simplification based on the current unit literals in the
         * model. This might be turned off if the constraint is represented lazily as a huge [IntRangeCollection] and
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

        fun <V : Variable<*, *>> auxiliary(variable: V) = variable.also {
            this.auxiliary.add(variable)
        }

        override fun toString() = "Builder($name)"
    }

    override fun toString() = "Model(${scope.scopeName})"
}

