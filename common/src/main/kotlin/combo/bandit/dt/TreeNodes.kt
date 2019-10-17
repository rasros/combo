package combo.bandit.dt

import combo.bandit.univariate.BanditPolicy
import combo.math.VarianceEstimator
import combo.sat.*
import combo.util.IntCollection
import combo.util.RandomCache
import combo.util.isEmpty

sealed class Node<E : VarianceEstimator> {
    /**
     * Find the exact node that matches the instance. This will always work unless there is an index out of bounds.
     */
    abstract fun findLeaf(instance: Instance): LeafNode<E>

    /**
     * Finds all leaves that match the given literals. This can possibly return all leaves if for example the
     * literals are empty.
     */
    abstract fun findLeaves(setLiterals: Literals): Sequence<LeafNode<E>>

    abstract fun update(instance: Instance, result: Float, weight: Float): Node<E>
}

class SplitNode<E : VarianceEstimator>(val ix: Int, var pos: Node<E>, var neg: Node<E>) : Node<E>() {

    override fun update(instance: Instance, result: Float, weight: Float): Node<E> {
        if (instance.isSet(ix)) pos = pos.update(instance, result, weight)
        else neg = neg.update(instance, result, weight)
        return this
    }

    override fun findLeaf(instance: Instance) =
            if (instance.isSet(ix)) pos.findLeaf(instance)
            else neg.findLeaf(instance)

    override fun findLeaves(setLiterals: Literals): Sequence<LeafNode<E>> {
        for (l in setLiterals) {
            if (l.toIx() == ix) {
                return if (l.toBoolean()) pos.findLeaves(setLiterals)
                else neg.findLeaves(setLiterals)
            }
        }
        return pos.findLeaves(setLiterals) + neg.findLeaves(setLiterals)
    }
}

abstract class LeafNode<E : VarianceEstimator>(val literals: IntCollection, var data: E, val blocked: RandomCache<IntCollection>?) : Node<E>() {
    override fun findLeaf(instance: Instance) = this
    override fun findLeaves(setLiterals: Literals) = sequenceOf(this)

    /**
     * Checks whether the assumptions can be satisfied by the conjunction formed by literals.
     * Both arrays are assumed sorted.
     */
    fun matches(assumptions: IntCollection): Boolean {
        if (assumptions.isEmpty()) return true
        for (lit in literals)
            if (!lit in assumptions) return false
        return true
    }

    /**
     * The block buffer is a set of failed assumptions that should not be immediately tried again. This is applied
     * in the rare case when the assumptions matches the [literals] and the node is chosen and the solver then tries
     * to generate an instance with the given assumption and fails. In that case the assumptions are added to the
     * blocked buffer and a new node is chosen (this node will not be selected due to the assumptions are blocked).
     */
    fun blocks(assumptions: IntCollection): Boolean {
        if (assumptions.isEmpty() || blocked == null) return false
        // blocked = null
        // findNull == null -> true
        return blocked.find {
            if (it === assumptions) true
            else {
                var covers = true
                for (lit in it) {
                    if (lit !in assumptions) {
                        covers = false
                        break
                    }
                }
                covers
            }
        } != null
    }
}

class TerminalNode<E : VarianceEstimator>(val banditPolicy: BanditPolicy<E>, setLiterals: IntCollection, data: E, blockQueueSize: Int)
    : LeafNode<E>(setLiterals, data, if (blockQueueSize > 0) RandomCache(blockQueueSize) else null) {
    override fun update(instance: Instance, result: Float, weight: Float) =
            this.apply { banditPolicy.update(data, result, weight) }
}
