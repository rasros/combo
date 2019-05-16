package combo.model

import combo.sat.BitArray
import combo.sat.BitArrayBuilder
import combo.sat.InstancePermutation
import combo.sat.constraints.Cardinality
import combo.sat.constraints.Conjunction
import combo.sat.constraints.Relation
import combo.sat.not
import combo.sat.solvers.ExhaustiveSolver
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.*

class ModelTest {
    @Test
    fun cantUseUnregisteredFeature() {
        assertFailsWith(NoSuchElementException::class) {
            Model.model {
                constraint { "m1" and "m2" }
            }
        }
    }

    @Test
    fun cantRegisterFeatureTwice() {
        assertFailsWith(IllegalArgumentException::class) {
            Model.model {
                bool("f1")
                bool("f1")
            }
        }
    }

    @Test
    fun emptyModel() {
        val m1 = Model.model { }
        val m2 = Model.builder { }.build()
        assertEquals(0, m1.problem.nbrVariables)
        assertEquals(0, m2.problem.nbrVariables)
    }

    @Test
    fun model1() {
        with(TestModels.MODEL1) {
            assertEquals(7, index.asSequence().count())
            assertEquals(13, problem.nbrVariables)

            val solver = ModelSolver(this, ExhaustiveSolver(problem))
            val assignments = solver.asSequence(index["f3"]).toList()
            assignments.forEach {
                assertTrue(it.contains("f3"))
                assertTrue(it.contains("alt1"))
                assertEquals("d", it.getString("alt2"))
            }
        }
    }

    @Test
    fun model2() {
        with(TestModels.MODEL2) {
            assertEquals(5, index.asSequence().count())
            assertEquals(10, problem.nbrVariables)
            assertNotNull(problem.constraints.find { c -> c is Conjunction && c.literals.size == 10 })

            val solver = ModelSolver(this, ExhaustiveSolver(problem))
            val assignments = solver.asSequence().toList()
            assertEquals(1, assignments.size)
            assertEquals(2, assignments[0].toMap().size)
            assertTrue(assignments[0].getBoolean("f1"))
            assertTrue(assignments[0].getBoolean("f2"))
        }
    }

    @Test
    fun model3() {
        with(TestModels.MODEL3) {
            assertEquals(2, index.asSequence().count())
            assertEquals(6, problem.nbrVariables)
            assertNotNull(problem.constraints.find { c -> c is Conjunction && c.literals.size == 4 })

            val solver = ModelSolver(this, ExhaustiveSolver(problem))
            val assignments = solver.asSequence()
            assignments.forEach {
                assertEquals("c", it.getString("alt1"))
                assertTrue("b" in it.get<Set<String>>("mult1")!!)
            }
        }
    }

    @Test
    fun model4() {
        with(TestModels.MODEL4) {
            assertEquals(5, index.asSequence().count())
            assertEquals(15, problem.nbrVariables)

            val solver = ModelSolver(this, ExhaustiveSolver(problem))
            val assignments = solver.asSequence(
                    index.find<Alternative<Int>>("a1")!!.option(2),
                    !index["m1"])
            assignments.forEach {
                assertTrue(it.contains("a1"))
                assertFalse(it.contains("m1"))
                assertTrue(it.contains("m2"))
            }
        }
    }

    @Test
    fun model5() {
        with(TestModels.MODEL5) {
            assertEquals(8, index.asSequence().count())
            assertEquals("r1", index.children.first().scopeName)
            assertEquals("sub1", index.children.first().children.first().scopeName)
            assertEquals("sub2", index.children.first().children.first().children.first().scopeName)
            assertEquals("sub3", index.children.first().children.first().children.first().children.first().scopeName)
            assertEquals("sub4", index.children.first().children.first().children.first().children.first().asSequence().first().name)
            assertEquals("r2", index.children.last().scopeName)

            val solver = ModelSolver(this, ExhaustiveSolver(problem))
            val assignments = solver.asSequence()
            assignments.forEach {
                assertTrue(it.contains("f1") || it.contains("sub4"))
                if (it.contains("sub2")) assertTrue(it.contains("sub1"))
            }
        }
    }

    @Test
    fun model6() {
        with(TestModels.MODEL6) {
            assertEquals(12, index.asSequence().count())
            assertEquals(6, problem.constraints.size)
            assertEquals(Relation.values().size - 1,
                    problem.constraints.filter { it is Cardinality }.map { (it as Cardinality).relation }.toSet().size)

            val solver = ModelSolver(this, ExhaustiveSolver(problem))
            val assignments = solver.asSequence(index["0"], index["2"], index["5"], index["8"])
            assignments.forEach {
                assertFalse(it.contains("1"))
            }
        }
    }

