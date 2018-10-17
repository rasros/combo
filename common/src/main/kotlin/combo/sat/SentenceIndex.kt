package combo.sat

class SentenceIndex(sentences: Array<out Sentence>, nbrVariables: Int) {

    private val varToSent: Array<IntArray> = Array(nbrVariables) { IntArray(2) }

    init {
        val sizes = IntArray(nbrVariables)
        for ((i, clause) in sentences.withIndex()) {
            for (lit in clause) {
                val id = lit.asIx()
                if (varToSent[id].size == sizes[id]) {
                    varToSent[id] = varToSent[id].copyOf(sizes[id] * 2)
                }
                varToSent[id][sizes[id]++] = i
            }
        }
        for (i in sizes.indices)
            varToSent[i] = varToSent[i].copyOf(sizes[i])
    }

    fun sentencesWith(ix: Ix) = varToSent[ix]
}
