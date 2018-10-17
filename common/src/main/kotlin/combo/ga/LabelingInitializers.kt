@file:JvmName("LabelingInitializers")

package combo.ga

import combo.math.IntPermutation
import combo.math.Rng
import combo.sat.*
import kotlin.jvm.JvmName

interface LabelingInitializer {
    fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng): MutableLabeling
}

class RandomInitializer : LabelingInitializer {
    override fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng) =
            builder.generate(problem.nbrVariables, rng).also {
                problem.initFixed(it, null)
            }
}

class ImplicationInitializer : LabelingInitializer {
    override fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng): MutableLabeling {
        val l = builder.build(problem.nbrVariables)
        val s = builder.build(problem.nbrVariables)
        problem.initFixed(l, s)
        val perm = IntPermutation(problem.nbrVariables, rng)
        for (i in 0 until problem.nbrVariables) {
            val ix = perm.encode(i)
            if (s[ix]) continue
            s[ix] = true
            l[ix] = rng.boolean()
            l.setAll(problem.implicationGraph[l.asLiteral(ix)])
            problem.implicationGraph[l.asLiteral(ix)].forEach { s[it.asIx()] = true }
        }
        return l
    }
}

class TreeInitializerDFS : LabelingInitializer {

    override fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng): MutableLabeling {
        val l = builder.build(problem.nbrVariables)
        val s = builder.build(problem.nbrVariables)
        val t = builder.build(problem.nbrVariables)
        problem.initFixed(l, s)
        dfs(problem, problem.root, l, s, t, rng)
        return l
    }

    private fun dfs(problem: Problem, tree: Problem.Tree, l: MutableLabeling, s: MutableLabeling, t: MutableLabeling, rng: Rng) {
        with(problem) {
            if (tree.value >= 0) {
                label(l, s, t, tree.value.asLiteral(rng.boolean()))
            }
            if (!tree.isLeaf) {
                val perm = IntPermutation(tree.children.size, rng)
                for (i in 0 until tree.children.size) {
                    val ix = perm.encode(i)
                    dfs(problem, tree.children[ix], l, s, t, rng)
                }
            }
        }
    }
}

class TreeInitializerBFS(problem: Problem, reversed: Boolean = true) : LabelingInitializer {

    private val treeRows: Array<IntArray>

    init {
        val depth = problem.root.depth()
        val treeRowsFull = Array(depth) { IntArray(0) }
        fun buildTree(ds: Array<IntArray>, t: Problem.Tree, depth: Int) {
            if (t.value >= 0)
                ds[depth] = ds[depth] + t.value
            t.children.forEach { buildTree(ds, it, depth + 1) }
        }
        buildTree(treeRowsFull, problem.root, 0)
        treeRows = treeRowsFull.sliceArray(treeRowsFull.indexOfFirst { it.isNotEmpty() } until depth)
        if (reversed) treeRows.reverse()
    }

    override fun generate(problem: Problem, builder: LabelingBuilder<*>, rng: Rng): MutableLabeling {
        val l = builder.build(problem.nbrVariables)
        val s = builder.build(problem.nbrVariables)
        val t = builder.build(problem.nbrVariables)
        problem.initFixed(l, s)
        with(problem) {
            for (row in treeRows) {
                val perm = IntPermutation(row.size, rng)
                for (i in 0 until row.size) {
                    val ix = row[perm.encode(i)]
                    label(l, s, t, ix.asLiteral(rng.boolean()))
                }
            }
        }
        return l
    }
}

private fun Problem.label(l: MutableLabeling, s: MutableLabeling, t: MutableLabeling, lit: Literal) {
    return if (tryLabel(l, s, t, lit, true)) {
        finalizeLabel(l, s, t, lit)
    } else {
        backtrack(l, s, t, lit)
        tryLabel(l, s, t, !lit, false)
        finalizeLabel(l, s, t, !lit)
    }
}

private fun Problem.tryLabel(l: MutableLabeling, s: MutableLabeling, t: MutableLabeling, lit: Literal, check: Boolean): Boolean {
    val ix = lit.asIx()
    if (s[ix])
        return true
    else if (t[ix])
        return lit == l.asLiteral(ix)
    l.set(lit)
    t[ix] = true
    return (!check || index.sentencesWith(ix).all { sentences[it].satisfies(l, t) }) &&
            implicationGraph[lit].all { tryLabel(l, s, t, it, check) }
}

private fun Problem.backtrack(l: MutableLabeling, s: MutableLabeling, t: MutableLabeling, lit: Literal) {
    val ix = lit.asIx()
    if (t[ix] != s[ix]) {
        t[ix] = s[ix]
        implicationGraph[lit].forEach { backtrack(l, s, t, l.asLiteral(it.asIx())) }
    }
}

private fun Problem.finalizeLabel(l: MutableLabeling, s: MutableLabeling, t: MutableLabeling, lit: Literal) {
    val ix = lit.asIx()
    if (t[ix] && !s[ix]) {
        s[ix] = true
        implicationGraph[lit].forEach { finalizeLabel(l, s, t, l.asLiteral(it.asIx())) }
    }
}

private fun Problem.initFixed(l: MutableLabeling, s: MutableLabeling?) {
    sentences.forEach { sent ->
        if (sent is Conjunction) {
            l.setAll(sent.literals)
            sent.literals.forEach {
                if (s != null) s[it.asIx()] = true
                implicationGraph[it]
            }
        }
    }
}
