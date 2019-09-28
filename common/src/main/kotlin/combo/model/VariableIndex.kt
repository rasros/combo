package combo.model

import combo.util.assert

/**
 * Contains mappings from variable to the index they have in optimization problems.
 * Variables can be iterated in declaration order.
 */
class VariableIndex : Iterable<Variable<*,*>> {

    private val index: MutableMap<Variable<*, *>, Int> = LinkedHashMap()

    var nbrVariables: Int = 0
        private set

    fun add(variable: Variable<*, *>): Int {
        assert(variable !is Root)
        if (index.containsKey(variable))
            throw IllegalArgumentException("Variable $variable already added.")
        index[variable] = nbrVariables
        nbrVariables += variable.nbrLiterals
        return nbrVariables
    }

    fun indexOf(variable: Variable<*, *>): Int =
            if (variable is Root) throw IllegalArgumentException("Root variable cannot be referenced.")
            else index[variable] ?: throw NoSuchElementException("Variable $variable not found.")

    operator fun contains(variable: Variable<*, *>): Boolean = index.containsKey(variable)

    override fun iterator(): Iterator<Variable<*, *>>  = index.keys.iterator()
}