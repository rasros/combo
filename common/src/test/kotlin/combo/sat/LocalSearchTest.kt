package combo.sat

import combo.model.ModelTest
import kotlin.random.Random
import kotlin.test.Test

class LocalSearchTest {
    @Test
    fun test() {
        val p = ModelTest.large2.problem
        val l = BitFieldLabelingBuilder().generate(p.nbrVariables, Random.Default)
        println("pre  " + l.asLiterals().joinToString())
        val originalLiterals = IntArray(p.nbrVariables / 2) { -1 }
        l.flipChain(16, p.implicationGraph, originalLiterals)
        println("post " + l.asLiterals().joinToString())
        l.flipChain(16, originalLiterals)
        println("undo " + l.asLiterals().joinToString())
    }

}
