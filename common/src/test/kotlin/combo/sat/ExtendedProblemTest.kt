package combo.sat

import combo.model.ModelTest
import combo.test.assertContentEquals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtendedProblemTest {

    companion object {
        val smallProblems = ModelTest.smallModels.map { ExtendedProblem(it.problem) }
        val smallUnsatProblems = ModelTest.smallUnsatModels.map { ExtendedProblem(it.problem) }
        val largeProblems = ModelTest.largeModels.map { ExtendedProblem(it.problem) }
        val hugeProblem = ExtendedProblem(ModelTest.hugeModel.problem)
    }

    @Test
    fun propagationSimple() {
        val p = Problem(arrayOf(Disjunction(intArrayOf(0, 2))), 2)
        val ep = ExtendedProblem(p)
        assertContentEquals(intArrayOf(), ep.literalPropagations[0])
        assertContentEquals(intArrayOf(2), ep.literalPropagations[1])
        assertContentEquals(intArrayOf(), ep.literalPropagations[2])
        assertContentEquals(intArrayOf(0), ep.literalPropagations[3])
    }

    @Test
    fun propagationMultiple() {
        val p = Problem(arrayOf(Disjunction(intArrayOf(0, 2)), Disjunction(intArrayOf(2, 4))), 3)
        val ep = ExtendedProblem(p)
        assertContentEquals(intArrayOf(), ep.literalPropagations[0])
        assertContentEquals(intArrayOf(2), ep.literalPropagations[1])
        assertContentEquals(intArrayOf(), ep.literalPropagations[2])
        assertContentEquals(intArrayOf(0, 4), ep.literalPropagations[3])
        assertContentEquals(intArrayOf(), ep.literalPropagations[4])
        assertContentEquals(intArrayOf(2), ep.literalPropagations[5])
    }

    @Test
    fun unsatisfiableTracker() {
        for ((i, ep) in smallUnsatProblems.withIndex()) {
            val l = BitFieldLabeling(ep.problem.nbrVariables)
            val t = ep.LabelingTracker(l)
            assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
        }
    }

    @Test
    fun trackerSatOrUnsat() {
        for ((i, ep) in (smallProblems + largeProblems).withIndex()) {
            for (j in 1..10) {
                val l = BitFieldLabeling(ep.problem.nbrVariables)
                val t = ep.LabelingTracker(l)
                if (ep.problem.satisfies(l)) assertTrue(t.unsatisfied.isEmpty(), "Model $i")
                else {
                    assertTrue(t.unsatisfied.isNotEmpty(), "Model $i")
                    assertEquals(ep.problem.sentences.count { !it.satisfies(l) }, t.unsatisfied.size, "Model $i")
                }
            }
        }
    }
}
