package combo.sat

import combo.math.ImplicationDigraph
import combo.model.TestModels
import combo.sat.constraints.Conjunction
import combo.sat.solvers.ExhaustiveSolver
import combo.util.EMPTY_INT_ARRAY
import combo.util.collectionOf
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.*

class ValidatorTest {

    private fun checkUnsatisfied(p: Problem, t: Validator, assumption: Constraint) {
        if (p.satisfies(t.instance) && assumption.satisfies(t.instance)) {
            assertEquals(0, (t.totalUnsatisfied - assumption.violations(t.instance)))
        } else {
            assertEquals((p.constraints.sumBy { it.violations(t.instance) }
                    + assumption.violations(t.instance)), t.totalUnsatisfied)
        }
    }

    @Test
    fun initializeSeededRandom() {
        val p = TestModels.MODELS[2].problem
        val validator1 = Validator.build(p, FastRandomSet(), BitArrayBuilder.create(p.nbrVariables), null, EMPTY_INT_ARRAY, Random(0))
        val validator2 = Validator.build(p, FastRandomSet(), BitArrayBuilder.create(p.nbrVariables), null, EMPTY_INT_ARRAY, Random(0))
        assertEquals(validator1.instance, validator2.instance)
    }

    @Test
    fun initializePreSolved() {
        for (p in TestModels.PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val instance = solver.witnessOrThrow() as MutableInstance
            val copy = instance.copy()
            val validator = Validator.build(p, NoInitializer, instance, null, EMPTY_INT_ARRAY, Random(0))
            assertEquals(0, validator.totalUnsatisfied)
            assertEquals(copy, instance)
        }
    }

    @Test
    fun initializePreSolvedSameAssumptions() {
        for (p in TestModels.PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val instance = solver.witnessOrThrow() as MutableInstance
            val copy = instance.copy()
            val assumptions = intArrayOf(instance.literal(Random.nextInt(p.nbrVariables)))
            val validator = Validator.build(p, NoInitializer, instance, null, EMPTY_INT_ARRAY, Random(0))
            checkUnsatisfied(p, validator, Conjunction(collectionOf(*assumptions)))
            assertEquals(copy, instance)
        }
    }

    @Test
    fun initializePreSolvedDifferentAssumptions() {
        for (p in TestModels.PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val instance = solver.witnessOrThrow() as MutableInstance
            val copy = instance.copy()
            val assumptions = intArrayOf(!instance.literal(Random.nextInt(p.nbrVariables)))
            val validator = Validator.build(p, NoInitializer, instance, null, assumptions, Random(0))
            checkUnsatisfied(p, validator, Conjunction(collectionOf(*assumptions)))
            assertEquals(copy, instance)

            val randomInstance = instance.copy()
            val validatorCoerced = Validator.build(p, FastRandomSet(), randomInstance, null, assumptions, Random(0))
            checkUnsatisfied(p, validatorCoerced, Conjunction(collectionOf(*assumptions)))
            assertNotEquals(copy, randomInstance)
        }
    }

    @Test
    fun initialize() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.PROBLEMS + TestModels.LARGE_PROBLEMS) {
            try {
                val instance = BitArray(p.nbrVariables)
                val validator = Validator.build(p,
                        ImplicationConstraintCoercer(p, ImplicationDigraph(p), FastRandomSet()),
                        instance, null, EMPTY_INT_ARRAY, Random)
                checkUnsatisfied(p, validator, Tautology)
            } catch (e: UnsatisfiableException) {
                assertTrue(p in TestModels.UNSAT_PROBLEMS)
                continue
            }
        }
    }

    @Test
    fun initializeAssumptions() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.PROBLEMS + TestModels.LARGE_PROBLEMS) {
            val instance = BitArray(p.nbrVariables)
            val assumptions = IntArray(max(1, Random.nextInt(p.nbrVariables))) { it.toLiteral(Random.nextBoolean()) }
            val validator = Validator.build(p, FastRandomSet(), instance, null, assumptions, Random)
            checkUnsatisfied(p, validator, Conjunction(collectionOf(*assumptions)))
        }
    }

    @Test
    fun flip() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.PROBLEMS + TestModels.LARGE_PROBLEMS) {
            val instance = BitArray(p.nbrVariables)
            val validator = Validator.build(p, FastRandomSet(), instance, null, EMPTY_INT_ARRAY, Random)
            checkUnsatisfied(p, validator, Tautology)
            val ix = Random.nextInt(p.nbrVariables)
            val lit = validator.instance.literal(ix)
            validator.flip(ix)
            checkUnsatisfied(p, validator, Tautology)
            assertNotEquals(lit, validator.instance.literal(ix))
        }
    }

    @Test
    fun flipMany() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.PROBLEMS + TestModels.LARGE_PROBLEMS) {
            val instance = BitArray(p.nbrVariables)
            val validator = Validator.build(p, RandomSet(), instance, null, EMPTY_INT_ARRAY, Random)
            checkUnsatisfied(p, validator, Tautology)
            for (lit in 1..10) {
                validator.flip(Random.nextInt(p.nbrVariables))
                checkUnsatisfied(p, validator, Tautology)
            }
        }
    }

    @Test
    fun improvementNoChange() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.PROBLEMS + TestModels.LARGE_PROBLEMS) {
            val instance = BitArrayBuilder.create(p.nbrVariables)
            val validator = Validator.build(p, FastRandomSet(), instance, null, EMPTY_INT_ARRAY, Random)
            val ix = Random.nextInt(p.nbrVariables)
            val copy = instance.copy()
            validator.improvement(ix)
            assertEquals(copy, instance)
            checkUnsatisfied(p, validator, Tautology)
        }
    }

    @Test
    fun improvement() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.PROBLEMS + TestModels.LARGE_PROBLEMS) {
            val instance = BitArrayBuilder.create(p.nbrVariables)
            val validator = Validator.build(p, FastRandomSet(0.1f), instance, null, EMPTY_INT_ARRAY, Random)
            val ix = Random.nextInt(p.nbrVariables)
            val imp = validator.improvement(ix)
            val preFlips = p.violations(instance)
            validator.flip(ix)
            val postFlips = p.violations(instance)
            assertEquals(postFlips, preFlips - imp, "$postFlips = $preFlips - $imp")
            checkUnsatisfied(p, validator, Tautology)
        }
    }

    @Test
    fun improvementAssumptions() {
        val p = Problem(arrayOf(), 10)
        val instance = BitArrayBuilder.create(p.nbrVariables)
        val validator = Validator.build(p, FastRandomSet(), instance, null, intArrayOf(1, 2, 3, 4), Random)
        if (validator.instance[0]) validator.flip(0)
        val imp = validator.improvement(0)
        assertTrue(imp > 0)
        checkUnsatisfied(p, validator, Conjunction(collectionOf(1, 2, 3, 4)))
    }

    @Test
    fun randomUnsatisfied() {
        for (p in TestModels.UNSAT_PROBLEMS) {
            val instance = BitArrayBuilder.create(p.nbrVariables)
            val validator = Validator.build(p, FastRandomSet(0.8f), instance, null, EMPTY_INT_ARRAY, Random)
            val sent = validator.randomUnsatisfied(Random)
            assertFalse(sent.satisfies(validator.instance))
        }
    }
}
