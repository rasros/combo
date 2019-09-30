package combo.sat.optimizers

import combo.sat.Problem

class GeneticAlgorithmTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, randomSeed: Int): Optimizer<O>? {
        return GeneticAlgorithm.Builder().randomSeed(randomSeed).candidateSize(80).penalty(DisjunctPenalty()).timeout(5 * 1000L).build()
    }

    override fun <O : ObjectiveFunction> infeasibleOptimizer(problem: Problem, randomSeed: Int): Optimizer<O>? {
        return GeneticAlgorithm.Builder().randomSeed(randomSeed).candidateSize(3).timeout(1L).maxSteps(1).restarts(1).build()
    }
}

/*
class GALinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem, randomSeed: Int) = GeneticAlgorithmOptimizer<LinearObjective>(problem).apply {
        this.randomSeed = randomSeed; timeout = 5 * 1000L; candidateSize = 80
        val id = ImplicationDigraph(problem)
        initializer = ImplicationConstraintCoercer(problem, id, WordRandomSet())
        penalty = DisjunctPenalty()
        guessMutator = PropagatingMutator(FixedRateMutation(), id)
    }

    override fun largeOptimizer(problem: Problem, randomSeed: Int) = GeneticAlgorithmOptimizer<LinearObjective>(problem).apply {
        this.randomSeed = randomSeed; timeout = 5 * 1000L; candidateSize = 20; restarts = 1
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
        penalty = DisjunctPenalty()
    }

}

class GASolverTest : SolverTest() {
    override fun solver(problem: Problem, randomSeed: Int) = GeneticAlgorithmSolver(problem).apply {
        this.randomSeed = randomSeed; timeout = 5 * 1000L
    }

    override fun largeSolver(problem: Problem, randomSeed: Int) = GeneticAlgorithmSolver(problem).apply {
        this.randomSeed = randomSeed; timeout = 1000 * 5 * 1000L; candidateSize = 20
        val id = ImplicationDigraph(problem)
        initializer = ImplicationConstraintCoercer(problem, id, WordRandomSet())
        mutation = PropagatingMutator(FixedRateMutation(), id)
    }
}
 */
