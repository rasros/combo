@file:JvmName("LabelingInitializers")

package combo.sat

import combo.math.IntPermutation
import combo.math.Rng
import combo.util.EMPTY_INT_ARRAY
import combo.util.Tree
import kotlin.jvm.JvmName

interface LabelingInitializer {
    fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng): MutableLabeling
}

class RandomInitializer : LabelingInitializer {
    override fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng) =
            builder.generate(problem.nbrVariables, rng).also {
                initFixed(problem, it, null)
            }
}

class LookaheadInitializer(problem: Problem, reversed: Boolean = true) : LabelingInitializer {

    private val bfsRows: Array<IntArray>

    init {
        data class VariableTree(override val value: Int, override val children: ArrayList<VariableTree> = ArrayList())
            : Tree<Int, VariableTree>

        val ts = Array(problem.implicationGraph.size / 2) { VariableTree(it) }
        val root = VariableTree(-1)
        for (i in 0 until problem.implicationGraph.size step 2) {
            val implications = problem.implicationGraph[i].filter { it.asBoolean() }
            if (implications.isEmpty()) {
                root.children.add(ts[i.asIx()])
            } else {
                implications.forEach {
                    ts[it.asIx()].children.add(ts[i.asIx()])
                }
            }
        }
        val depth = root.depth() - 1
        bfsRows = Array(depth) { EMPTY_INT_ARRAY }
        fun buildBfsRows(ds: Array<IntArray>, t: VariableTree, depth: Int) {
            if (t.value >= 0)
                ds[depth] = ds[depth] + t.value
            t.children.forEach { buildBfsRows(ds, it, depth + 1) }
        }
        buildBfsRows(bfsRows, root, -1)
        if (reversed) bfsRows.reverse()
    }

    override fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng): MutableLabeling {
        val l = builder.build(problem.nbrVariables)
        val s = builder.build(problem.nbrVariables)
        initFixed(problem, l, s)
        for (row in bfsRows) {
            for (ix in IntPermutation(row.size, rng).iterator()) {
                val lit = ix.asLiteral(rng.boolean())
                s[ix] = true
                l.set(lit)
                if (!problem.index.sentencesWith(ix).all { problem.sentences[it].satisfies(l, s) }) {
                    l.set(!lit)
                }
            }
        }
        return l
    }
}

private fun initFixed(problem: Problem, l: MutableLabeling, s: MutableLabeling?) {
    problem.sentences.forEach { sent ->
        if (sent is Conjunction) {
            l.setAll(sent.literals)
            sent.literals.forEach {
                if (s != null) s[it.asIx()] = true
                problem.implicationGraph[it]
            }
        }
    }
}
