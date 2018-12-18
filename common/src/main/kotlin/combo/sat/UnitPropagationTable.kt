package combo.sat

import combo.util.IntSet

class UnitPropagationTable(problem: Problem, exactPropagation: Boolean = false) {

    val literalPropagations: Array<IntArray>
    val literalSentences: Array<IntArray>
    val variableSentences: Array<IntArray>

    init {
        if (!exactPropagation) {
            val implicationSets = Array(problem.nbrVariables * 2) { IntSet() }

            for (sent in problem.sentences) {
                if (sent is Disjunction && sent.size == 2) {
                    implicationSets[!sent.literals[0]].add(sent.literals[1])
                    implicationSets[!sent.literals[1]].add(sent.literals[0])
                } else if (sent is Cardinality && sent.degree == 1 &&
                        (sent.operator == Cardinality.Operator.AT_MOST || sent.operator == Cardinality.Operator.EXACTLY)) {
                    for (i in sent.literals.indices) {
                        for (j in (i + 1) until sent.literals.size) {
                            implicationSets[sent.literals[i]].add(!sent.literals[j])
                            implicationSets[sent.literals[j]].add(!sent.literals[i])
                        }
                    }
                } else if (sent is Reified) {
                    if (sent.clause is Disjunction) {
                        for (clauseLit in sent.clause.literals) {
                            implicationSets[!sent.literal].add(!clauseLit)
                            implicationSets[clauseLit].add(sent.literal)
                        }
                    } else if (sent.clause is Conjunction) {
                        for (clauseLit in sent.clause.literals) {
                            implicationSets[sent.literal].add(clauseLit)
                            implicationSets[clauseLit].add(sent.literal)
                            implicationSets[!clauseLit].add(!sent.literal)
                            implicationSets[!sent.literal].add(!clauseLit)
                        }
                    }
                }
            }

            //for (imps in implicationSets)
                //for (ilit in imps.toArray()) // toArray to avoid concurrent modification
                    //imps.addAll(implicationSets[ilit]) // TODO efficient propagate looping

            literalPropagations = Array(problem.nbrVariables * 2) { i ->
                implicationSets[i].toArray()
            }
        } else {
            literalPropagations = Array(problem.nbrVariables * 2) {
                val set = IntSet()
                set.add(it)
                try {
                    problem.unitPropagation(set)
                } catch (ignored: UnsatisfiableException) {
                }
                set.remove(it)
                set.toArray()
            }
        }

        literalSentences = Array(problem.nbrVariables * 2) { i ->
            val sentences = IntSet()
            sentences.addAll(problem.sentencesWith(i.asIx()))
            literalPropagations[i].forEach { j ->
                sentences.addAll(problem.sentencesWith(j.asIx()))
            }
            sentences.toArray()
        }

        variableSentences = Array(problem.nbrVariables) { i ->
            val sentences = IntSet()
            sentences.addAll(literalSentences[i.asLiteral(true)])
            sentences.addAll(literalSentences[i.asLiteral(false)])
            sentences.toArray()
        }
    }
}
