package combo.sat

import combo.math.IntPermutation
import combo.model.IterationsReachedException
import combo.model.TimeoutException
import combo.util.HashIntSet
import combo.util.IntSet
import combo.util.millis
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
 * WalkSat first picks a clause which is unsatisfied by the current labeling, then flips a variable within that
 * clause. The clause is picked at random among unsatisfied clauses. The variable is picked that will result in the
 * fewest previously satisfied clauses becoming unsatisfied, with some probability of picking one of the variables at
 * random.
 */
class WalkSat(val problem: Problem,
              override val config: SolverConfig = SolverConfig(),
              val timeout: Long = -1L,
              val probRandomWalk: Double = 0.05,
              val maxRestarts: Int = Int.MAX_VALUE,
              val maxFlips: Int = 400,
              val maxConsideration: Int = 32) : Solver, LocalSearch() {

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        val end = if (timeout > 0L) millis() + timeout else Long.MAX_VALUE
        for (i in 1..maxRestarts) {
            recordIteration()
            val rng = config.nextRandom()
            val labeling = config.labelingBuilder.generate(problem.nbrVariables, rng)
            val result = satIteration(contextLiterals, labeling, rng, end)
            if (result != null) {
                recordCompleted(true)
                return result.apply { pack() }
            }
            if (millis() > end) throw TimeoutException(timeout)

        }
        recordCompleted(false)
        throw IterationsReachedException(maxRestarts)
    }

    private fun satIteration(context: Literals, labeling: MutableLabeling, rng: Random, end: Long): MutableLabeling? {

        labeling.setAll(context)
        val contextIxs = HashIntSet().apply { context.forEach { this.add(it.asIx()) } }

        val unsatisfied: HashIntSet = let {
            var set: HashIntSet? = null
            for ((i, s) in problem.sentences.withIndex()) {
                if (!s.satisfies(labeling)) {
                    if (set == null)
                        set = HashIntSet(max(16, problem.sentences.size / 8))
                    set.add(i)
                }
            }
            set ?: return labeling
        }

        val improvement = IntArray(maxConsideration)

        for (flips in 1..maxFlips) {
            if (unsatisfied.isEmpty())
                return labeling

            // Pick random unsatisfied clause
            val pickedSentenceIx = unsatisfied.random(rng)
            val pickedSentence = problem.sentences[pickedSentenceIx]
            val literals = pickedSentence.literals

            val ix = if (probRandomWalk > rng.nextDouble()) literals[rng.nextInt(literals.size)].asIx()
            else chooseBest(contextIxs, literals, labeling, improvement, rng)

            if (ix < 0 || ix in contextIxs) continue
            labeling.flip(ix)
            for (sentenceIx in problem.sentencesWith(ix)) {
                if (problem.sentences[sentenceIx].satisfies(labeling)) unsatisfied.remove(sentenceIx)
                else unsatisfied.add(sentenceIx)
            }
            recordFlip(pickedSentence, labeling.asLiteral(ix))
            if (millis() > end) return null
        }
        return null
    }

    private fun chooseBest(contextIds: IntSet,
                           literals: Literals,
                           labeling: MutableLabeling,
                           improvement: IntArray,
                           rng: Random): Int {
        val perm = if (literals.size >= improvement.size) IntPermutation(literals.size, rng) else null
        for (i in 0 until min(improvement.size, literals.size)) {
            improvement[i] = 0
            val ix = literals[perm?.encode(i) ?: i].asIx()
            if (ix in contextIds) {
                improvement[i] = -1
                continue
            }
            val affected = problem.sentencesWith(ix)
            for (sentenceIx in affected) {
                //if (sentenceIx in unsatisfied) {
                val sent = problem.sentences[sentenceIx]
                val preFlip = sent.flipsToSatisfy(labeling)
                labeling.flip(ix)
                val postFlip = sent.flipsToSatisfy(labeling)
                improvement[i] += preFlip - postFlip
                labeling.flip(ix)
                //}
            }
        }
        val imprIx = IntPermutation(min(literals.size, improvement.size), rng).let {
            it.maxBy { i ->
                improvement[i]
            }!!
        }
        val ix = literals[perm?.encode(imprIx) ?: imprIx].asIx()
        return if (improvement[imprIx] < 0) -1
        else ix
    }
}


//class PropWalkSat() : Solver, LocalSearch() {
//}