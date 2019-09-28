package combo.model

import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.sat.constraints.Relation
import combo.util.IntHashSet
import combo.util.MAX_VALUE32
import combo.util.collectionOf
import combo.util.mapArray
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads

// TODO doc

class Model(val problem: Problem, val index: VariableIndex, val scope: Scope) {

    fun denseAssignment(vararg literals: Literal) = Assignment(BitArray(problem.nbrVariables), index, scope, literals)
    fun sparseAssignment(vararg literals: Literal) = Assignment(SparseBitArray(problem.nbrVariables), index, scope, literals)
    fun toAssignment(instance: Instance) = Assignment(instance, index, scope)
    fun toAssignments(sequence: Sequence<Instance>) = sequence.map { toAssignment(it) }

    /**
     * Performs a breadth first search from the current scope and down, throwing an error in case of failure.
     */
    operator fun get(name: String): Variable<*, *> = scope[name]

    /**
     * Performs a breadth first search from the current scope and down, throwing an error in case of failure.
     * Then the [value] is extracted from the value.
     */
    operator fun <V> get(name: String, value: V) = scope[name, value]

    companion object {
        inline fun model(name: String = Variable.defaultName(), init: Builder.() -> Unit): Model {
            val builder = Builder(name)
            init.invoke(builder)
            return builder.build()
        }
    }

    class Builder private constructor(override val index: VariableIndex,
                                      override val scope: RootScope,
                                      override val constraints: MutableList<Constraint>,
                                      override val units: IntHashSet) : ModelBuilder<RootScope>(), Value by scope.reifiedValue {

        @JvmOverloads
        constructor(name: String = Variable.defaultName()) : this(VariableIndex(), RootScope(Root(name)), ArrayList(), IntHashSet())

        override fun toString() = "Builder($name)"
    }

    private class ChildBuilder<S : Scope>(val parent: ModelBuilder<*>, override val scope: S) : ModelBuilder<S>(), Value by scope.reifiedValue {
        override val index: VariableIndex get() = parent.index
        override val constraints: MutableList<Constraint> get() = parent.constraints
        override val units: IntHashSet get() = parent.units

        override fun toString() = "ChildBuilder($name)"
    }

    @ModelMarker
    abstract class ModelBuilder<S : Scope> : Value {

        abstract val index: VariableIndex
        abstract val scope: S
        abstract val constraints: MutableList<Constraint>
        abstract val units: IntHashSet

        fun build(): Model {
            val constraints = let {
                val problem = Problem(index.nbrVariables, constraints.toTypedArray())
                val reduced = problem.unitPropagation(units, true)
                if (units.size > 0) reduced + Conjunction(collectionOf(*units.toArray()))
                else reduced
            }
            return Model(Problem(index.nbrVariables, constraints), index, scope)
        }

        /**
         * Performs a breadth first search from the current scope and down, throwing an error in case of failure.
         */
        operator fun get(name: String): Variable<*, *> = scope.find(name)
                ?: throw NoSuchElementException("Scope contains no variable with name $name.")

        /**
         * Performs a breadth first search from the current scope and down, throwing an error in case of failure.
         * Then the [value] is extracted from the value.
         */
        operator fun <V> get(name: String, value: V) = scope.find<Variable<V, *>>(name)?.value(value)
                ?: throw NoSuchElementException("Scope contains no variable with name $name.")

        fun bool(name: String = Variable.defaultName()) = flag(name, true)

        fun <V> flag(name: String = Variable.defaultName(), value: V) =
                Flag(name, value).apply {
                    addVariable(this)
                }

        fun <V> optionalNominal(name: String = Variable.defaultName(), vararg values: V) =
                Nominal(name, null, *values).also {
                    addVariable(it)
                }

        fun <V> nominal(name: String = Variable.defaultName(), vararg values: V) =
                Nominal(name, scope.reifiedValue, *values).also {
                    addVariable(it)
                }

        fun <V> multiple(name: String = Variable.defaultName(), vararg values: V) =
                Multiple(name, scope.reifiedValue, *values).also {
                    addVariable(it)
                }

