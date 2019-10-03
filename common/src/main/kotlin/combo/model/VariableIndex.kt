package combo.model

import combo.util.assert

/**
 * Contains mappings from variable to the index they have in optimization problems.
 * Variables can be iterated in declaration order.
 */
class VariableIndex : Iterable<Variable<*, *>> {

    private val index: MutableMap<Variable<*, *>, Int> = LinkedHashMap()
    private val variables = ArrayList<Variable<*, *>>()

    var nbrLiterals: Int = 0
        private set

    val nbrVariables: Int get() = index.size

    fun add(variable: Variable<*, *>): Int {
        assert(variable !is Root)
        require(!index.containsKey(variable)) { "Variable $variable already added." }
        index[variable] = nbrLiterals
        variables.add(variable)
        nbrLiterals += variable.nbrValues
        return nbrLiterals
    }

    fun variable(variableIndex: Int) = variables[variableIndex]

    fun variableWithValue(valueIndex: Int): Variable<*, *> {
        TODO("Implement with binary search")
    }

    fun indexOf(variable: Variable<*, *>): Int =
            if (variable is Root) throw IllegalArgumentException("Root variable cannot be referenced.")
            else index[variable] ?: throw NoSuchElementException("Variable $variable not found.")

    operator fun contains(variable: Variable<*, *>): Boolean = index.containsKey(variable)

    override fun iterator(): Iterator<Variable<*, *>> = index.keys.iterator()
}