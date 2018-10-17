package combo.model

import combo.sat.BitFieldLabeling
import combo.sat.ExhaustiveSolver
import combo.ga.RandomInitializer
import combo.sat.WalkSat
import kotlin.test.*

class ModelTest {

    val m1 = let {
        // UnitFlag
        // NormalFlag
        // NormalAlternative
        // NormalOr
        val f1 = flag(name = "f1")
        val f2 = flag(name = "f2")
        val f3 = flag(name = "f3")
        val f4 = flag(name = "f4")
        val or1 = or(4, 5, 6, name = "or1")
        val alt1 = alternative("a", "c", name = "alt1")
        val alt2 = alternative("a", "d", name = "alt2")
        Model.builder()
                .optional(f1)
                .mandatory(Model.builder(f2)
                        .mandatory(or1)
                        .optional(Model.builder(alt1)
                                .optional(f3)
                                .optional(alt2)))
                .optional(f4)
                .constrained(f3 implies alt2.option("d"))
                .constrained(or1.option(4) implies or1.option(6))
                .build()
    }

    val m2 = let {
        // NullFlag
        // NullOr
        // NullAlternative
        val f1 = flag(name = "f1")
        val f2 = flag(name = "f2")
        val f3 = flag(name = "f3")
        val or1 = or(4, 5, 6, name = "or1")
        val alt1 = alternative("a", "c", name = "alt1")
        Model.builder()
                .optional(Model.builder(f1)
                        .optional(alt1)
                        .optional(or1))
                .mandatory(f2)
                .optional(f3)
                .constrained(!(f2 and or1))
                .constrained(!(f2 and alt1))
                .constrained(!(f2 and f3))
                .build()
    }

    val m3 = let {
        // UnitOptions
        val f1 = flag(name = "f1")
        val f2 = flag(name = "f2")
        val f3 = flag(name = "f3")
        val or1 = or(4, 5, 6, name = "or1")
        val alt1 = alternative("a", "b", "c", name = "alt1")
        Model.builder()
                .optional(Model.builder(f1)
                        .optional(alt1)
                        .optional(or1))
                .optional(Model.builder(f2).optional(f3))
                .constrained(or1.option(5))
                .constrained(alt1.option("c"))
                .build()
    }

    val m4 = let {
        // Alternative hierarchies
        val A1 = alternative(1 until 10, "A1")
        val A2 = alternative(5 until 100 step 5, "A2")
        val A3 = alternative(5 until 100 step 5, "A3")
        Model.builder(A1).optional(Model.builder(A2).optional(Model.builder(A3))).build()
    }

    val m5 = let {
        // Small and predictable
        val a = alternative(1, 2)
        val f = flag()
        val o = or(1, 2)
        Model.builder().optional(a).optional(o).optional(f).build()
    }

    val testModels: Array<Model> = arrayOf(m1, m2, m3, m4, m5)

    @Test
    fun emptyModel() {
        val m = Model.builder().build()
        assertEquals(m.problem.nbrVariables, 0)
        val l = WalkSat(m.problem, init = RandomInitializer()).witnessOrThrow()
        assertEquals(l.size, 0)
        val a = m.toAssignment(l)
        assertEquals(1, a.size)
    }

    @Test
    fun simpleModel() {
        val builder = Model.builder()
        builder.optional(flag())
        builder.optional(flag())
        val m = builder.build()
        assertEquals(3, m.features.size)
        assertEquals(4, ExhaustiveSolver(m.problem).sequence().count())
    }

    @Test
    fun problemCreation() {
        val p = m5.problem
        assertEquals(7, p.nbrVariables)
        val l = BitFieldLabeling(1, LongArray(1) { 0b0 })
        for (f in m5.features) {
            if (f.name != "${"$"}root")
                assertNull(m5.toAssignment(l)[f])
        }
        assertTrue(p.satisfies(l))
    }

    @Test
    fun createdProblemTreeUniqueness() {
        for (p in testModels.map { it.problem }) {
            val values = p.root.asSequence().map { it.value }.toList()
            assertEquals(values.toSet().size, values.size)
        }
    }

    @Test
    fun createdProblemTreeDenseness() {
        for (p in testModels.map { it.problem }) {
            val values = p.root.asSequence().map { it.value }.sorted().toList()
            for ((i, j) in values.withIndex())
                assertEquals(i, j + 1)
        }
    }

    @Test
    fun cantUseUnregisteredFeature() {
        assertFailsWith(ValidationException::class) {
            Model.builder().constrained(flag()).build()
        }
    }

    @Test
    fun cantRegisterFeatureTwice() {
        val f = flag()
        assertFailsWith(ValidationException::class) {
            Model.builder().optional(f).optional(f).build()
        }
    }
}

