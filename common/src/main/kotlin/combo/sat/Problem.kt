package combo.sat

import combo.util.IntList
import combo.util.IntSet

class Problem(val sentences: Array<out Sentence>, val nbrVariables: Int) {

    val nbrSentences get() = sentences.size

    private val variableSentences: Array<IntArray> = Array(nbrVariables) { IntList() }.let { lists ->
        for ((i, sent) in sentences.withIndex()) {
            for (lit in sent) {
                val ix = lit.asIx()
                lists[ix].add(i)
            }
        }
        Array(nbrVariables) { lists[it].toArray() }
    }

    fun sentencesWith(varIx: Ix) = variableSentences[varIx]

    fun satisfies(l: Labeling) = sentences.all { it.satisfies(l) }

    fun flipsToSatisfy(l: Labeling) = sentences.sumBy { it.flipsToSatisfy(l) }

    fun unitPropagation(units: IntSet = IntSet()): Array<Sentence> {

        fun addUnit(units: IntSet, unit: Literal): Boolean {
            if (units.contains(!unit)) throw UnsatisfiableException("Unsatisfiable by unit propagation.", literal = unit)
            else return units.add(unit)
        }

        val unitsIx = ArrayList<Int>()
        if (units.isNotEmpty())
            unitsIx.add(sentences.size + 1)

        val initial = Conjunction(units.copy())
        for (i in sentences.indices)
            if (sentences[i].isUnit()) {
                unitsIx.add(i)
                for (l in sentences[i]) addUnit(units, l)
            }

        val copy = Array<Sentence?>(sentences.size) { null }

        while (!unitsIx.isEmpty()) {
            val clauseId = unitsIx.removeAt(0)
            val clause = if (clauseId >= sentences.size) initial else copy[clauseId] ?: sentences[clauseId]
            for (unitLit in clause.literals) {
                val unitId = unitLit.asIx()
                val matching = sentencesWith(unitId)
                for (i in matching.indices) {
                    val reduced = (copy[matching[i]] ?: sentences[matching[i]]).propagateUnit(unitLit)
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.literals.any { l -> addUnit(units, l) }) unitsIx.add(matching[i])
                }
            }
        }
        return copy.asSequence()
                .mapIndexed { i, it -> it ?: sentences[i] }
                .filter { !it.isUnit() && it != Tautology }
                .toList()
                .toTypedArray()
    }

    fun simplify(units: IntSet = IntSet(), addConjunction: Boolean = false): Problem {
        var reduced = unitPropagation(units)
        if (addConjunction && units.isNotEmpty()) reduced += Conjunction(units)
        return Problem(reduced, nbrVariables)
    }
}

