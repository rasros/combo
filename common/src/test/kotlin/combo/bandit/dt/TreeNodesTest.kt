package combo.bandit.dt

import combo.math.RunningVariance
import combo.math.VarianceEstimator
import combo.sat.BitArray
import combo.sat.Instance
import combo.sat.toLiteral
import combo.test.assertContentEquals
import combo.util.IntCollection
import combo.util.RandomCache
import combo.util.collectionOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreeNodesTest {
    @Test
    fun findLeaf() {
        val node = SplitNode(1,
                SplitNode(0, TestNode(collectionOf(1.toLiteral(true), 0.toLiteral(true))),
                        TestNode(collectionOf(1.toLiteral(true), 0.toLiteral(false)))),
                TestNode(collectionOf(1.toLiteral(false))))

        assertEquals(node.neg, node.findLeaf(BitArray(2, intArrayOf(0b00))))
        assertEquals(node.neg, node.findLeaf(BitArray(2, intArrayOf(0b01))))
        assertEquals((node.pos as SplitNode).neg, node.findLeaf(BitArray(2, intArrayOf(0b10))))
        assertEquals((node.pos as SplitNode).pos, node.findLeaf(BitArray(2, intArrayOf(0b11))))
    }

    @Test
    fun findLeaves() {
        val node = SplitNode(1,
                SplitNode(0, TestNode(collectionOf(1.toLiteral(true), 0.toLiteral(true))),
                        TestNode(collectionOf(1.toLiteral(true), 0.toLiteral(false)))),
                TestNode(collectionOf(1.toLiteral(false))))

        val t1 = (node.pos as SplitNode).pos
        val t2 = (node.pos as SplitNode).neg
        val t3 = node.neg

        //       / t1
        //      0
        //    /  \ t2
        // 1 /
        //   \ t3

        assertContentEquals(listOf(t1, t2, t3), node.findLeaves(IntArray(0)).toList())
        assertContentEquals(listOf(t2, t3), node.findLeaves(intArrayOf(0.toLiteral(false))).toList())
        assertContentEquals(listOf(t1, t3), node.findLeaves(intArrayOf(0.toLiteral(true))).toList())
        assertContentEquals(listOf(t3), node.findLeaves(intArrayOf(1.toLiteral(false))).toList())
        assertContentEquals(listOf(t1, t2), node.findLeaves(intArrayOf(1.toLiteral(true))).toList())
        assertContentEquals(listOf(t3), node.findLeaves(intArrayOf(0.toLiteral(false), 1.toLiteral(false))).toList())
        assertContentEquals(listOf(t2), node.findLeaves(intArrayOf(0.toLiteral(false), 1.toLiteral(true))).toList())
        assertContentEquals(listOf(t3), node.findLeaves(intArrayOf(0.toLiteral(true), 1.toLiteral(false))).toList())
        assertContentEquals(listOf(t1), node.findLeaves(intArrayOf(0.toLiteral(true), 1.toLiteral(true))).toList())
    }

    @Test
    fun blocks() {
        val t = TestNode(collectionOf(1, 3, -5), RunningVariance(), RandomCache(1))

        val rng = Random
        t.blocked?.add(rng, collectionOf())
        assertTrue(t.blocks(collectionOf(1)))
        assertTrue(t.blocks(collectionOf(-1)))

        t.blocked?.add(rng, collectionOf(1))
        assertTrue(t.blocks(collectionOf(1)))
        assertTrue(t.blocks(collectionOf(1, 2)))
        assertFalse(t.blocks(collectionOf(-1)))
        assertFalse(t.blocks(collectionOf(2)))

        t.blocked?.add(rng, collectionOf(-1, 2))
        assertTrue(t.blocks(collectionOf(-1, 2)))
        assertFalse(t.blocks(collectionOf(-1, -2)))
        assertFalse(t.blocks(collectionOf(-1)))
        assertFalse(t.blocks(collectionOf(2)))

        t.blocked?.add(rng, collectionOf(1, 3, -5))
        assertTrue(t.blocks(collectionOf(1, 3, -5)))
        assertTrue(t.blocks(collectionOf(1, 3, -5, 2)))
        assertFalse(t.blocks(collectionOf(1, -3, -5)))
        assertFalse(t.blocks(collectionOf(1, -3, -5)))
    }

    @Test
    fun matches() {
        val t = TestNode(collectionOf(1, 3, -5))
        assertTrue(t.matches(collectionOf(1)))
        assertTrue(t.matches(collectionOf(1, 4)))
        assertTrue(t.matches(collectionOf(3, -4)))
        assertTrue(t.matches(collectionOf(1, 3, -5)))
        assertTrue(t.matches(collectionOf(-5, 3)))
    }

    @Test
    fun matchesNot() {
        val t = TestNode(collectionOf(1, 3, -5))
        assertFalse(t.matches(collectionOf(-1)))
        assertFalse(t.matches(collectionOf(-1, 4)))
        assertFalse(t.matches(collectionOf(-3, -4)))
        assertFalse(t.matches(collectionOf(1, 3, 5)))
        assertFalse(t.matches(collectionOf(-5, 3, -1)))
    }
}

private class TestNode(setLiterals: IntCollection, data: VarianceEstimator = RunningVariance(), blocked: RandomCache<IntCollection>? = null)
    : LeafNode<VarianceEstimator>(setLiterals, data, blocked) {
    override fun update(instance: Instance, result: Float, weight: Float) = this
}