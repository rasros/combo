package combo.sat

import combo.math.IntPermutation
import combo.sat.constraints.Conjunction
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class ConstraintTest {
    fun randomExhaustivePropagations(cs: Array<Constraint>) {
        // This test thoroughly test that unit propagation does not change the truth value of an instance.
        // It iteratively calls unitPropagation on each literal in the instance.
        val rng = Random.Default
        for (c in cs) for (i in c.literals) require(i.toIx() <= 4)
        for (l in InstancePermutation(5, BitArrayFactory, rng)) {
            for (c in cs) {
                val c2 = IntPermutation(l.size, rng).iterator().asSequence().fold(c) { s: Constraint, i ->
                    val v = l[i]
                    val cp = s.unitPropagation(i.toLiteral(v))
                    val cSat = c.satisfies(l)
                    val cpSat = cp.satisfies(l)
                    if (cSat != cpSat) {
                        s.unitPropagation(i.toLiteral(v))
                        println()
                    }
                    if (cp.isUnit())
                        assertEquals(c.satisfies(l), Conjunction(collectionOf(*cp.unitLiterals())).satisfies(l))
                    assertEquals(c.satisfies(l), cp.satisfies(l))
                    cp
                }
                assertTrue(c2.isUnit() || c2 == Tautology || c2 == Empty, "$c + ${l.toLiterals().joinToString()} -> $c2")
                if (c2 == Empty) assertFalse(c.satisfies(l))
            }
        }
    }

    fun randomCacheUpdates(instance: MutableInstance, constraint: Constraint) {
        val preCache = constraint.cache(instance)
        assertEquals(constraint.violations(instance), constraint.violations(instance, preCache), "$preCache: $instance")
        val lit = constraint.literals.random(Random)
        val updatedCache = constraint.cacheUpdate(preCache, !instance.literal(lit.toIx()))
        instance.flip(lit.toIx())
        assertEquals(constraint.violations(instance), constraint.violations(instance, updatedCache))
    }
}