        fun <V> optionalMultiple(name: String = Variable.defaultName(), vararg values: V) =
                Multiple(name, null, *values).also {
                    addVariable(it)
                }

        fun optionalInt(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) =
                IntVar(name, null, min, max).also {
                    addVariable(it)
                }

        fun int(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) =
                IntVar(name, scope.reifiedValue, min, max).also {
                    addVariable(it)
                }

        fun optionalFloat(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32, max: Float = MAX_VALUE32) =
                FloatVar(name, null, min, max).also {
                    addVariable(it)
                }

        fun float(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32, max: Float = MAX_VALUE32) =
                FloatVar(name, scope.reifiedValue, min, max).also {
                    addVariable(it)
                }

        fun bits(name: String = Variable.defaultName(), nbrBits: Int) =
                BitsVar(name, scope.reifiedValue, nbrBits).also {
                    addVariable(it)
                }

        fun optionalBits(name: String = Variable.defaultName(), nbrBits: Int) =
                BitsVar(name, null, nbrBits).also {
                    addVariable(it)
                }

        /**
         * Adds an optional sub-model. Every variable in the new model will be controlled by a [bool] named [name]. The
         * scope name will also be [name].
         */
        fun model(name: String = Variable.defaultName(), init: ModelBuilder<ChildScope<S>>.() -> Unit) = model(bool(name), name, init)

        /**
         * A sub model allows organizing groups of variables in a logical hierarchy. Every variable 'v' under this will
         * have the constraint that v => reifiedValue. In this way, variables below this will only be true if the
         * reifiedValue is true.
         */
        @JsName("reifiedModel")
        fun model(reifiedValue: Value, scopeName: String = reifiedValue.name, init: ModelBuilder<ChildScope<S>>.() -> Unit): ModelBuilder<ChildScope<S>> {
            require(index.contains(reifiedValue.canonicalVariable)) { "${reifiedValue.canonicalVariable} not found in model." }
            @Suppress("UNCHECKED_CAST")
            val builder = ChildBuilder(this, scope.addScope(scopeName, reifiedValue) as ChildScope<S>)
            val parentValue = this@ModelBuilder.scope.reifiedValue
            if (!scope.inScope(reifiedValue.canonicalVariable.name) && parentValue !is Root)
                impose {
                    reifiedValue implies parentValue
                }
            init.invoke(builder)
            return builder
        }

        /**
         * Add a constraint through [ConstraintFactory]. Note that only one constraint can be added at a time.
         */
        inline fun impose(init: ConstraintFactory<S>.() -> Expression) {
            val builder = ConstraintFactory(scope, index)
            val exp = init.invoke(builder)
            addConstraint(exp)
        }

        fun addVariable(variable: Variable<*, *>) = apply {
            require(variable.nbrLiterals > 0)
            index.add(variable)
            scope.add(variable)
            val parentValue = this@ModelBuilder.scope.reifiedValue
            if (!variable.mandatory && parentValue !is Root)
                impose { variable implies parentValue }
            variable.implicitConstraints(scope, index).forEach {
                addConstraint(it)
            }
        }

        /**
         * Adds a separate [Model] as a child model to this. Constraints defined in the other model are not copied,
         * but implicit constraints are added again.
         */
        fun addModel(model: Model) = apply {
            val scopes = ArrayList<Scope>()
            val definedIn = ArrayList<ModelBuilder<*>>()
            scopes.add(model.scope)
            definedIn.add(this)
            while (scopes.isNotEmpty()) {
                val next = scopes.removeAt(0)
                val parent = definedIn.removeAt(0)
                val rv = if (next.reifiedValue !in index) {
                    when {
                        next.reifiedValue is Root -> parent.bool(next.reifiedValue.name)
                        next.reifiedValue !is Variable<*, *> -> error("Value ${next.reifiedValue} not contained in scope.")
                        else -> parent.addVariable(next.reifiedValue as Variable<*, *>)
                    }
                } else next.reifiedValue
                parent.model(rv, next.scopeName) {
                    next.scopeVariables.forEach { addVariable(it) }
                    next.children.forEach {
                        scopes.add(it)
                        definedIn.add(this)
                    }
                }
            }
        }

