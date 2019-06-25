package combo.sat

import combo.model.TestModels
import combo.sat.constraints.Conjunction
import combo.sat.solvers.ExhaustiveSolver
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.*

class ValidatorTest {

    private fun checkValidatorState(p: Problem, t: Validator, assumption: Constraint) {
        if (p.satisfies(t.instance) && assumption.satisfies(t.instance)) {
            assertEquals(0, (t.totalUnsatisfied - assumption.violations(t.instance)))
        } else {
            assertEquals((p.constraints.sumBy { it.violations(t.instance) }
                    + assumption.violations(t.instance)), t.totalUnsatisfied)
        }
    }

    @Test
    fun initialize() {
        for (p in TestModels.SAT_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val instance = solver.witnessOrThrow() as MutableInstance
            val copy = instance.copy()
            val validator = Validator(p, instance, Tautology)
            assertEquals(0, validator.totalUnsatisfied)
            assertEquals(copy, instance)
        }
    }

    @Test
    fun initializeSameAssumption() {
        for (p in TestModels.SAT_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val instance = solver.witnessOrThrow() as MutableInstance
            val copy = instance.copy()
            val assumption = Conjunction(collectionOf(instance.literal(Random.nextInt(p.binarySize))))
            val validator = Validator(p, instance, assumption)
            checkValidatorState(p, validator, assumption)
            assertEquals(copy, instance)
        }
    }

    @Test
    fun initializeDifferentAssumption() {
        for (p in TestModels.SAT_PROBLEMS) {
            val solver = ExhaustiveSolver(p)
            val instance = solver.witnessOrThrow() as MutableInstance
            val assumption = Conjunction(collectionOf(!instance.literal(Random.nextInt(p.binarySize))))
            val validator = Validator(p, instance, assumption)
            checkValidatorState(p, validator, assumption)
            assertEquals(1, validator.totalUnsatisfied)
        }
    }

    @Test
    fun flip() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.SAT_PROBLEMS + TestModels.LARGE_SAT_PROBLEMS) {
            val instance = BitArray(p.binarySize)
            WordRandomSet().initialize(instance, Tautology, Random, null)
            val validator = Validator(p, instance, Tautology)
            checkValidatorState(p, validator, Tautology)
            val ix = Random.nextInt(p.binarySize)
            val lit = validator.instance.literal(ix)
            validator.flip(ix)
            checkValidatorState(p, validator, Tautology)
            assertNotEquals(lit, validator.instance.literal(ix))
        }
    }

    @Test
    fun flipMany() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.SAT_PROBLEMS + TestModels.LARGE_SAT_PROBLEMS) {
            val instance = BitArray(p.binarySize)
            WordRandomSet().initialize(instance, Tautology, Random, null)
            val validator = Validator(p, instance, Tautology)
            checkValidatorState(p, validator, Tautology)
            for (lit in 1..10) {
                validator.flip(Random.nextInt(p.binarySize))
                checkValidatorState(p, validator, Tautology)
            }
        }
    }

    @Test
    fun improvementNoChange() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.SAT_PROBLEMS + TestModels.LARGE_SAT_PROBLEMS) {
            val instance = BitArrayBuilder.create(p.binarySize)
            WordRandomSet().initialize(instance, Tautology, Random, null)
            val validator = Validator(p, instance, Tautology)
            val ix = Random.nextInt(p.binarySize)
            val copy = instance.copy()
            validator.improvement(ix)
            assertEquals(copy, instance)
            checkValidatorState(p, validator, Tautology)
        }
    }

    @Test
    fun improvement() {
        for (p in TestModels.UNSAT_PROBLEMS + TestModels.SAT_PROBLEMS + TestModels.LARGE_SAT_PROBLEMS) {
            val instance = BitArrayBuilder.create(p.binarySize)
            WordRandomSet(0.1f).initialize(instance, Tautology, Random, null)
            val validator = Validator(p, instance, Tautology)
            val ix = Random.nextInt(p.binarySize)
            val imp = validator.improvement(ix)
            val preFlips = p.violations(instance)
            validator.flip(ix)
            val postFlips = p.violations(instance)
            assertEquals(postFlips, preFlips - imp, "$postFlips = $preFlips - $imp")
            checkValidatorState(p, validator, Tautology)
        }
    }

    @Test
    fun improvementAssumptions() {
        val p = Problem(10, arrayOf())
        val instance = BitArrayBuilder.create(p.binarySize)
        val assumption = Conjunction(collectionOf(1, 2, 3, 4))
        WordRandomSet().initialize(instance, assumption, Random, null)
        val validator = Validator(p, instance, assumption)
        if (validator.instance[0]) validator.flip(0)
        val imp = validator.improvement(0)
        assertTrue(imp > 0)
        checkValidatorState(p, validator, assumption)
    }

    @Test
    fun randomUnsatisfied() {
        for (p in TestModels.UNSAT_PROBLEMS) {
            val instance = BitArrayBuilder.create(p.binarySize)
            WordRandomSet(0.8f).initialize(instance, Tautology, Random, null)
            val validator = Validator(p, instance, Tautology)
            val sent = validator.randomUnsatisfied(Random)
            assertFalse(sent.satisfies(validator.instance))
        }
    }
}
