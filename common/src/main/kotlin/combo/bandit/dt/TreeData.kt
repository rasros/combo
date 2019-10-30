package combo.bandit.dt

import combo.bandit.BanditData
import combo.bandit.univariate.BanditPolicy
import combo.math.VarianceEstimator
import combo.sat.Literals
import combo.sat.toIx
import combo.sat.toLiteral
import combo.util.ArrayQueue
import combo.util.EmptyCollection
import combo.util.collectionOf

/**
 * This class holds the data in the leaf nodes. The order of the [literals] is significant and cannot be changed.
 */
data class NodeData(val literals: Literals, val data: VarianceEstimator) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is NodeData) return false
        return literals.contentEquals(other.literals) && data == other.data
    }

    override fun hashCode(): Int {
        var result = literals.hashCode()
        result = 31 * result + data.hashCode()
        return result
    }
}

class TreeData(val nodes: List<NodeData>) : BanditData, List<NodeData> by nodes {
    override fun migrate(from: IntArray, to: IntArray): TreeData {
        TODO("not implemented")
    }

    fun buildTree(banditPolicy: BanditPolicy): Node {
        val prior = banditPolicy.baseData()
        if (nodes.isEmpty()) return TerminalNode(banditPolicy, EmptyCollection, prior, 0)

        val rootSplit = nodes[0].literals[0].toIx()
        val root = SplitNode(rootSplit, TerminalNode(banditPolicy, collectionOf(rootSplit.toLiteral(true)), prior, 0),
                TerminalNode(banditPolicy, collectionOf(rootSplit.toLiteral(false)), prior, 0))

        // Rebuild tree structure
        for ((literals, data) in this) {
            // This is done in three steps,
            // 1) traverse the tree to the closest parent that the node should be added to,
            // 2) if needed, add more splits until the path is rebuilt,
            // 3) add the leaf node to the parent.

            // 1) traverse the tree
            var r = root
            var stopIx = 0
            while (true) {
                r = if (r.pos is SplitNode && r.ix.toLiteral(true) in literals) r.pos as SplitNode
                else if (r.neg is SplitNode && r.ix.toLiteral(false) in literals) r.neg as SplitNode
                else break
                stopIx++
            }

            // 2) create more parents
            while (stopIx + 1 < literals.size) {
                val ix = literals[stopIx + 1].toIx()
                if (r.ix.toLiteral(true) == literals[stopIx]) {
                    val setLiterals = literals.sliceArray(0 until stopIx) + r.ix.toLiteral(true)
                    r.pos = SplitNode(ix, TerminalNode(banditPolicy, collectionOf(*(setLiterals + ix.toLiteral(true))), prior, 0),
                            TerminalNode(banditPolicy, collectionOf(*(setLiterals + ix.toLiteral(false))), prior, 0))
                    r = r.pos as SplitNode
                } else if (r.ix.toLiteral(false) == literals[stopIx]) {
                    val setLiterals = literals.sliceArray(0 until stopIx) + r.ix.toLiteral(false)
                    r.neg = SplitNode(ix, TerminalNode(banditPolicy, collectionOf(*(setLiterals + ix.toLiteral(true))), prior, 0),
                            TerminalNode(banditPolicy, collectionOf(*(setLiterals + ix.toLiteral(false))), prior, 0))
                    r = r.neg as SplitNode
                } else
                    break
                stopIx++
            }

            // 3) add leaf node
            if (literals[stopIx] == r.ix.toLiteral(true)) {
                if ((r.pos as? LeafNode)?.data === prior)
                    r.pos = TerminalNode(banditPolicy, collectionOf(*literals), data.copy(), 0)
                // else there is junk in the historicData and the current node is ignored
            } else if (literals[stopIx] == r.ix.toLiteral(false)) {
                if ((r.neg as? LeafNode)?.data === prior)
                    r.neg = TerminalNode(banditPolicy, collectionOf(*literals), data.copy(), 0)
                // else there is junk in the historicData and the current node is ignored
            }
        }

        // Replace all TerminalNodes with data == prior with real node
        val queue = ArrayQueue<SplitNode>()
        queue.add(root)
        while (queue.size > 0) {
            val r = queue.remove()
            if (r.pos is SplitNode) queue.add(r.pos as SplitNode)
            else if ((r.pos is TerminalNode) && (r.pos as TerminalNode).data === prior)
                r.pos = TerminalNode(banditPolicy, (r.pos as TerminalNode).literals, banditPolicy.baseData(), 0)
            if (r.neg is SplitNode) queue.add(r.neg as SplitNode)
            else if ((r.neg is TerminalNode) && (r.neg as TerminalNode).data === prior)
                r.neg = TerminalNode(banditPolicy, (r.neg as TerminalNode).literals, banditPolicy.baseData(), 0)
        }
        return root
    }
}

class ForestData(val trees: List<TreeData>)
    : BanditData, List<TreeData> by trees {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}

