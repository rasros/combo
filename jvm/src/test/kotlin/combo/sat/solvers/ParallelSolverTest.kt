package combo.sat.solvers

import combo.math.nextNormal
import combo.model.TestModels
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.toLiteral
import combo.util.EmptyCollection
import combo.util.collectionOf
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelSolverTest {
    private val solvers = arrayOf<(Problem) -> Solver>(
            { p: Problem -> LocalSearchSolver(p).apply { randomSeed = 0 } },
            { p: Problem -> ExhaustiveSolver(p).apply { randomSeed = 0 } },
            { p: Problem -> CachedSolver(LocalSearchSolver(p).apply { randomSeed = 0 }) },
            { p: Problem -> JacopSolver(p).apply { randomSeed = 0 } },
            { p: Problem -> Sat4JSolver(p).apply { randomSeed = 0 } })

    private val optimizers = arrayOf<(Problem) -> Optimizer<LinearObjective>>(
            { p: Problem -> LocalSearchOptimizer<LinearObjective>(p).apply { randomSeed = 0 } },
            { p: Problem -> ExhaustiveSolver(p).apply { randomSeed = 0 } },
            { p: Problem -> CachedOptimizer(LocalSearchOptimizer<LinearObjective>(p).apply { randomSeed = 0 }) },
            { p: Problem -> JacopSolver(p).apply { randomSeed = 0 } },
            { p: Problem -> Sat4JSolver(p).apply { randomSeed = 0 } })

    @Test
    fun parallelSolve() {
        val p = TestModels.SAT_PROBLEMS[0]
        for (solverCreator in solvers) {
            val solver = solverCreator.invoke(p)
            val pool = Executors.newFixedThreadPool(10)
            try {
                val list = ArrayList<Callable<Instance>>()
                for (i in 0 until 50) {
                    list.add(Callable {
                        val assumptions = if (Random.nextBoolean()) EmptyCollection
                        else collectionOf(Random.nextInt(p.nbrVariables).toLiteral(Random.nextBoolean()))
                        solver.witnessOrThrow(assumptions)
                    })
                }
                val instances = pool.invokeAll(list).map { it.get() }
                pool.shutdown()
                pool.awaitTermination(10L, TimeUnit.SECONDS)
                assertEquals(50, instances.size)
                instances.forEach {
                    assertTrue(p.satisfies(it))
                }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun parallelSequence() {
        val p = TestModels.SAT_PROBLEMS[1]
        val nbrSolutions = ExhaustiveSolver(p).asSequence().count()
        for (solverCreator in solvers) {
            val solver = solverCreator.invoke(p)
            val pool = Executors.newFixedThreadPool(5)
            try {
                val list = ArrayList<Callable<Set<Instance>>>()
                for (i in 0 until 5)
                    list.add(Callable {
                        solver.asSequence().take(10).toSet()
                    })
                val instances = pool.invokeAll(list).flatMap { it.get() }.toSet()
                pool.shutdown()
                pool.awaitTermination(10L, TimeUnit.SECONDS)
                assertEquals(nbrSolutions, instances.size, solver::class.java.name)
                instances.forEach {
                    assertTrue(p.satisfies(it), solver::class.java.name)
                }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test
    fun parallelOptimize() {
        val p = TestModels.SAT_PROBLEMS[3]
        for (solverCreator in optimizers) {
            val optimizer = solverCreator.invoke(p)
            val pool = Executors.newFixedThreadPool(5)
            try {
                val list = ArrayList<Callable<Instance>>()
                for (i in 0 until 20)
                    list.add(Callable {
                        val assumptions = if (Random.nextBoolean()) EmptyCollection
                        else collectionOf(Random.nextInt(p.nbrVariables).toLiteral(Random.nextBoolean()))
                        optimizer.optimizeOrThrow(
                                LinearObjective(true, FloatArray(p.nbrVariables) { Random.nextNormal() }),
                                assumptions)
                    })
                val instances = pool.invokeAll(list).map { it.get() }
                pool.shutdown()
                pool.awaitTermination(10L, TimeUnit.SECONDS)
                assertEquals(20, instances.size, optimizer::class.java.name)
                instances.forEach {
                    assertTrue(p.satisfies(it), optimizer::class.java.name)
                }
            } finally {
                pool.shutdownNow()
            }
        }
    }
}
