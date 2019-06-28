package combo.sat.solvers

import combo.ga.FixedRateMutation
import combo.ga.PropagatingMutator
import combo.sat.*

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

    override fun infeasibleOptimizer(problem: Problem, randomSeed: Int) = GeneticAlgorithmOptimizer<LinearObjective>(problem).apply {
        this.randomSeed = randomSeed; timeout = 1; maxSteps = 1
    }
}

class GeneticAlgorithmOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O, randomSeed: Int) = GeneticAlgorithmOptimizer<O>(problem).apply {
        this.randomSeed = randomSeed; timeout = 5 * 1000L; candidateSize = 50; restarts = 1
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WordRandomSet())
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

    override fun unsatSolver(problem: Problem, randomSeed: Int) = GeneticAlgorithmSolver(problem).apply {
        this.randomSeed = randomSeed; timeout = 1L; maxSteps = 1
    }
}