        /**
         * Add a constraint to the model.
         * @param unitPropagation perform unit propagation simplification based on the current unit literals in the
         * model.
         */
        fun addConstraint(expression: Expression, unitPropagation: Boolean = true) = apply {

            fun imposeConstraint(constraint: Constraint) {
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

            when (expression) {
                is Literal -> {
                    val set = IntHashSet()
                    expression.collectLiterals(index, set)
                    imposeConstraint(Conjunction(collectionOf(*set.toArray())))
                }
                is CNF -> expression.disjunctions.forEach { imposeConstraint(it) }
                is Constraint -> imposeConstraint(expression)
                else -> throw UnsupportedOperationException(
                        "Custom types of expression cannot be added as constraints in this way.")
            }
        }

        fun add() = SubBuilder(this)
    }

    /**
     * For java inter-op with fluent builder style.
     */
    class SubBuilder<S : Scope>(val builder: ModelBuilder<S>) {
        @JvmOverloads
        fun <V> flag(name: String = Variable.defaultName(), value: V) = builder.apply { flag(name, value) }

        @JvmOverloads
        fun bool(name: String = Variable.defaultName()) = builder.apply { bool(name) }

        @JvmOverloads
        fun <V> optionalNominal(name: String = Variable.defaultName(), vararg values: V) = builder.apply { optionalNominal(name, *values) }

        @JvmOverloads
        fun <V> nominal(name: String = Variable.defaultName(), vararg values: V) = builder.apply { nominal(name, *values) }

        @JvmOverloads
        fun <V> multiple(name: String = Variable.defaultName(), vararg values: V) = builder.apply { multiple(name, *values) }

        @JvmOverloads
        fun <V> optionalMultiple(name: String = Variable.defaultName(), vararg values: V) = builder.apply { optionalMultiple(name, *values) }

        @JvmOverloads
        fun optionalInt(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) = builder.apply { optionalInt(name, min, max) }

        @JvmOverloads
        fun int(name: String = Variable.defaultName(), min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) = builder.apply { int(name, min, max) }

        @JvmOverloads
        fun optionalFloat(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32, max: Float = MAX_VALUE32) = builder.apply { optionalFloat(name, min, max) }

        @JvmOverloads
        fun float(name: String = Variable.defaultName(), min: Float = -MAX_VALUE32, max: Float = MAX_VALUE32) = builder.apply { float(name, min, max) }

        @JvmOverloads
        fun bits(name: String = Variable.defaultName(), nbrBits: Int) = builder.apply { bits(name, nbrBits) }

        @JvmOverloads
        fun optionalBits(name: String = Variable.defaultName(), nbrBits: Int) = builder.apply { optionalBits(name, nbrBits) }

        fun disjunction(vararg references: String) =
                builder.apply { impose { disjunction(*references.mapArray { builder[it] }) } }

        fun conjunction(vararg references: String) =
                builder.apply { impose { conjunction(*references.mapArray { builder[it] }) } }

        fun linear(degree: Int, relation: Relation, weights: IntArray, vararg references: String) =
                builder.apply { impose { linear(degree, relation, weights, references.mapArray { builder[it] }) } }

        fun cardinality(degree: Int, relation: Relation, vararg references: String) =
                builder.apply { impose { cardinality(degree, relation, *references.mapArray { builder[it] }) } }

        fun exactly(degree: Int, vararg references: String) =
                builder.apply { impose { exactly(degree, *references.mapArray { builder[it] }) } }

        fun atMost(degree: Int, vararg references: String) =
                builder.apply { impose { atMost(degree, *references.mapArray { builder[it] }) } }

        fun atLeast(degree: Int, vararg references: String) =
                builder.apply { impose { atLeast(degree, *references.mapArray { builder[it] }) } }

        fun excludes(vararg references: String) =
                builder.apply { builder.impose { excludes(*references.mapArray { builder[it] }) } }
    }

    override fun toString() = "Model(${scope.scopeName})"
}

@DslMarker
annotation class ModelMarker