    @Test
    fun model7() {
        with(TestModels.MODEL7) {

            assertEquals(4, index.asSequence().count())
            assertEquals(3, problem.constraints.size)

            val solver = ModelSolver(this, ExhaustiveSolver(this.problem))

            val assignments1 = solver.asSequence(index["f2"]).map { it.toMap() }.toList()
            assertEquals(1, assignments1.size)
            assignments1.forEach {
                assertTrue(it.containsKey(index["f1"]))
                assertTrue(it.containsKey(index["f2"]))
            }
            assertNull(solver.witness(index["f1"], !index["sub2"]))

            val assignments2 = solver.asSequence(index["f1"]).map { it.toMap() }.toList()
            assertEquals(2, assignments2.size)
            assignments2.forEach {
                assertTrue(it.containsKey(index["f1"]))
            }
        }
    }

    @Test
    fun unsat1() {
        with(TestModels.UNSAT1) {
            assertEquals(2, index.asSequence().count())
            assertEquals(4, problem.constraints.size)
            assertNull(ExhaustiveSolver(problem).witness())
        }
    }

    @Test
    fun unsat2() {
        with(TestModels.UNSAT2) {
            assertEquals(3, index.asSequence().count())
            assertEquals(8, problem.constraints.size)
            assertNull(ExhaustiveSolver(problem).witness())
        }
    }

    @Test
    fun unsat3() {
        with(TestModels.UNSAT3) {
            assertEquals(1, index.asSequence().count())
            assertEquals(3, problem.constraints.size)
            assertNull(ExhaustiveSolver(problem).witness())
        }
    }

    @Test
    fun unsat4() {
        with(TestModels.UNSAT4) {
            assertEquals(3, index.asSequence().count())
            assertEquals(6, problem.constraints.size)
            assertNull(ExhaustiveSolver(problem).witness())
        }
    }

    @Test
    fun large1() {
        with(TestModels.LARGE1) {
            assertEquals(4, index.asSequence().count())
            assertEquals(6, problem.constraints.size)
            assertEquals(322, problem.nbrVariables)
        }
    }

    @Test
    fun large2() {
        with(TestModels.LARGE2) {
            assertEquals(55, index.asSequence().count())
            assertEquals(107, problem.constraints.size)
            assertEquals(105, problem.nbrVariables)

            val m1: Flag<*> = index.find("Cat 1")!!
            val m2: Multiple<*> = index.find("Cat 1 1")!!
            val con = Conjunction(collectionOf(!m1.toLiteral(index), m2.toLiteral(index)))
            for (i in InstancePermutation(problem.nbrVariables, BitArrayBuilder, Random).asSequence().take(100)) {
                con.coerce(i, Random)
                assertFalse(problem.satisfies(i), "$i")
            }
        }
    }

    @Test
    fun large3() {
        with(TestModels.LARGE3) {
            assertEquals(500, index.asSequence().count())
            assertEquals(499, problem.constraints.size)
            assertEquals(500, problem.nbrVariables)
        }
    }

    @Test
    fun large4() {
        with(TestModels.LARGE4) {
            assertEquals(500, index.asSequence().count())
            assertEquals(1000, problem.constraints.size)
            assertEquals(500, problem.nbrVariables)
        }
    }

    @Test
    fun numeric1() {
        with(TestModels.NUMERIC1) {
            assertEquals(7, index.asSequence().count())
            assertEquals(11, problem.constraints.size)
            assertEquals(103, problem.nbrVariables)
            val instance = BitArray(problem.nbrVariables)
            for (c in problem.constraints)
                c.coerce(instance, Random)
            assertTrue(problem.satisfies(instance))
        }
    }

    @Test
    fun numeric2() {
        with(TestModels.NUMERIC2) {
            assertEquals(3, index.asSequence().count())
            assertEquals(5, problem.constraints.size)
            assertEquals(99, problem.nbrVariables)
            val instance = BitArray(problem.nbrVariables)
            for (c in problem.constraints)
                c.coerce(instance, Random)
            assertTrue(problem.satisfies(instance))
        }
    }

    @Test
    fun numeric3() {
        with(TestModels.NUMERIC3) {
            assertEquals(13, index.asSequence().count())
            assertEquals(20, problem.constraints.size)
            assertEquals(435, problem.nbrVariables)
            val instance = BitArray(problem.nbrVariables)
            for (c in problem.constraints)
                c.coerce(instance, Random)
            assertTrue(problem.satisfies(instance))
        }
    }
}
