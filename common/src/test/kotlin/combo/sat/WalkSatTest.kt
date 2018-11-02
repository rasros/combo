package combo.sat

import combo.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class WalkSatTest : SolverTest() {

    @Test
    fun categoryTest() {
        val root = Model.builder("Category")
        for (i in 1..5) {
            val sub = Model.builder("Cat$i")
            for (j in 1..20) {
                val subsub = Model.builder("Cat$i$j")
                for (k in 1..10) {
                    subsub.optional(flag("Cat$i$j$k"))
                }
                subsub.constrained(subsub.value reified or(subsub.children.map { it.value }))
                sub.optional(subsub)
            }
            sub.constrained(sub.value reified or(sub.children.map { it.value }))
            root.optional(sub)
        }
        root.constrained(atMost(root.leaves().map { it.value }.toList(), 5))
        val model = root.build()
        val walkSat = solver(model.problem)
        repeat(10) {
            walkSat.witness(intArrayOf())
        }
        assertEquals(0, walkSat.totalFlips)
    }

    override fun solver(problem: Problem) = WalkSat(problem)
    override fun unsatSolver(problem: Problem) = WalkSat(problem, maxFlips = 10, maxRestarts = 1)
    override fun timeoutSolver(problem: Problem) = WalkSat(problem, timeout = 1L, maxConsideration = 1, maxFlips = 1, maxRestarts = 100)
}
