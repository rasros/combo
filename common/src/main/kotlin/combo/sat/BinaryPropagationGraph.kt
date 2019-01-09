package combo.sat

import combo.util.IntSet

// TODO move to PropSearchStateFactory
class BinaryPropagationGraph(problem: Problem) {

    val literalPropagations: Array<IntArray>
    private val literalSentences: Array<IntArray> // TODO remove, use variableSentences instead
    val variableSentences: Array<IntArray>
    val complexSentences = IntSet()

    init {
        val implicationSets = Array(problem.nbrVariables * 2) { IntSet(2) }

        for ((sentId, sent) in problem.sentences.withIndex()) {
            if (sent is Disjunction && sent.size == 2) {
                val array = sent.literals.toArray()
                implicationSets[!array[0]].add(array[1])
                implicationSets[!array[1]].add(array[0])
            } else if (sent is Cardinality && sent.degree == 1 && sent.operator == Cardinality.Operator.AT_MOST) {
                val array = sent.literals.toArray()
                for (i in array.indices) {
                    for (j in (i + 1) until array.size) {
                        implicationSets[array[i]].add(!array[j])
                        implicationSets[array[j]].add(!array[i])
                    }
                }
            }
            /*else if (sent is Reified) {
                complexSentences.add(sentId) // TODO investigate cases where not necessary
                if (sent.clause is Disjunction) {
                    for (clauseLit in sent.clause.literals) {
                        implicationSets[!sent.literal].add(!clauseLit)
                        implicationSets[clauseLit].add(sent.literal)
                    }
                } else if (sent.clause is Conjunction) {
                // TODO at least this one
                    for (clauseLit in sent.clause.literals) {
                        implicationSets[sent.literal].add(clauseLit)
                        implicationSets[clauseLit].add(sent.literal)
                        implicationSets[!clauseLit].add(!sent.literal)
                        implicationSets[!sent.literal].add(!clauseLit)
                    }
                }
            }*/ else complexSentences.add(sentId)
        }

        // Adds transitive implications like: a -> b -> c
        // Ie. transitive closure
        val inverseGraph = Array(problem.nbrVariables * 2) { IntSet(2) }
        for (i in 0 until problem.nbrVariables * 2) {
            for (j in implicationSets[i]) inverseGraph[j].add(i)
        }

        val queue = IntSet().apply {
            addAll(0 until problem.nbrVariables * 2)
        }

        while (queue.isNotEmpty()) {
            val lit = queue.first()
            var dirty = false
            for (i in implicationSets[lit].toArray()) {
                if (implicationSets[lit].addAll(implicationSets[i])) {
                    dirty = true
                    queue.addAll(inverseGraph[lit])
                    for (j in implicationSets[lit]) inverseGraph[j].add(lit)
                    inverseGraph[i].addAll(implicationSets[lit])
                }
            }
            if (!dirty) queue.remove(lit)
        }

        for (i in 0 until problem.nbrVariables)
            if (i in implicationSets[i])
                throw UnsatisfiableException("Unsatisfiable by krom 2-sat.", literal = i)

        literalPropagations = Array(problem.nbrVariables * 2) { i ->
            implicationSets[i].toArray()
        }

        literalSentences = Array(problem.nbrVariables * 2) { i ->
            val sentences = IntSet()
            for (sentId in problem.sentencesWith(i.asIx()))
                if (sentId in complexSentences) sentences.add(sentId)
            //sentences.addAll(problem.sentencesWith(i.asIx()))
            literalPropagations[i].forEach { j ->
                for (sentId in problem.sentencesWith(j.asIx()))
                    if (sentId in complexSentences) sentences.add(sentId)
                //sentences.addAll(problem.sentencesWith(j.asIx()))
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
