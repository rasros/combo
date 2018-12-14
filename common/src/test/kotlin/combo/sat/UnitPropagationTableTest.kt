package combo.sat

import combo.model.ModelTest
import combo.test.assertContentEquals
import combo.util.IntSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TODO
class UnitPropagationTableTest {

    companion object {
        val smallPropTables = ModelTest.smallModels.map { UnitPropagationTable(it.problem) }
        val smallUnsatPropTables = ModelTest.smallUnsatModels.map { UnitPropagationTable(it.problem) }
        val largePropTables = ModelTest.largeModels.map { UnitPropagationTable(it.problem) }
        val hugePropTable = UnitPropagationTable(ModelTest.hugeModel.problem)
    }

    @Test
    fun propagationSimple() {
        val p = Problem(arrayOf(Disjunction(intArrayOf(0, 2))), 2)
        val ep = UnitPropagationTable(p)
        assertContentEquals(intArrayOf(), ep.literalPropagations[0])
        assertContentEquals(intArrayOf(2), ep.literalPropagations[1])
        assertContentEquals(intArrayOf(), ep.literalPropagations[2])
        assertContentEquals(intArrayOf(0), ep.literalPropagations[3])
    }

    @Test
    fun propagationMultiple() {
        val p = Problem(arrayOf(Disjunction(intArrayOf(0, 2)), Disjunction(intArrayOf(2, 4))), 3)
        val ep = UnitPropagationTable(p)
        assertContentEquals(intArrayOf(), ep.literalPropagations[0])
        assertContentEquals(intArrayOf(2), ep.literalPropagations[1])
        assertContentEquals(intArrayOf(), ep.literalPropagations[2])
        assertContentEquals(intArrayOf(0, 4), ep.literalPropagations[3])
        assertContentEquals(intArrayOf(), ep.literalPropagations[4])
        assertContentEquals(intArrayOf(2), ep.literalPropagations[5])
    }

    /*
    @Test
    fun unsatisfiableTracker() {
        for ((i, pt) in smallUnsatPropTables.withIndex()) {
            val problem = ModelTest.smallUnsatModels[i].problem
            val l = BitFieldLabeling(problem.nbrVariables)
            val t = PropLabelingTracker(l, problem, pt)
            assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
        }
    }
    */

    /*
    @Test
    fun trackerSatOrUnsat() {
        fun helper(p: Problem, pt: UnitPropagationTable) {
            for (j in 1..10) {
                val l = BitFieldLabeling(p.nbrVariables)
                val t = PropLabelingTracker(l, p, pt)
                if (p.satisfies(l)) assertTrue(t.unsatisfied.isEmpty(), "Model $i")
                else {
                    assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
                    assertEquals(p.sentences.count { !it.satisfies(l) }, t.unsatisfied.size)
                }
            }

        }
        for ((i, ep) in (smallPropTables).withIndex())
            helper(ModelTest.smallModels[i].problem, ep)

        for ((i, ep) in (largePropTables).withIndex())
            helper(ModelTest.largeModels[i].problem, ep)
    }
    */

    /*
    @Test
    fun includedInAffectedSetWithSetLabeling() {
        val pt = largePropTables[1]
        val p = ModelTest.largeModels[1].problem
        val l = BitFieldLabeling(p.nbrVariables)
        val t = PropLabelingTracker(l, p, pt).also { PropLabelingTracker.initialize(t, rng = Random(0)) }
        val pre = t.labeling.copy()
        val affected = IntSet()
        t.set(!t.labeling.asLiteral(10), affected)
        var nbrDifferent = 0
        for (i in 0 until t.labeling.size) {
            if (pre[i] != t.labeling[i]) {
                nbrDifferent++
                assertTrue(i in affected)
            }
        }
        assertEquals(nbrDifferent, affected.size)
    }
    */

    /*
    @Test
    fun includedInAffectedSetNoSetLabeling() {
        val p = ModelTest.largeModels[1].problem
        val pt = largePropTables[1]
        val l = BitFieldLabelingBuilder().generate(p.nbrVariables, Random)
        val t = PropLabelingTracker(l, p, pt)

        val pre = t.labeling.copy()
        val affected = IntSet()
        t.set(!t.labeling.asLiteral(10), affected)
        var nbrDifferent = 0
        for (i in 0 until t.labeling.size) {
            if (pre[i] != t.labeling[i]) {
                nbrDifferent++
                assertTrue(i in affected)
            }
        }
        assertEquals(nbrDifferent, affected.size)
    }
    */

    /*
    @Test
    fun undoTracker() {
        for ((i, ep) in (smallPropTables + largePropTables).withIndex()) {
            for (j in 1..10) {
                val l = BitFieldLabeling(ep.problem.nbrVariables)
                val t = ep.PropLabelingTracker(l)
                val pre = l.copy()
                val affected = IntSet()
                for (k in 0 until l.size) {
                    t.set(l)
                    assertEquals()
                }

                if (ep.problem.satisfies(l)) assertTrue(t.unsatisfied.isEmpty(), "Model $i")
                else {
                    assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
                    assertEquals(ep.problem.sentences.count { !it.satisfies(l) }, t.unsatisfied.size, "Model $i")
                }
            }
        }
    }
    */
}
