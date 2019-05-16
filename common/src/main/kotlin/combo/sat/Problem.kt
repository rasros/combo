package combo.sat

import combo.sat.constraints.Conjunction
import combo.util.*

/**
 * This class sufficiently describes a SAT problem, with the constraints and a count of the number of variables. It also
 * holds an index of variable to constraints in [constraintsWith].
 */
class Problem(val constraints: Array<out Constraint>, val nbrVariables: Int) {

    val nbrConstraints get() = constraints.size

    private val variableConstraints: Map<Int, IntArray> = let {
        val map = HashMap<Int, IntList>()
        for ((i, cons) in constraints.withIndex()) {
            for (lit in cons.literals) {
                val ix = lit.toIx()
                assert(ix < nbrVariables)
                if (!map.containsKey(ix)) map[ix] = IntList()
                map[ix]!!.add(i)
            }
        }
        // TODO makes linkedhashmap unnecessarily
        map.mapValues { it.value.toArray() }
    }

    /**
     * Returns the index into the [constraints] array of all constraints with the given variable.
     */
    fun constraintsWith(varIx: Int) = variableConstraints[varIx] ?: EMPTY_INT_ARRAY

    fun satisfies(instance: Instance) = constraints.all { it.satisfies(instance) }

    fun violations(instance: Instance) = constraints.sumBy { it.violations(instance) }

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

        val initial: Constraint = if (units.isEmpty()) Empty else Conjunction(units.copy())
        for (i in constraints.indices)
            if (constraints[i].isUnit()) {
                unitsIx.add(i)
                for (l in constraints[i].unitLiterals()) addUnit(units, l)
            }

        val copy = Array<Constraint?>(constraints.size) { null }

        while (!unitsIx.isEmpty()) {
            val constraintId = unitsIx.removeAt(0)
            val constraint = if (constraintId >= constraints.size) initial else copy[constraintId]
                    ?: constraints[constraintId]
            for (unitLit in constraint.unitLiterals()) {
                val unitId = unitLit.toIx()
                val matching = constraintsWith(unitId)
                for (i in matching.indices) {
                    val reduced = (copy[matching[i]] ?: constraints[matching[i]]).unitPropagation(unitLit)
                    if (reduced is Empty) throw UnsatisfiableException("Unsatisfiable by unit propagation.")
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.unitLiterals().any { l -> addUnit(units, l) }) unitsIx.add(matching[i])
                }
            }
        }
        return if (returnConstraints) {
            copy.asSequence()
                    .mapIndexed { i, it -> it ?: constraints[i] }
                    .filter { !it.isUnit() && it != Tautology }
                    .toList()
                    .toTypedArray()
        } else emptyArray()
    }
}


