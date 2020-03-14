package combo.demo.models.topcategory

import combo.model.Model
import combo.model.Root
import combo.sat.constraints.Relation
import java.io.InputStreamReader

fun topCategoryModel(): Model {
    class Node(val value: String, val children: MutableList<Node> = ArrayList())

    val lines = InputStreamReader(Node::class.java.getResourceAsStream("/combo/demo/models/tc_attributes.txt")).readLines()
    val categories = lines.subList(0, lines.size - 3)
    val lookup = HashMap<String, Node>()
    val rootNodes = ArrayList<Node>()
    for (category in categories) {
        val ourNode = lookup[category] ?: Node(category).apply { lookup[category] = this }
        if (!category.contains("/")) rootNodes.add(ourNode)
        else {
            val parentCategory = category.substringBeforeLast("/")
            val parentNode = lookup[parentCategory]
                    ?: Node(parentCategory).apply { lookup[parentCategory] = this }
            parentNode.children.add(ourNode)
        }
    }
    val tree = Node("/", rootNodes)

    fun toModel(category: Node, depth: Int = 0): Model =
            Model.model(category.value) {
                if (category.value != "/") bool("${category.value}/Top-level")
                for (n in category.children) {
                    if (n.children.isEmpty()) bool(n.value)
                    else addModel(toModel(n, depth + 1))
                }
                if (depth == 0) {
                    scope.scopesAsSequence().forEach {
                        impose {
                            if (it.reifiedValue is Root) disjunction(*it.variables.toTypedArray())
                            else it.reifiedValue reifiedEquivalent disjunction(*it.variables.toTypedArray())
                        }
                    }
                    val leaves = scope.asSequenceWithScope()
                            .filterNot { it.second.children.any { c -> c.reifiedValue == it.first } }
                            .map { it.first }.toList().toTypedArray()
                    val k = int("Top-k", 1, 100)
                    impose { cardinality(k, Relation.LE, *leaves) }
                    nominal("domain", "D1", "D2", "D3")
                }
            }
    return toModel(tree)
}