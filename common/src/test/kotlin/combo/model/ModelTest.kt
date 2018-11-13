package combo.model

import combo.sat.BitFieldLabeling
import combo.sat.ExhaustiveSolver
import combo.sat.WalkSat
import kotlin.test.*

class ModelTest {

    companion object {

        val small1 = let {
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

        val small2 = let {
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

        val small3 = let {
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

        val small4 = let {
            // Small and predictable
            val a = alternative(1, 2)
            val f = flag()
            val o = or(1, 2)
            Model.builder().optional(a).optional(o).optional(f).build()
        }

        val smallUnsat1 = let {
            val f1 = flag()
            val f2 = flag()
            val f3 = flag()
            val builder = Model.builder().optional(f1).optional(f2).optional(f3)
            builder.constrained(f1 or f2 or f3)
            builder.constrained(f1 or f2 or !f3)
            builder.constrained(f1 or !f2 or f3)
            builder.constrained(f1 or !f2 or !f3)
            builder.constrained(!f1 or f2 or f3)
            builder.constrained(!f1 or f2 or !f3)
            builder.constrained(!f1 or !f2 or f3)
            builder.constrained(!f1 or !f2 or !f3)
            builder.build()
        }

        val large1 = let {
            // Alternative hierarchies
            val A1 = alternative(1 until 10, "A1")
            val A2 = alternative(5 until 100 step 5, "A2")
            val A3 = alternative(5 until 100 step 5, "A3")
            Model.builder(A1).optional(Model.builder(A2).optional(Model.builder(A3))).build()
        }

        val large2 = let {
            val root = Model.builder("Category")
            for (i in 1..5) {
                val sub = Model.builder("Cat$i")
                for (j in 1..10) {
                    val subsub = Model.builder("Cat$i$j")
                    for (k in 1..10) {
                        subsub.optional(flag("Cat$i$j$k"))
                    }
                    subsub.constrained(subsub.value reified or(subsub.children.map { it.value }))
                    sub.optional(subsub)
                }
                sub.constrained(sub.value reified or(sub.children.map { it.value }))
                root.optional(sub)
            }
            root.constrained(atMost(root.leaves().map { it.value }.toList(), 5))
            root.build()
        }

        val smallModels: Array<Model> = arrayOf(small1, small2, small3, small4)
        val smallUnsatModels: Array<Model> = arrayOf(smallUnsat1)
        val largeModels: Array<Model> = arrayOf(large1, large2)

        val hugeModel = let {
            val root = Model.builder("Category")
            for (i in 1..10) {
                val sub = Model.builder("Cat$i")
                for (j in 1..10) {
                    val subsub = Model.builder("Cat$i$j")
                    for (k in 1..10) {
                        subsub.optional(flag("Cat$i$j$k"))
                    }
                    subsub.constrained(subsub.value reified or(subsub.children.map { it.value }))
                    sub.optional(subsub)
                }
                sub.constrained(sub.value reified or(sub.children.map { it.value }))
                root.optional(sub)
            }
            root.mandatory(alternative(1..100))
            root.constrained(exactly(root.leaves().map { it.value }.toList(), 10))
            root.constrained(exactly(root.children.map { it.value }.toList(), 2))
            root.constrained(excludes(root.children[0].value, root.children[4].value))
            root.constrained(excludes(root.children[1].value, root.children[5].value))
            root.build()
        }
    }

    @Test
    fun emptyModel() {
        val m = Model.builder().build()
        assertEquals(m.problem.nbrVariables, 0)
        val l = WalkSat(m.problem).witnessOrThrow()
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
        val p = small4.problem
        assertEquals(7, p.nbrVariables)
        val l = BitFieldLabeling(1, LongArray(1) { 0b0 })
        for (f in small4.features) {
            if (f.name != "${"$"}root")
                assertNull(small4.toAssignment(l)[f])
        }
        assertTrue(p.satisfies(l))
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

