package combo.sat

import combo.ga.LabelingInitializer
import combo.ga.LookaheadInitializer
import combo.math.IntPermutation
import combo.math.Rng
import combo.model.IterationsReachedException
import combo.model.TimeoutException
import combo.util.IndexSet
import combo.util.millis
import kotlin.math.max
import kotlin.math.min

/*
 * WalkSat first picks a clause which is unsatisfied by the current labeling, then flips a variable within that
 * clause. The clause is picked at random among unsatisfied clauses. The variable is picked that will result in the
 * fewest previously satisfied clauses becoming unsatisfied, with some probability of picking one of the variables at
 * random.
 */
class WalkSat(val problem: Problem,
              override val config: SolverConfig = SolverConfig(),
              val init: LabelingInitializer = LookaheadInitializer(problem),
              val timeout: Long = -1L,
              val probRandomWalk: Double = 0.05,
              val maxRestarts: Int = Int.MAX_VALUE,
              val maxFlips: Int = 400,
              val maxConsideration: Int = 32) : Solver {

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        for (i in 1..maxRestarts) {
            recordIteration()
            val rng = config.nextRng()
            val p = if (contextLiterals.isNotEmpty())
                problem.unitPropagation(IndexSet().apply { addAll(contextLiterals) }, true)
            else problem
            val labeling = init.generate(p, config.labelingBuilder, rng)
            val result = satIteration(p, labeling, rng, end)
            if (result != null) {
                recordWitness(true)
                return result.apply { pack() }
            }
            if (millis() > end) throw TimeoutException(timeout)

        }
        recordWitness(false)
        throw IterationsReachedException(maxRestarts)
    }

    private fun satIteration(problem: Problem, labeling: MutableLabeling, rng: Rng, end: Long): MutableLabeling? {
        val unsatisfied = IndexSet(max(16, problem.sentences.size / 8))
        for ((i, s) in problem.sentences.withIndex()) {
            if (!s.satisfies(labeling)) {
                unsatisfied.add(i)
            }
        } // TODO initialize lazily in case everything is satisfied
        val improvement = IntArray(maxConsideration)

        for (flips in 1..maxFlips) {
            if (unsatisfied.isEmpty()) {
                return labeling
            }

            // Pick random clause
            val pickedSentenceIx = unsatisfied.random(rng)
            val pickedSentence = problem.sentences[pickedSentenceIx]
            val literals = pickedSentence.literals

            val id = if (probRandomWalk > rng.double()) {
                // With configured probability, pick randomly within the clause
                literals[rng.int(literals.size)].asIx()
            } else {
                // Otherwise pick the literal in the clause with the highest improvement
                val litIx = chooseBest(problem, literals, unsatisfied, labeling, improvement, rng)
                if (litIx >= 0) literals[litIx].asIx()
                else continue
            }
            val lit = !labeling.asLiteral(id)
            problem.set(labeling, lit, unsatisfied)
            problem.implicationGraph[lit].forEach {
                problem.set(labeling, it, unsatisfied)
            }
            recordFlip(pickedSentence, labeling.asLiteral(id))
            if (millis() > end) return null
        }
        return null
    }

    private fun Problem.set(l: MutableLabeling, lit: Literal, unsatisfied: IndexSet) {
        l.set(lit)
        for (sentenceIx in index.sentencesWith(lit.asIx())) {
            val sentence = sentences[sentenceIx]
            if (sentence.satisfies(l)) unsatisfied.remove(sentenceIx)
            else unsatisfied.add(sentenceIx)
        }
    }

    private fun chooseBest(problem: Problem,
                           literals: Literals,
                           unsatisfied: IndexSet,
                           labeling: MutableLabeling,
                           improvement: IntArray,
                           rng: Rng): Int {
        val perm = if (literals.size >= improvement.size) IntPermutation(literals.size, rng) else null
        for (i in 0 until min(improvement.size, literals.size)) {
            improvement[i] = 0
            val ix = perm?.encode(i) ?: i
            val id = literals[ix].asIx()
            val affected = problem.index.sentencesWith(id)
            for (sentenceIx in affected) {
                if (sentenceIx in unsatisfied) {
                    val sent = problem.sentences[sentenceIx]
                    val preFlip = sent.flipsToSatisfy(labeling)
                    labeling.flip(id)
                    val postFlip = sent.flipsToSatisfy(labeling)
                    improvement[i] += preFlip - postFlip
                    labeling.flip(id)
                }
            }
        }
        val imprIx = getMaxIx(improvement, min(literals.size, improvement.size), rng)
        val litIx = perm?.encode(imprIx) ?: imprIx
        return if (improvement[imprIx] < 0) -1
        else litIx
    }

    private fun getMaxIx(counts: IntArray, size: Int, rng: Rng): Int {
        // TODO simplify this using IntPermutation
        var max = Int.MIN_VALUE
        var nbrMax = 0
        for (i in 0 until size) {
            val c = counts[i]
            if (c > max) {
                max = c
                nbrMax = 1
            } else if (max == c) nbrMax++
        }
        val maxIx = rng.int(nbrMax)
        nbrMax = 0
        for (i in 0 until size) {
            val c = counts[i]
            if (c == max && nbrMax++ == maxIx)
                return i
        }
        return 0
    }

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

    private fun recordWitness(satisfied: Boolean) {
        if (satisfied) totalSuccesses++
        totalEvaluated++
    }

    private fun recordIteration() {
        if (config.debugMode) {
            latestSequenceSentence = ArrayList()
            latestSequenceLiterals = ArrayList()
        }
        totalIterations++
    }

    private fun recordFlip(sentence: Sentence, literal: Int) {
        if (config.debugMode) {
            latestSequenceSentence!!.add(sentence)
            latestSequenceLiterals!!.add(literal)
        }
        totalFlips++
    }

}


