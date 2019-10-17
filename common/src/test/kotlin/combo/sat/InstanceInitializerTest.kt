package combo.sat

import combo.math.FallbackVector
import combo.model.TestModels
import combo.sat.constraints.Conjunction
import combo.sat.optimizers.LinearObjective
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.Test


abstract class InstanceInitializerTest {
    abstract fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random)

    @Test
    fun oneBit() {
        initialize(Problem(1), BitArray(1), Tautology, Random)
    }

    @Test
    fun wholeWord() {
        initialize(Problem(32), BitArray(32), Tautology, Random)
    }

    @Test
    fun twoWords() {
        initialize(Problem(64), SparseBitArray(64), Tautology, Random)
    }

    @Test
    fun twoWordsMisaligned() {
        initialize(Problem(100), SparseBitArray(100), Tautology, Random)
    }

    @Test
    fun modelWithAssumptions() {
        initialize(TestModels.MODEL1.problem, BitArray(TestModels.MODEL1.problem.nbrValues), Conjunction(collectionOf(2)), Random)
    }
}

class WordRandomSetTest : InstanceInitializerTest() {
    override fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random) {
        WordRandomSet().initialize(instance, assumption, rng, null)
    }
}

class RandomSetTest : InstanceInitializerTest() {
    override fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random) {
        RandomSet().initialize(instance, assumption, rng, null)
    }
}

class WeightSetTest : InstanceInitializerTest() {
    override fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random) {
        WeightSet().initialize(instance, assumption, rng, LinearObjective(true, FallbackVector(FloatArray(problem.nbrValues))))
    }
}

class GeometricRandomSetTest : InstanceInitializerTest() {
    override fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random) {
        GeometricRandomSet().initialize(instance, assumption, rng, LinearObjective(true, FallbackVector(FloatArray(problem.nbrValues))))
    }
}

class ConstraintCoercerTest : InstanceInitializerTest() {
    override fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random) {
        ConstraintCoercer(problem, RandomSet()).initialize(instance, assumption, rng, null)
    }
}

class ImplicationConstraintCoercerTest : InstanceInitializerTest() {
    override fun initialize(problem: Problem, instance: Instance, assumption: Constraint, rng: Random) {
        ImplicationConstraintCoercer(problem, TransitiveImplications(problem), RandomSet()).initialize(instance, assumption, rng, null)
    }
}
