package combo.sat

import combo.util.IntHashSet
import kotlin.random.Random

/**
 * This contains cached information about satisfied constraints during search.
 */
class Validator(val problem: Problem, val instance: MutableInstance, val assumption: Constraint) :
        MutableInstance, Instance by instance {


    private fun rebuildIndex() {
        totalUnsatisfied = 0
        unsatisfied.clear()
        for ((constId, const) in problem.constraints.withIndex()) {
            constraintCache[constId] = const.cache(instance)
            totalUnsatisfied += const.violations(this, constraintCache[constId]).also {
                if (it > 0) unsatisfied.add(constId)
            }
        }
        constraintCache[constraintCache.lastIndex] = assumption.cache(instance)
        totalUnsatisfied += assumption.violations(this, constraintCache.last()).also {
            if (it > 0) unsatisfied.add(constraintCache.lastIndex)
        }
    }

    var totalUnsatisfied: Int = 0
        private set

    private val assumptionIxs = IntHashSet(nullValue = -1).apply {
        assumption.literals.forEach { add(it.toIx()) }
    }
    private val unsatisfied = IntHashSet(nullValue = -1)
    private val constraintCache = IntArray(problem.nbrConstraints + 1)

    init {
        rebuildIndex()
    }

    fun randomUnsatisfied(rng: Random): Constraint {
        val constId = unsatisfied.random(rng)
        return if (constId == constraintCache.lastIndex) assumption
        else problem.constraints[constId]
    }

    fun improvement(ix: Int): Int {
        val literal = !instance.literal(ix)
        val assumptionImprovement =
                if (literal.toIx() in assumptionIxs) improvementConst(ix, assumption, constraintCache.lastIndex)
                else 0
        return assumptionImprovement + problem.constraintsWith(ix).sumBy { constId ->
            val const = problem.constraints[constId]
            improvementConst(ix, const, constId)
        }
    }

    private fun improvementConst(ix: Int, const: Constraint, constId: Int): Int {
        val oldFlips = const.violations(this, constraintCache[constId])
        instance.flip(ix)
        val newMatches = const.cacheUpdate(constraintCache[constId], instance.literal(ix))
        val newFlips = const.violations(this, newMatches)
        instance.flip(ix)
        return oldFlips - newFlips
    }

    override fun set(ix: Int, value: Boolean) {
        val literal = ix.toLiteral(value)
        if (literal in instance) return
        if (literal.toIx() in assumptionIxs)
            updateConst(ix, assumption, constraintCache.lastIndex)
        for (constId in problem.constraintsWith(ix)) {
            val const = problem.constraints[constId]
            updateConst(ix, const, constId)
        }
        instance.flip(ix)
    }

    private fun updateConst(ix: Int, const: Constraint, constId: Int) {
        val oldFlips = const.violations(this, constraintCache[constId])
        instance.flip(ix)
        constraintCache[constId] = const.cacheUpdate(constraintCache[constId], instance.literal(ix))
        val newFlips = const.violations(this, constraintCache[constId])
        instance.flip(ix)
        if (oldFlips > 0 && newFlips == 0) unsatisfied.remove(constId)
        else if (newFlips > 0 && oldFlips == 0) unsatisfied.add(constId)
        totalUnsatisfied += newFlips - oldFlips
    }

    override fun setWord(wordIx: Int, value: Int) {
        instance.setWord(wordIx, value)
        rebuildIndex()
    }

    override fun equals(other: Any?) = instance.equals(other)
    override fun hashCode() = instance.hashCode()

    override fun clear() {
        instance.clear()
        rebuildIndex()
    }
}
