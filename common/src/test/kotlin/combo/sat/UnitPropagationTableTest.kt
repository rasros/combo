package combo.sat

import combo.model.ModelTest
import combo.test.assertContentEquals
import kotlin.test.Test

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
}

