package combo.sat

import combo.sat.constraints.*
import combo.util.IntHashSet
import combo.util.IntList
import combo.util.isNotEmpty
import kotlin.math.min

/**
 * This class calculates the transitive closure of a graph in adjacency list form.
 * This is a recursive free implementation of the Simple_SC algorithm by Esko Nuutilas,
 * see http://www.cs.hut.fi/~enu/thesis.html
 */
class ImplicationDigraph(val nbrVariables: Int, implications: Map<Int, IntArray>) {

    /**
     * This constructor calculates the initial edges in the graph.
     */
    constructor(problem: Problem) : this(problem.binarySize, HashMap<Int, IntHashSet>().let { map ->
        for (constraint in problem.constraints) {
            if (constraint is Disjunction && constraint.size == 2) {
                val array = constraint.literals.toArray()
                map.getOrPut(!array[0]) { IntHashSet() }.add(array[1])
                map.getOrPut(!array[1]) { IntHashSet() }.add(array[0])
            } else if ((constraint is Cardinality && constraint.degree == 1 && constraint.relation == Relation.LE) ||
                    (constraint is Cardinality && constraint.degree == 2 && constraint.relation == Relation.LT)) {
                val array = constraint.literals.toArray()
                for (i in array.indices) {
                    for (j in (i + 1) until array.size) {
                        map.getOrPut(array[i]) { IntHashSet() }.add(!array[j])
                        map.getOrPut(array[j]) { IntHashSet() }.add(!array[i])
                    }
                }
            } else if (constraint is ReifiedEquivalent && constraint.constraint is Disjunction) {
                for (literal in constraint.constraint.literals) {
                    map.getOrPut(literal) { IntHashSet() }.add(constraint.literal)
                    map.getOrPut(!constraint.literal) { IntHashSet() }.add(!literal)
                }
            } else if (constraint is ReifiedEquivalent && constraint.constraint is Conjunction) {
                for (literal in constraint.constraint.literals) {
                    map.getOrPut(!literal) { IntHashSet() }.add(!constraint.literal)
                    map.getOrPut(constraint.literal) { IntHashSet() }.add(literal)
                }
            }
        }
        map.mapValues { it.value.toArray() }
    })

    private val trueImplications = arrayOfNulls<SparseBitArray?>(nbrVariables * 2)
    private val falseImplications = arrayOfNulls<SparseBitArray?>(nbrVariables * 2)

    fun trueImplications(literal: Int) = trueImplications[toArrayIndex(literal)]
    fun falseImplications(literal: Int) = falseImplications[toArrayIndex(literal)]

    fun propagate(literal: Int, instance: MutableInstance) {
        trueImplications(literal)?.run { instance.or(this) }
        falseImplications(literal)?.run { instance.andNot(this) }
    }

    fun toArray(literal: Int): IntArray {
        val set = IntHashSet()
        trueImplications[toArrayIndex(literal)]?.forEach { set.add(it.toLiteral(true)) }
        falseImplications[toArrayIndex(literal)]?.forEach { set.add(it.toLiteral(false)) }
        return set.toArray()
    }

    init {
        for ((from, edges) in implications) {
            val ix = toArrayIndex(from)
            for (to in edges) {
                if (to.toBoolean()) {
                    if (trueImplications[ix] == null) trueImplications[ix] = SparseBitArray(nbrVariables)
                    trueImplications[ix]!![to.toIx()] = true
                } else {
                    if (falseImplications[ix] == null) falseImplications[ix] = SparseBitArray(nbrVariables)
                    falseImplications[ix]!![to.toIx()] = true
                }
            }
        }

        val visited = BooleanArray(nbrVariables * 2)
        val component = IntArray(nbrVariables * 2) { -1 }
        val root = IntArray(nbrVariables * 2)

        var components = 0
        val stack = IntList()

        // This is a stack simulation to avoid recursion and stack overflow for large graphs (n>10000)
        val callStack = IntList()

        for (v in 0 until nbrVariables * 2) {
            if (visited[v]) continue
            callStack.add(v)
            callStack.add(0)
            while (callStack.isNotEmpty()) {
                val i = callStack.removeAt(callStack.size - 1)
                val v1 = callStack.removeAt(callStack.size - 1)
                if (i == 0) {
                    visited[v1] = true
                    root[v1] = v1
                    stack.add(v1)
                }
                var recurse = false
                for (j in i until (implications[toLiteral(v1)]?.size ?: 0)) {
                    val w = toArrayIndex(implications[toLiteral(v1)]!![j])
                    if (!visited[w]) {
                        callStack.add(v1)
                        callStack.add(j + 1)
                        callStack.add(w)
                        callStack.add(0)
                        recurse = true
                        break
                    }
                    if (component[w] < 0) root[v1] = min(root[v1], root[w])
                    addImplications(v1, w)
                }
                if (recurse) continue
                if (root[v1] == v1) {
                    var w: Int
                    do {
                        w = stack.removeAt(stack.size - 1)
                        component[w] = components

                        val ti = trueImplications[v1]
                        val fi = falseImplications[v1]
                        trueImplications[w] = ti
                        falseImplications[w] = fi

                        // If a variable and its negation belong to the same strongly connected component, ie. x <=> !x
                        if (component[w] == component[w xor 1])
                            throw UnsatisfiableException("Unsatisfiable during initialization of implication graph (2-sat).")

                    } while (w != v1)
                    components++
                }
                if (callStack.isNotEmpty()) {
                    val w = v1
                    val v2 = callStack[callStack.size - 2]
                    root[v2] = min(root[v2], root[w])
                    addImplications(v2, w)
                }
            }
        }
    }

    private fun addImplications(to: Int, from: Int) {
        if (trueImplications[to] == null && trueImplications[from] != null) trueImplications[to] = SparseBitArray(nbrVariables)
        if (trueImplications[from] != null) trueImplications[to]!!.or(trueImplications[from]!!)
        if (falseImplications[to] == null && falseImplications[from] != null) falseImplications[to] = SparseBitArray(nbrVariables)
        if (falseImplications[from] != null) falseImplications[to]!!.or(falseImplications[from]!!)
    }

    private fun toArrayIndex(literal: Int) = (literal.toIx() shl 1) + if (literal.toBoolean()) 0 else 1
    private fun toLiteral(arrayIndex: Int) = (arrayIndex shr 1).toLiteral(arrayIndex and 1 == 0)
}