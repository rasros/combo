package combo.sat.optimizers

import combo.math.RunningVariance
import combo.math.Vector
import combo.math.nextNormal
import combo.math.sample
import combo.model.FloatVar
import combo.model.IntVar
import combo.model.TestModels
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.test.assertContentEquals
import combo.test.assertEquals
import combo.util.IntArrayList
import combo.util.IntCollection
import combo.util.collectionOf
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.*

abstract class OptimizerTest {

    abstract fun <O : ObjectiveFunction> optimizer(problem: Problem, randomSeed: Int = 0): Optimizer<O>?
    abstract fun <O : ObjectiveFunction> infeasibleOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<O>?

    open fun linearOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<LinearObjective>? = optimizer(problem, randomSeed)
    open fun largeLinearOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<LinearObjective>? = linearOptimizer(problem, randomSeed)
    open fun infeasibleLinearOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<LinearObjective>? = infeasibleOptimizer(problem, randomSeed)
    open fun timeoutLinearOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<LinearObjective>? = infeasibleLinearOptimizer(problem, randomSeed)

    open fun satOptimizer(problem: Problem, randomSeed: Int = 0) = optimizer<SatObjective>(problem, randomSeed)
    open fun linearSatOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<*>? = satOptimizer(problem, randomSeed)
    open fun numericSatOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<*>? = satOptimizer(problem, randomSeed)
    open fun largeSatOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<*>? = satOptimizer(problem, randomSeed)
    open fun infeasibleSatOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<*>? = infeasibleOptimizer<SatObjective>(problem, randomSeed)
    open fun timeoutSatOptimizer(problem: Problem, randomSeed: Int = 0): Optimizer<*>? = infeasibleSatOptimizer(problem, randomSeed)

    private fun optimizerTest(p: Problem, function: ObjectiveFunction, i: Int) {
        val optimizer = optimizer<ObjectiveFunction>(p)
        optimizer ?: return
        val instance = optimizer.optimizeOrThrow(function)
        assertTrue(p.satisfies(instance))
        val optValue = function.value(instance)
        val bruteForceLabelingIx = (0 until 2.0.pow(p.nbrVariables).toInt()).minBy {
            val instance1 = BitArray(p.nbrVariables, intArrayOf(it))
            if (p.satisfies(instance1)) function.value(instance1)
            else Float.POSITIVE_INFINITY
        }
        val bruteForceValue = function.value(BitArray(p.nbrVariables, intArrayOf(bruteForceLabelingIx!!)))
        assertEquals(bruteForceValue, optValue, max(1.0f, 0.01f * p.nbrVariables), "Model $i")
    }

    @Test
    fun interactiveObjectiveOptimizer() {
        for ((i, p) in TestModels.TINY_PROBLEMS.withIndex()) {
            val rng = Random(i)
            val function = InteractionObjective(FloatArray(p.nbrVariables) { rng.nextFloat() - 0.5f })
            optimizerTest(p, function, i)
        }
    }

    @Test
    fun oneMaxObjectiveOptimizer() {
        for ((i, p) in TestModels.TINY_PROBLEMS.withIndex()) {
            val function = OneMaxObjective(p.nbrVariables)
            optimizerTest(p, function, i)
        }
    }

    @Test
    fun jumpObjectiveOptimizer() {
        for ((i, p) in TestModels.TINY_PROBLEMS.withIndex()) {
            val bruteForceLabelingIx = (0 until 2.0.pow(p.nbrVariables).toInt()).minBy {
                val instance = BitArray(p.nbrVariables, intArrayOf(it))
                if (p.satisfies(instance)) OneMaxObjective(p.nbrVariables).value(instance)
                else Float.POSITIVE_INFINITY
            }
            val bruteForceValue = OneMaxObjective(p.nbrVariables).value(
                    BitArray(p.nbrVariables, intArrayOf(bruteForceLabelingIx!!)))
            val function = JumpObjective(-bruteForceValue.toInt())
            optimizerTest(p, function, i)
        }
    }


    @Test
    fun emptyProblemSat() {
        val optimizer = satOptimizer(Problem(0, arrayOf())) ?: return
        val instance = optimizer.witnessOrThrow()
        assertEquals(0, instance.size)
    }

