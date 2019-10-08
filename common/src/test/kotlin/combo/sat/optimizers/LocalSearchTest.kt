package combo.sat.optimizers

import combo.sat.InitializerType
import combo.sat.Problem

class LocalSearchTest : OptimizerTest() {
    override fun <O : ObjectiveFunction> optimizer(problem: Problem, randomSeed: Int) =
            LocalSearch.Builder(problem).randomSeed(randomSeed).timeout(5 * 1000L).build()

    override fun linearOptimizer(problem: Problem, randomSeed: Int) =
            LocalSearch.Builder(problem).initializer(InitializerType.WEIGHT_MAX_PROPAGATE_COERCE)
                    .randomSeed(randomSeed).timeout(5 * 1000L).build()

    override fun <O : ObjectiveFunction> infeasibleOptimizer(problem: Problem, randomSeed: Int) =
            LocalSearch.Builder(problem).randomSeed(randomSeed).timeout(1L).maxSteps(1).restarts(1).build()
}
