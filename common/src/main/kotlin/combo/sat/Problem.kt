package combo.sat

import combo.util.IntList
import combo.util.IntSet

/**
 * This class sufficiently describes a SAT problem, with the constraints and a count of the number of variables. It also
 * holds an index of variable to constraints in [sentencesWith].
 */
class Problem(val constraints: Array<out Constraint>, val nbrVariables: Int) {

    val nbrSentences get() = constraints.size

    private val variableSentences: Array<IntArray> = Array(nbrVariables) { IntList() }.let { lists ->
        for ((i, sent) in constraints.withIndex()) {
            for (lit in sent) {
                val ix = lit.toIx()
                lists[ix].add(i)
            }
        }
        Array(nbrVariables) { lists[it].toArray() }
    }

    /**
     * Returns the index into the [constraints] array of all constraints with the given variable.
     */
    fun sentencesWith(varIx: Ix) = variableSentences[varIx]

    fun satisfies(l: Labeling) = constraints.all { it.satisfies(l) }

    fun flipsToSatisfy(l: Labeling) = constraints.sumBy { it.flipsToSatisfy(l) }

    /**
     * Performs unit propagation on all constraints. Additional unit variables can be added in the [units] parameter.
     * New units will be added to the set. This method does not change the original problem but returns a reduced
     * problem.
     */
    fun unitPropagation(units: IntSet = IntSet()): Array<Constraint> {

        fun addUnit(units: IntSet, unit: Literal): Boolean {
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
                val matching = sentencesWith(unitId)
                for (i in matching.indices) {
                    val reduced = (copy[matching[i]] ?: constraints[matching[i]]).propagateUnit(unitLit)
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.literals.any { l -> addUnit(units, l) }) unitsIx.add(matching[i])
                }
            }
        }
        return copy.asSequence()
                .mapIndexed { i, it -> it ?: constraints[i] }
                .filter { !it.isUnit() && it != Tautology }
                .toList()
                .toTypedArray()
    }
}