    @Test
    fun smallInfeasibleSatShouldThrow() {
        for ((i, p) in TestModels.UNSAT_PROBLEMS.withIndex()) {
            assertFailsWith(ValidationException::class, "Model $i") {
                val optimizer = infeasibleSatOptimizer(p) ?: return
                optimizer.witnessOrThrow()
            }
        }
    }

    @Test
    fun smallInfeasibleSequenceShouldBeEmpty() {
        for ((i, p) in TestModels.UNSAT_PROBLEMS.withIndex()) {
            try {
                val optimizer = infeasibleSatOptimizer(p) ?: return
                assertEquals(0, optimizer.asSequence().count(), "Model $i")
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallSatProblemsSolvable() {
        for ((i, p) in TestModels.SAT_PROBLEMS.withIndex()) {
            val optimizer = satOptimizer(p) ?: return
            assertTrue(p.satisfies(optimizer.witnessOrThrow()), "Model $i")
            assertTrue(p.satisfies(optimizer.witness()!!), "Model $i")
        }
    }

    @Test
    fun guessIsReusedForWitness() {
        for (p in TestModels.SAT_PROBLEMS) {
            val optimizer = satOptimizer(p) ?: return
            val initial = optimizer.witnessOrThrow() as MutableInstance
            assertEquals(initial, optimizer.witness(guess = initial))
        }
    }

    @Test
    fun smallSatRepeatedShouldNotThrow() {
        for (p in TestModels.TINY_PROBLEMS) {
            val optimizer = satOptimizer(p) ?: return
            repeat(20) {
                optimizer.witnessOrThrow()
            }
        }
    }

    @Test
    fun sequenceWithRandomSeedIsDeterministic() {
        for (p in TestModels.SAT_PROBLEMS) {
            val optimizer1 = satOptimizer(p, 1) ?: return
            val optimizer2 = satOptimizer(p, 1) ?: return
            val solutions1 = optimizer1.asSequence().take(10).toList()
            val solutions2 = optimizer2.asSequence().take(10).toList()
            assertContentEquals(solutions1, solutions2)
        }
    }

    @Test
    fun problemsWithNumericConstraintsSolvable() {
        for ((i, p) in TestModels.NUMERIC_PROBLEMS.withIndex()) {
            val optimizer = numericSatOptimizer(p) ?: return
            assertTrue(p.satisfies(optimizer.witnessOrThrow()), "Model $i")
            assertTrue(p.satisfies(optimizer.witness()!!), "Model $i")
        }
    }

    @Test
    fun problemsWithNumericConstraintsAndAssumptionsSolvable() {
        val m = TestModels.NUMERIC3
        val optimizer = numericSatOptimizer(m.problem) ?: return
        val opt1 = m["opt1"] as IntVar

        // First number is optional -100..100
        val intSet = optimizer.witnessOrThrow(collectionOf(m.index.indexOf(opt1).toLiteral(true)))
        assertTrue(TestModels.NUMERIC3.toAssignment(intSet)[opt1]!! in -100..100)

        val intUnset = optimizer.witnessOrThrow(collectionOf(m.index.indexOf(opt1).toLiteral(false)))
        assertNull(TestModels.NUMERIC3.toAssignment(intUnset)[opt1])

        // First number is optional -100..100
        val opt2 = m["opt2"] as FloatVar
        val floatSet = optimizer.witnessOrThrow(collectionOf(m.index.indexOf(opt2).toLiteral(true)))
        assertTrue(TestModels.NUMERIC3.toAssignment(floatSet)[opt2]!! in -0.1f..1.0f)

        val floatUnset = optimizer.witnessOrThrow(collectionOf(m.index.indexOf(opt2).toLiteral(false)))
        assertNull(TestModels.NUMERIC3.toAssignment(floatUnset)[opt2])
    }

    @Test
    fun problemsWithLinearConstraintsSolvable() {
        for ((i, p) in TestModels.PB_PROBLEMS.withIndex()) {
            val optimizer = linearSatOptimizer(p) ?: return
            assertTrue(p.satisfies(optimizer.witnessOrThrow()), "Model $i")
            assertTrue(p.satisfies(optimizer.witness()!!), "Model $i")
        }
    }

    @Test
    fun smallSatSequence() {
        for ((i, p) in TestModels.TINY_PROBLEMS.withIndex()) {
            val optimizer = satOptimizer(p) ?: return
            assertTrue(p.satisfies(optimizer.asSequence().first()), "Model $i")
            assertTrue(p.satisfies(optimizer.witnessOrThrow()), "Model $i")
            assertTrue(p.satisfies(optimizer.asSequence().first()), "Model $i")
        }
    }

    @Test
    fun largeSatProblemsSolvable() {
        for ((i, p) in TestModels.LARGE_SAT_PROBLEMS.withIndex()) {
            val optimizer = largeSatOptimizer(p) ?: return
            assertTrue(p.satisfies(optimizer.witnessOrThrow()), "Model $i")
            assertTrue(p.satisfies(optimizer.witness()!!), "Model $i")
        }
    }

    @Test
    fun smallSatProblemsRepeatedWithAssumptions() {
        for ((i, p) in TestModels.SAT_PROBLEMS.withIndex()) {
            val optimizer = satOptimizer(p) ?: return
            val instance = optimizer.witnessOrThrow()
            val rng = Random(i.toLong())
            val assumptions = IntArrayList()
            for (j in 0 until instance.size) {
                if (rng.nextBoolean())
                    assumptions.add(instance.literal(j))
            }
            assertTrue(p.satisfies(instance))
            val restricted = optimizer.witnessOrThrow(assumptions)
            assertTrue(p.satisfies(restricted),
                    "Model $i, assumptions ${assumptions.joinToString(",")}")
            assertTrue(Conjunction(assumptions).satisfies(restricted),
                    "Model $i, assumptions ${assumptions.joinToString(",")}")
        }
    }

    @Test
    fun smallSatProblemsRepeatedWithAssumptionsInSequence() {
        for ((i, p) in TestModels.SAT_PROBLEMS.withIndex()) {
            val optimizer = satOptimizer(p) ?: return
            val instance = optimizer.witnessOrThrow()
            val rng = Random(i.toLong())
            val assumptions = IntArrayList()
            for (j in 0 until instance.size) {
                if (rng.nextBoolean())
                    assumptions.add(instance.literal(j))
            }
            val restricted = optimizer.witnessOrThrow(assumptions)
            assertTrue(p.satisfies(restricted),
                    "Model $i, assumptions ${assumptions.joinToString(",")}")
            assertTrue(Conjunction(assumptions).satisfies(restricted),
                    "Model $i, assumptions ${assumptions.joinToString(",")}")
        }
    }

    @Test
    fun smallInfeasibleWithAssumptions() {
        fun testUnsat(assumptions: IntCollection, p: Problem) {
            assertFailsWith(ValidationException::class) {
                val optimizer = infeasibleSatOptimizer(p) ?: return
                optimizer.witnessOrThrow(assumptions)
            }
        }
        testUnsat(collectionOf(11, 12), TestModels.SAT_PROBLEMS[0])
        testUnsat(collectionOf(6, -7, -8), TestModels.SAT_PROBLEMS[0])
        testUnsat(collectionOf(-2, 4), TestModels.SAT_PROBLEMS[0])
        testUnsat(collectionOf(1, 6), TestModels.SAT_PROBLEMS[2])
        testUnsat(collectionOf(3, 4, 5), TestModels.SAT_PROBLEMS[3])
        testUnsat(collectionOf(-10, -11, -12), TestModels.SAT_PROBLEMS[3])
        testUnsat(collectionOf(-4, 5), TestModels.SAT_PROBLEMS[4])
    }

    @Test
    fun smallInfeasibleSequenceWithAssumptions() {
        fun testUnsat(assumptions: IntCollection, p: Problem) {
            val optimizer = infeasibleSatOptimizer(p) ?: return
            assertEquals(0, optimizer.asSequence(assumptions).count())
        }
        testUnsat(collectionOf(11, 12), TestModels.SAT_PROBLEMS[0])
        testUnsat(collectionOf(6, -7, -8), TestModels.SAT_PROBLEMS[0])
        testUnsat(collectionOf(-2, 4), TestModels.SAT_PROBLEMS[0])
        testUnsat(collectionOf(1, 6), TestModels.SAT_PROBLEMS[2])
        testUnsat(collectionOf(3, 4, 5), TestModels.SAT_PROBLEMS[3])
        testUnsat(collectionOf(-10, -11, -12), TestModels.SAT_PROBLEMS[3])
        testUnsat(collectionOf(-4, 5), TestModels.SAT_PROBLEMS[4])
    }

    @Test
    fun sequenceSizeNoConstraints() {
        val p = Problem(4, arrayOf())
        val optimizer = satOptimizer(p) ?: return
        val toSet = optimizer.asSequence().take(200).toSet()
        assertTrue(toSet.size in 14..16)
    }

    @Test
    fun timeoutOnWitness() {
        val optimizer = timeoutSatOptimizer(TestModels.LARGE_SAT_PROBLEMS[1]) ?: return
        assertFailsWith(ValidationException::class) {
            optimizer.witnessOrThrow()
        }
    }

    @Test
    fun timeoutOnSequence() {
        val optimizer = timeoutSatOptimizer(TestModels.LARGE_SAT_PROBLEMS[1]) ?: return
        optimizer.asSequence().count()
    }


    @Test
    fun emptyProblemLinearOptimize() {
        val p = Problem(0)
        val optimizer = linearOptimizer(p) ?: return
        val l = optimizer.optimizeOrThrow(LinearObjective(true, floatArrayOf()))
        assertEquals(0, l.size)
    }

    @Test
    fun smallLinearOptimizeInfeasible() {
        for ((i, p) in TestModels.UNSAT_PROBLEMS.withIndex()) {
            try {
                val unsatOptimizer = infeasibleLinearOptimizer(p) ?: return
                assertFailsWith(ValidationException::class, "Model $i") {
                    unsatOptimizer.optimizeOrThrow(LinearObjective(false, FloatArray(p.nbrVariables) { 0.0f }))
                }
            } catch (e: UnsatisfiableException) {
            }
        }
    }

    @Test
    fun smallLinearOptimizeFeasibility() {
        for (p in TestModels.SAT_PROBLEMS) {
            linearOptimizer(p)?.optimizeOrThrow(LinearObjective(false, FloatArray(p.nbrVariables) { 0.0f }))
        }
    }

    @Test
    fun guessReuseForLinearOptimizer() {
        for ((i, p) in TestModels.SAT_PROBLEMS.withIndex()) {
            val optimizer = linearOptimizer(p) ?: return
            val weights = FloatArray(p.nbrVariables) { Random.nextNormal() }
            val obj = LinearObjective(false, weights)
            val initial = optimizer.optimizeOrThrow(obj)
            val actual = optimizer.optimizeOrThrow(obj, guess = initial.copy())
            assertTrue(initial dot weights >= actual dot weights, "$i")
        }
    }

    @Test
    fun smallLinearOptimizeRepeated() {
        for (p in TestModels.TINY_PROBLEMS) {
            val optimizer = linearOptimizer(p) ?: return
            val varianceEstimate = generateSequence {
                optimizer.optimizeOrThrow(LinearObjective(false, FloatArray(p.nbrVariables) { 1.0f }))
            }.map { it.sum().toFloat() }.take(20).sample(RunningVariance())
            if (optimizer.complete) assertEquals(0.0f, varianceEstimate.variance)
        }
    }

    @Test
    fun deterministicMaximizeForSetRandomSeed() {
        for (p in TestModels.TINY_PROBLEMS) {
            val optimizer1 = linearOptimizer(p, 1) ?: return
            val optimizer2 = linearOptimizer(p, 1) ?: return
            val obj = LinearObjective(true, FloatArray(p.nbrVariables) { Random(0).nextNormal() })
            val solutions1 = generateSequence { optimizer1.optimizeOrThrow(obj) }.take(10).toList()
            val solutions2 = generateSequence { optimizer2.optimizeOrThrow(obj) }.take(10).toList()
            assertContentEquals(solutions1, solutions2)
        }
    }

    @Test
    fun deterministicMinimizeForSetRandomSeed() {
        for (p in TestModels.TINY_PROBLEMS) {
            val optimizer1 = linearOptimizer(p, 1) ?: return
            val optimizer2 = linearOptimizer(p, 1) ?: return
            val obj = LinearObjective(false, FloatArray(p.nbrVariables) { Random(0).nextNormal() })
            val solutions1 = generateSequence { optimizer1.optimizeOrThrow(obj) }.take(10).toList()
            val solutions2 = generateSequence { optimizer2.optimizeOrThrow(obj) }.take(10).toList()
            assertContentEquals(solutions1, solutions2)
        }
    }

    @Test
    fun smallLinearOptimize() {
        fun testOptimize(weights: FloatArray, p: Problem, maximize: Boolean, target: Float, delta: Float = max(target * .3f, 0.01f)) {
            val optimizer = linearOptimizer(p) ?: return
            val optimizeOrThrow = optimizer.optimizeOrThrow(LinearObjective(maximize, weights))
            assertTrue(p.satisfies(optimizeOrThrow))
            assertEquals(target, optimizeOrThrow dot weights, delta)
            val optimize = optimizer.optimize(LinearObjective(maximize, weights))!!
            assertTrue(p.satisfies(optimize))
            assertEquals(target, optimize dot weights, delta)
        }

        with(TestModels.MODEL1.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 1.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 10.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { -1.0f }, this, false, -11.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { -1.0f }, this, true, 0.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { 0.0f }, this, false, 0.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() }, this, true, 62.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() }, this, false, 0.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() * .1f }, this, true, 6.2f, 0.2f)
            testOptimize(FloatArray(nbrVariables) { it.toFloat() * .1f }, this, false, 0.2f, 0.2f)
        }

