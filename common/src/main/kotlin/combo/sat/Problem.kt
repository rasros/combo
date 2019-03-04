package combo.sat

import combo.util.*

/**
 * This class sufficiently describes a SAT problem, with the constraints and a count of the number of variables. It also
 * holds an index of variable to constraints in [constraintsWith].
 */
class Problem(val constraints: Array<out Constraint>, val nbrVariables: Int) {

    val nbrConstraints get() = constraints.size

    private val variableConstraints: IntHashMap<IntArray> = let {
        val map = IntHashMap<IntList>(nullKey = -1)
        for ((i, cons) in constraints.withIndex()) {
            for (lit in cons) {
                val ix = lit.toIx()
                assert(ix < nbrVariables)
                if (!map.containsKey(ix)) map[ix] = IntList()
                map[ix]!!.add(i)
            }
        }
        map.map { it.toArray() }
    }

    /**
     * Returns the index into the [constraints] array of all constraints with the given variable.
     */
    fun constraintsWith(varIx: Int) = variableConstraints[varIx] ?: EMPTY_INT_ARRAY

    fun satisfies(l: Instance) = constraints.all { it.satisfies(l) }

    fun flipsToSatisfy(l: Instance) = constraints.sumBy { it.flipsToSatisfy(l) }

    /**
     * Performs unit propagation on all constraints. Additional unit variables can be added in the [units] parameter.
     * New units will be added to the set. This method does not change the original problem but returns a reduced
     * problem.
     */
    fun unitPropagation(units: IntHashSet = IntHashSet(), returnConstraints: Boolean = false): Array<Constraint> {

        fun addUnit(units: IntHashSet, unit: Literal): Boolean {
            if (units.contains(!unit)) throw UnsatisfiableException("Unsatisfiable by unit propagation.", literal = unit)
            else return units.add(unit)
        }

        val unitsIx = ArrayList<Int>()
        if (units.isNotEmpty())
            unitsIx.add(constraints.size + 1)

        val initial = Conjunction(units.copy())
        for (i in constraints.indices)
            if (constraints[i].isUnit()) {
                unitsIx.add(i)
                for (l in constraints[i]) addUnit(units, l)
            }

        val copy = Array<Constraint?>(constraints.size) { null }

        while (!unitsIx.isEmpty()) {
            val clauseId = unitsIx.removeAt(0)
            val clause = if (clauseId >= constraints.size) initial else copy[clauseId] ?: constraints[clauseId]
            for (unitLit in clause.literals) {
                val unitId = unitLit.toIx()
                val matching = constraintsWith(unitId)
                for (i in matching.indices) {
                    val reduced = (copy[matching[i]] ?: constraints[matching[i]]).unitPropagation(unitLit)
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.literals.any { l -> addUnit(units, l) }) unitsIx.add(matching[i])
                }
            }
        }
        return if (returnConstraints) {
            copy.asSequence()
                    .mapIndexed { i, it -> it ?: constraints[i] }
                    .filter { !it.isUnit() && it != Tautology }
                    .toMutableList().apply { add(Conjunction(collectionOf(*units.toArray()))) }
                    .toTypedArray()
        } else emptyArray()
    }
}


