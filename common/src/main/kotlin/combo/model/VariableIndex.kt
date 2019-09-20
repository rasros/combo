package combo.model

import combo.util.ArrayQueue
import combo.util.assert

/**
 * This can be used to query the variables and child scopes defined as part of the model.
 * The [find] method searches for variables and [getChildScope] can be used to get a sub scope.
 */
class VariableIndex private constructor(val scopeName: String,
                                        val reifiedValue: Value,
                                        val parent: VariableIndex?,
                                        private val index: MutableMap<Variable<*>, Int>,
                                        private val variableCounter: Counter) : Iterable<Variable<*>> {

    constructor(reifiedValue: Value) : this(reifiedValue.name, reifiedValue, null, HashMap(), Counter())

    /**
     * All direct child scopes, see also [getChildScope].
     */
    val children: List<VariableIndex> = ArrayList()

    /**
     * All sequence in the current scope only.
     */
    val scopeVariables: List<Variable<*>> = ArrayList()

    fun variables(): Sequence<Variable<*>> = index.keys.asSequence()

    private val names = HashMap<String, Variable<*>>()

    val nbrVariables: Int get() = variableCounter.count
    val isRoot: Boolean get() = parent == null

    /**
     * All sequence in the current scope and all scopes below this, including all child sequence. The [Sequence] is
     * iterated in depth-first order using a stack.
     */
    fun asSequence(): Sequence<Variable<*>> {
        val stack = ArrayList<VariableIndex>()

        var i = 0
        var current: VariableIndex = this

        run {
            val currentVars = current.scopeVariables
            while (i < currentVars.size && currentVars[i].nbrLiterals <= 0)
                i++
            stack.addAll(this.children)
        }

        return generateSequence {
            var scopeVars = current.scopeVariables
            while (i < scopeVars.size && scopeVars[i].nbrLiterals <= 0)
                i++

            while (i >= current.scopeVariables.size && stack.isNotEmpty()) {
                current = stack.removeAt(stack.lastIndex)
                stack.addAll(current.children)
                i = 0
                scopeVars = current.scopeVariables
                // Advance over empty variables (unit and reference)
                while (i < scopeVars.size && scopeVars[i].nbrLiterals <= 0)
                    i++
            }

            // Return next variable or terminate with null
            scopeVars = current.scopeVariables
            if (i < scopeVars.size) scopeVars[i++]
            else null
        }
    }

    override fun iterator() = asSequence().iterator()

    fun add(variable: Variable<*>): Int {
        assert(variable !is Root)
        if (index.containsKey(variable))
            throw IllegalArgumentException("Variable $variable already added.")
        if (names.containsKey(variable.name))
            throw IllegalArgumentException("Variable with name ${variable.name} already exists in scope $scopeName.")
        index[variable] = variableCounter.count
        names[variable.name] = variable
        (scopeVariables as MutableList).add(variable)
        variableCounter.count += variable.nbrLiterals
        return variableCounter.count
    }

    fun addChildScope(scopeName: String, reifiedValue: Value) = VariableIndex(scopeName, reifiedValue, this, index, variableCounter).also {
        (children as MutableList).add(it)
    }

    fun indexOf(variable: Variable<*>): Int =
            if (variable is Root) throw IllegalArgumentException("Root variable cannot be referenced.")
            else index[variable] ?: throw NoSuchElementException("Variable $variable not found.")

    operator fun contains(variable: Variable<*>): Boolean = index.containsKey(variable)
    operator fun contains(reference: String) = find<Variable<*>>(reference) != null

    fun getChildScope(scopeName: String): VariableIndex = children.find { it.scopeName == scopeName }
            ?: throw NoSuchElementException("Scope with name $scopeName not found among children of ${this.scopeName}.")

    /**
     * Resolve the [reference] in the current context, moving upwards in the symbol tree hierarchy if a match is not
     * found. This function is intended for internal use through [ConstraintBuilder], which is why it throws an
     * exception on failure.
     */
    fun resolve(reference: String): Variable<*> {
        var p: VariableIndex? = this
        while (p != null) {
            val r = p.names[reference]
            if (r != null) return r
            p = p.parent
        }
        throw NoSuchElementException("VariableIndex contains no variable in scope with name $reference.")
    }

    fun inScope(reference: String): Boolean {
        var p: VariableIndex? = this
        while (p != null) {
            val r = p.names[reference]
            if (r != null) return true
            p = p.parent
        }
        return false
    }

    operator fun get(reference: String): Variable<*> = find(reference)
            ?: throw NoSuchElementException("VariableIndex contains no variable with name $reference.")

    /**
     * Performs a breadth-first search for resolving a variable. This is suitable to use at the root of the tree
     * hierarchy to find the closest child variable.
     */
    @Suppress("UNCHECKED_CAST")
    fun <V : Variable<*>> find(reference: String): V? {
        run {
            val v = names[reference]
            if (v != null) return v as V
        }

        val queue = ArrayQueue<VariableIndex>()
        queue.add(this)

        while (queue.size > 0) {
            val vi = queue.remove()
            val v = vi.names[reference]
            if (v != null) return v as V
            else queue.addAll(vi.children)
        }

        return null
    }

    override fun toString() = "VariableIndex($scopeName)"

    private class Counter {
        var count: Int = 0
        override fun toString() = "$count"
    }
}