        with(TestModels.MODEL2.problem) {
            testOptimize(FloatArray(nbrVariables) { 0.5f }, this, true, 1.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { 2.0f }, this, false, 4.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { -1.0f }, this, true, -2.0f, 0.0f)
            testOptimize(FloatArray(nbrVariables) { -1.0f }, this, false, -2.0f, 0.0f)
        }

        with(TestModels.MODEL3.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 4.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 2.0f, 1.0f)
        }

        with(TestModels.MODEL5.problem) {
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, true, 10.0f, 1.0f)
            testOptimize(FloatArray(nbrVariables) { 1.0f }, this, false, 2.0f, 0.0f)
        }
    }

    @Test
    fun largeLinearOptimize() {
        fun testOptimize(weights: Vector, p: Problem, maximize: Boolean, target: Float, delta: Float) {
            val optimizer = largeLinearOptimizer(p) ?: return
            val optimizeOrThrow = optimizer.optimizeOrThrow(LinearObjective(maximize, weights))
            assertTrue(p.satisfies(optimizeOrThrow))
            assertEquals(target, optimizeOrThrow dot weights, delta)
        }
        with(TestModels.LARGE_SAT_PROBLEMS[0]) {
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, true, 2944.1f, 20.0f)
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, false, 16.300001f, 10.0f)
        }
        with(TestModels.LARGE_SAT_PROBLEMS[2]) {
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, true, 11475.0f, 10.0f)
            testOptimize(FloatArray(nbrVariables) { -2.0f + it.toFloat() * 0.1f }, this, false, -21.0f, 2.0f)
        }
    }

    @Test
    fun smallLinearOptimizeAssumptionsFeasible() {
        for ((i, p) in TestModels.SAT_PROBLEMS.withIndex()) {
            val optimizer = linearOptimizer(p) ?: return
            val l = optimizer.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables)))
            val rng = Random(i.toLong())
            val assumptions = IntArrayList()
            for (j in 0 until l.size) {
                if (rng.nextBoolean())
                    assumptions.add(l.literal(j))
            }
            val restricted = optimizer.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables)), assumptions)
            assertTrue(p.satisfies(restricted),
                    "Model $i, assumptions ${assumptions.joinToString(",")}")
            assertTrue(Conjunction(assumptions).satisfies(restricted),
                    "Model $i, assumptions ${assumptions.joinToString(",")}")
        }
    }

    @Test
    fun smallLinearOptimizeAssumptionsInfeasible() {
        fun testUnsat(assumptions: IntCollection, p: Problem) {
            val solver = infeasibleLinearOptimizer(p) ?: return
            assertFailsWith(ValidationException::class) {
                solver.optimizeOrThrow(LinearObjective(true, FloatArray(p.nbrVariables)), assumptions)
            }
        }
        with(TestModels.SAT_PROBLEMS[0]) {
            testUnsat(collectionOf(-2, 5), this)
            testUnsat(collectionOf(9, -10, -11), this)
            testUnsat(collectionOf(6, -7, -8), this)
        }
        with(TestModels.SAT_PROBLEMS[3]) {
            testUnsat(collectionOf(-2, 3), this)
            testUnsat(collectionOf(3, 5), this)
            testUnsat(collectionOf(-13, -14, -15), this)
        }
        with(TestModels.SAT_PROBLEMS[4]) {
            testUnsat(collectionOf(6, -5), this)
        }
    }

    @Test
    fun smallLinearOptimizeAssumptions() {
        fun testOptimize(assumptions: IntCollection, weights: Vector, p: Problem, maximize: Boolean, target: Float, delta: Float = max(target * .3f, 0.01f)) {
            val optimizer = linearOptimizer(p) ?: return
            val optimizeOrThrow = optimizer.optimizeOrThrow(LinearObjective(maximize, weights), assumptions)
            assertTrue(Conjunction(assumptions).satisfies(optimizeOrThrow))
            assertTrue(p.satisfies(optimizeOrThrow))
            assertEquals(target, optimizeOrThrow dot weights, delta)
            val optimize = optimizer.optimize(LinearObjective(maximize, weights), assumptions)!!
            assertTrue(Conjunction(assumptions).satisfies(optimize))
            assertTrue(p.satisfies(optimize))
            assertEquals(target, optimize dot weights, delta)
        }

        with(TestModels.SAT_PROBLEMS[0]) {
            testOptimize(collectionOf(2, -3, -6, -7), FloatArray(nbrVariables) { 1.0f }, this, true, 3.0f, 2.0f)
            testOptimize(collectionOf(1, 5, 10), FloatArray(nbrVariables) { 1.0f }, this, false, 6.0f, 1.0f)
            testOptimize(collectionOf(11, 8), FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(collectionOf(5, -8, 10), FloatArray(nbrVariables) { 0.0f }, this, false, 0.0f, 0.0f)
            testOptimize(collectionOf(2, 3, 5), FloatArray(nbrVariables) { it.toFloat() }, this, true, 62.0f, 1.0f)
            testOptimize(collectionOf(8), FloatArray(nbrVariables) { it.toFloat() }, this, false, 16.0f, 3.0f)
            testOptimize(collectionOf(11), FloatArray(nbrVariables) { it.toFloat() * .1f }, this, true, 5.2f, 1.0f)
            testOptimize(collectionOf(-7, -6), FloatArray(nbrVariables) { it.toFloat() * .1f }, this, false, 0.0f, 0.1f)
        }

        with(TestModels.SAT_PROBLEMS[2]) {
            testOptimize(collectionOf(4), FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(collectionOf(4), FloatArray(nbrVariables) { 1.0f }, this, false, 3.0f, 1.0f)
            testOptimize(collectionOf(-4), FloatArray(nbrVariables) { 0.0f }, this, true, 0.0f, 0.0f)
            testOptimize(collectionOf(-4), FloatArray(nbrVariables) { 1.0f }, this, false, 2.0f, 1.0f)
        }
    }

    @Test
    fun timeoutLinearOptimize() {
        val solver = timeoutLinearOptimizer(TestModels.LARGE_SAT_PROBLEMS[1]) ?: return
        assertFailsWith(ValidationException::class) {
            solver.optimizeOrThrow(LinearObjective(true, FloatArray(TestModels.LARGE_SAT_PROBLEMS[1].nbrVariables)))
        }
    }
}


