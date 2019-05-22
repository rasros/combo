package combo.sat.solvers

import combo.ga.FixedRateMutation
import combo.ga.PropagatingMutator
import combo.math.ImplicationDigraph
import combo.sat.ImplicationConstraintCoercer
import combo.sat.Problem
import combo.sat.WeightSet
import combo.sat.WordRandomSet

class GALinearOptimizerTest : LinearOptimizerTest() {
    override fun optimizer(problem: Problem) = GeneticAlgorithmOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0; timeout = 5 * 1000L; candidateSize = 80
        val id = ImplicationDigraph(problem)
        initializer = ImplicationConstraintCoercer(problem, id, WordRandomSet())
        penalty = DisjunctPenalty()
        guessMutator = PropagatingMutator(FixedRateMutation(), id)
    }

    override fun largeOptimizer(problem: Problem) = GeneticAlgorithmOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0; timeout = 5 * 1000L; candidateSize = 20; restarts = 1
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WeightSet())
        penalty = DisjunctPenalty()
    }

    override fun infeasibleOptimizer(problem: Problem) = GeneticAlgorithmOptimizer<LinearObjective>(problem).apply {
        randomSeed = 0; timeout = 1; maxSteps = 1
    }
}

class GeneticAlgorithmOptimizerTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, function: O) = GeneticAlgorithmOptimizer<O>(problem).apply {
        randomSeed = 0; timeout = 5 * 1000L; candidateSize = 50; restarts = 1
        initializer = ImplicationConstraintCoercer(problem, ImplicationDigraph(problem), WordRandomSet())
    }
}

class GASolverTest : SolverTest() {
    override fun solver(problem: Problem) = GeneticAlgorithmSolver(problem).apply {
        randomSeed = 0; timeout = 5 * 1000L
    }

    override fun largeSolver(problem: Problem) = GeneticAlgorithmSolver(problem).apply {
        randomSeed = 0; timeout = 1000 * 5 * 1000L; candidateSize = 20
        val id = ImplicationDigraph(problem)
        initializer = ImplicationConstraintCoercer(problem, id, WordRandomSet())
        mutation = PropagatingMutator(FixedRateMutation(), id)
    }

    override fun unsatSolver(problem: Problem) = GeneticAlgorithmSolver(problem).apply {
        randomSeed = 0; timeout = 1L; maxSteps = 1
    }
}
