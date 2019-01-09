package combo.model

import combo.math.IntPermutation
import combo.sat.BitFieldLabeling
import combo.sat.solvers.ExhaustiveSolver
import combo.sat.solvers.LocalSearchSolver
import kotlin.random.Random
import kotlin.test.*

class ModelTest {

    companion object {

        val SMALL1 = let {
            // UnitFlag
            // NormalFlag
            // NormalAlternative
            // NormalOr
            val f1 = flag(name = "f1")
            val f2 = flag(name = "f2")
            val f3 = flag(name = "f3")
            val f4 = flag(name = "f4")
            val or1 = multiple(4, 5, 6, name = "or1")
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

        val SMALL2 = let {
            // NullFlag
            // NullOr
            // NullAlternative
            val f1 = flag(name = "f1")
            val f2 = flag(name = "f2")
            val f3 = flag(name = "f3")
            val or1 = multiple(4, 5, 6, name = "or1")
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

        val SMALL3 = let {
            // UnitOptions
            val f1 = flag(name = "f1")
            val f2 = flag(name = "f2")
            val f3 = flag(name = "f3")
            val or1 = multiple(4, 5, 6, name = "or1")
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

        val SMALL4 = let {
            // Small and predictable
            val a = alternative(1, 2)
            val f = flag()
            val o = multiple(1, 2)
            Model.builder().optional(a).optional(o).optional(f).build()
        }

        val SMALL5 = let {
            // Using constraint with all cardinality options
            val a = alternative(1, 2, 3)
            val b = flag()
            val c = flag()
            val d = flag()
            val o = multiple(5, 6, 7, 8)
            Model.builder()
                    .optional(a).optional(b).optional(c).optional(d).optional(o)
                    .constrained(atMost(b, c, d, degree = 1))
                    .constrained(exactly(a.option(2), o.option(8), b, c, degree = 1))
                    .constrained(atLeast(a, b, c, d, o, degree = 1))
                    .build()
        }

        val SMALL_UNSAT1 = let {
            val f1 = flag()
            val f2 = flag()
            val builder = Model.builder().optional(f1).optional(f2)
            builder.constrained(f1 or f2)
            builder.constrained(f1 or !f2)
            builder.constrained(!f1 or f2)
            builder.constrained(!f1 or !f2)
            builder.build()
        }

        val SMALL_UNSAT2 = let {
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

        val SMALL_UNSAT3 = let {
            val builder = Model.builder()
            val a = alternative(1..10)
            builder.optional(a)
            builder.constrained(atLeast(a.option(1), a.option(2), a.option(3), degree = 2))
            builder.build()
        }

        val LARGE1 = let {
            // Alternative hierarchies
            val A1 = alternative(1 until 100, "A1")
            val A2 = alternative(1 until 100, "A2")
            val A3 = alternative(1 until 100, "A3")
            Model.builder(A1).optional(Model.builder(A2).optional(Model.builder(A3))).build()
        }

        val LARGE2 = let {
            // Typical hierarchy
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
            root.constrained(exactly(root.leaves().map { it.value }.toList(), 5))
            root.build()
        }

        val LARGE3 = let {
            // Flag chain
            val root = Model.builder("Flag chain")
            var k = 0
            var next = Model.builder("${k++}")
            root.optional(next)
            for (i in 1..500)
                next = Model.builder("${k++}").also { next.optional(it) }
            root.build()
        }

        val LARGE4 = let {
            // Random disjunctions
            val flags = Array(500) { flag("$it") }
            val root = Model.builder("Random disjunctions")
            for (flag in flags) root.optional(flag)
            val rng = Random(0)
            for (i in 1..1000) {
                root.constrained(DisjunctionBuilder(IntPermutation(flags.size, rng).asSequence()
                        .take(rng.nextInt(8) + 2)
                        .map { flags[it] }
                        .map { if (rng.nextBoolean()) it.not() else it }
                        .toList().toTypedArray()))
            }
            root.build()
        }

        val SMALL_MODELS: Array<Model> = arrayOf(SMALL1, SMALL2, SMALL3, SMALL4, SMALL5)
        val SMALL_UNSAT_MODELS: Array<Model> = arrayOf(SMALL_UNSAT1, SMALL_UNSAT2, SMALL_UNSAT3)
        val LARGE_MODEL: Array<Model> = arrayOf(LARGE1, LARGE2, LARGE3, LARGE4)
    }

    @Test
    fun emptyModel() {
        val m = Model.builder().build()
        assertEquals(m.problem.nbrVariables, 0)
        val l = LocalSearchSolver(m.problem).witnessOrThrow()
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
        val p = SMALL4.problem
        assertEquals(7, p.nbrVariables)
        val l = BitFieldLabeling(1, LongArray(1) { 0b0 })
        for (f in SMALL4.features) {
            if (f.name != "${"$"}root")
                assertNull(SMALL4.toAssignment(l)[f])
        }
        assertTrue(p.satisfies(l))
    }

    @Test
    fun cantUseUnregisteredFeature() {
        assertFailsWith(IllegalArgumentException::class) {
            Model.builder().constrained(flag()).build()
        }
    }

    @Test
    fun cantRegisterFeatureTwice() {
        val f = flag()
        assertFailsWith(IllegalArgumentException::class) {
            Model.builder().optional(f).optional(f).build()
        }
    }
}

