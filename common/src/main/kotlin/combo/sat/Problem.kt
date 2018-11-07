package combo.sat

import combo.model.UnsatisfiableException
import combo.model.ValidationException
import combo.util.HashIntSet
import combo.util.IntSet
import combo.util.SortedArrayIntSet

class Problem(val sentences: Array<out Sentence>, val nbrVariables: Int) {

    /**
     * This is actually an inverted implication graph.
     * [value] is id of variable
     */
    data class Tree(override val value: Int, override val children: List<Tree> = emptyList())
        : combo.util.Tree<Int, Tree>

    val index: SentenceIndex = SentenceIndex(sentences, nbrVariables)

    val implicationGraph = let { _ ->
        val preImplications = Array(nbrVariables * 2) { SortedArrayIntSet(16) }

        val remaining = HashIntSet()
        for ((sentId, sent) in sentences.withIndex()) {
            if (sent is Disjunction && sent.size == 2) {
                preImplications[!sent.literals[0]].add(sent.literals[1])
                preImplications[!sent.literals[1]].add(sent.literals[0])
            } else if (sent is Cardinality && sent.degree == 1 &&
                    (sent.operator == Cardinality.Operator.AT_MOST || sent.operator == Cardinality.Operator.EXACTLY)) {
                for (i in sent.literals.indices) {
                    for (j in (i + 1) until sent.literals.size) {
                        preImplications[sent.literals[i]].add(!sent.literals[j])
                        preImplications[sent.literals[j]].add(!sent.literals[i])
                    }
                }
            } else if (sent is Reified) {
                if (sent.clause is Disjunction) {
                    for (clauseLit in sent.clause.literals)
                        preImplications[!sent.literal].add(!clauseLit)
                } else if (sent.clause is Conjunction) {
                    for (clauseLit in sent.clause.literals)
                        preImplications[sent.literal].add(clauseLit)
                }
                remaining.add(sentId)
            } else {
                remaining.add(sentId)
            }
        }
        Array(nbrVariables * 2) { i -> preImplications[i].toArray() }
    }

    val nbrSentences get() = sentences.size

    private fun addUnit(units: IntSet, unit: Literal): Boolean {
        if (units.contains(!unit)) throw UnsatisfiableException("Unsatisfiable by unit propagation.", literal = unit)
        else return units.add(unit)
    }

    fun satisfies(l: Labeling, s: Labeling? = null) = sentences.all { it.satisfies(l, s) }

    fun unitPropagation(units: IntSet = HashIntSet(), addConjunction: Boolean = false): Problem {
        for (l in units.toArray()) {
            if (units.contains(!l)) throw UnsatisfiableException(
                    "Unsatisfiable before unit propagation.", literal = l)
            if (l.asIx() > nbrVariables) throw ValidationException(
                    "Unregistered literal $l, expected $nbrVariables variables.")
        }

        val unitsIx = ArrayList<Int>()
        if (units.isNotEmpty()) {
            unitsIx.add(sentences.size + 1)
        }
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
                val matching = index.sentencesWith(unitId)
                for (i in matching.indices) {
                    val reduced = (copy[matching[i]] ?: sentences[matching[i]]).propagateUnit(unitLit)
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.literals.any { l -> addUnit(units, l) }) unitsIx.add(matching[i])
                }
            }
        }

        var reduced = copy
                .asSequence()
                .mapIndexed { i, it -> it ?: sentences[i] }
                .filter { !it.isUnit() && it != Tautology }
                .toList()
                .toTypedArray()
        if (addConjunction && units.isNotEmpty()) reduced += Conjunction(units.toArray().apply { sort() })
        return Problem(reduced, nbrVariables)
    }
}

