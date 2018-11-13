package combo.sat

import combo.model.UnsatisfiableException
import combo.util.HashIntSet
import combo.util.IntSet

class Problem(val sentences: Array<out Sentence>, val nbrVariables: Int) {

    val nbrSentences get() = sentences.size

    val varToSent: Array<IntArray> = Array(nbrVariables) { IntArray(2) }.apply {
        val sizes = IntArray(nbrVariables)
        for ((i, clause) in sentences.withIndex()) {
            for (lit in clause) {
                val id = lit.asIx()
                if (this[id].size == sizes[id]) {
                    this[id] = this[id].copyOf(sizes[id] * 2)
                }
                this[id][sizes[id]++] = i
            }
        }
        for (i in sizes.indices)
            this[i] = this[i].copyOf(sizes[i])
    }

    fun sentencesWith(varIx: Ix) = varToSent[varIx]

    fun satisfies(l: Labeling, s: Labeling? = null) = sentences.all { it.satisfies(l, s) }

    fun unitPropagation(units: IntSet = HashIntSet()): Array<Sentence> {

        fun addUnit(units: IntSet, unit: Literal): Boolean {
            if (units.contains(!unit)) throw UnsatisfiableException("Unsatisfiable by unit propagation.", literal = unit)
            else return units.add(unit)
        }

        val unitsIx = ArrayList<Int>()
        if (units.isNotEmpty())
            unitsIx.add(sentences.size + 1)

        val initial = Conjunction(units.toArray().apply { sort() })
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

    fun simplify(units: IntSet = HashIntSet(), addConjunction: Boolean = false): Problem {
        var reduced = unitPropagation(units)
        if (addConjunction && units.isNotEmpty()) reduced += Conjunction(units.toArray().apply { sort() })
        return Problem(reduced, nbrVariables)
    }
}

