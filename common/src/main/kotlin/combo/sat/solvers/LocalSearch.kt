package combo.sat.solvers

import combo.sat.Sentence

abstract class LocalSearch {

    abstract val config: SolverConfig

    var totalSuccesses: Long = 0
        private set
    var totalEvaluated: Long = 0
        private set
    var totalIterations: Long = 0
        private set
    var totalFlips: Long = 0
        private set

    var latestSequenceLiterals: MutableList<Int>? = null
    var latestSequenceSentence: MutableList<Sentence>? = null

    protected fun recordCompleted(satisfied: Boolean) {
        if (satisfied) totalSuccesses++
        totalEvaluated++
    }

    protected fun recordIteration() {
        if (config.debugMode) {
            latestSequenceSentence = ArrayList()
            latestSequenceLiterals = ArrayList()
        }
        totalIterations++
    }

    protected fun recordFlip(sentence: Sentence, literal: Int) {
        if (config.debugMode) {
            latestSequenceSentence!!.add(sentence)
            latestSequenceLiterals!!.add(literal)
        }
        totalFlips++
    }
}

