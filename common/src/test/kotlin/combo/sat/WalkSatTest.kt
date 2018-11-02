package combo.sat

import combo.ga.RandomInitializer
import combo.model.*
import kotlin.test.Test

class WalkSatTest : SolverTest() {

    @Test
    fun categoryTest() {
        val root = Model.builder("Category")
        for (i in 1..3) {
            val sub = Model.builder("Cat$i")
            for (j in 1..3) {
                val subsub = Model.builder("Cat$i$j")
                for (k in 1..3) {
                    subsub.optional(flag("Cat$i$j$k"))
                }
                subsub.constrained(subsub.value reified or(subsub.children.map { it.value }))
                sub.optional(subsub)
            }
            sub.constrained(sub.value reified or(sub.children.map { it.value }))
            root.optional(sub)
        }
        root.constrained(atMost(root.children.map { it.value }, 2))
        root.constrained(exactly(root.leaves().map { it.value }.toList(), 5))
        val model = root.build()
        val walkSat = WalkSat(model.problem, init = RandomInitializer())
        repeat(10) {
            val l = walkSat.witness(intArrayOf(1))
            println(l)
        }
    }

    override fun solver(problem: Problem) = WalkSat(problem)
    override fun unsatSolver(problem: Problem) = WalkSat(problem, maxFlips = 10, maxRestarts = 1)
    override fun timeoutSolver(problem: Problem) = WalkSat(problem, timeout = 1L, maxConsideration = 1, maxFlips = 1, maxRestarts = 100)
}
