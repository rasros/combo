package combo.sat.solvers

import combo.math.Vector
import combo.model.TimeoutException
import combo.model.UnsatisfiableException
import combo.sat.*
import combo.sat.optimizers.LinearOptimizer
import combo.util.millis
import org.jacop.constraints.SumBool
import org.jacop.constraints.XeqC
import org.jacop.core.BooleanVar
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.constraints.LinearFloat
import org.jacop.floats.constraints.PmulCeqR
import org.jacop.floats.constraints.XeqP
import org.jacop.floats.core.FloatVar
import org.jacop.satwrapper.SatTranslation
import org.jacop.search.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

// TODO stats
// TODO debug
class JacopSolver(problem: Problem,
                  override var config: SolverConfig = SolverConfig(),
                  var timeout: Long = -1L) : Solver, LinearOptimizer {

    private val store = Store()
    private val vars = Array(problem.nbrVariables) { i ->
        BooleanVar(store, "x$i")
    }
    private val optimizeVars = Array(problem.nbrVariables) { i ->
        FloatVar(store, "xf$i", 0.0, 1.0)
    }

    init {
        val translation = SatTranslation(store)
        for (i in vars.indices) store.impose(XeqP(vars[i], optimizeVars[i]))
        for (c in problem.sentences) {
            if (c is Conjunction)
                for (l in c.literals) store.impose(XeqC(vars[l.asIx()], if (l.asBoolean()) 1 else 0))
            else if (c is Cardinality) {
                val cardVars = Array(c.literals.size) { i ->
                    vars[c.literals[i].asIx()]
                }
                val degree = IntVar(store, c.degree, c.degree)
                store.impose(SumBool(cardVars, c.operator.operator, degree))
            } else if (c is Reified && c.clause is Disjunction) {
                val literal = if (!c.literal.asBoolean()) {
                    val negated = BooleanVar(store, "-x${c.literal.asIx()}")
                    translation.generate_not(vars[c.literal.asIx()], negated)
                    negated
                } else vars[c.literal.asIx()]
                val reified = c.clause
                val pos = reified.literals.asSequence().filter { it.asBoolean() }
                        .map { vars[it.asIx()] }.toList().toTypedArray()
                val neg = reified.literals.asSequence().filter { !it.asBoolean() }
                        .map { vars[it.asIx()] }.toList().toTypedArray()
                translation.generate_clause_reif(pos, neg, literal)
            } else {
                for (d in c.toCnf()) {
                    val pos = d.literals.asSequence().filter { it.asBoolean() }
                            .map { vars[it.asIx()] }.toList().toTypedArray()
                    val neg = d.literals.asSequence().filter { !it.asBoolean() }
                            .map { vars[it.asIx()] }.toList().toTypedArray()
                    translation.generate_clause(pos, neg)
                }
            }
        }
        translation.impose()
    }


    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        try {
            store.setLevel(store.level + 1)
            if (contextLiterals.isNotEmpty()) {
                for (l in contextLiterals) store.impose(XeqC(vars[l.asIx()], if (l.asBoolean()) 1 else 0))
            }
            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(config.debugMode)
                setTimeout(this)
            }
            val result = search.labeling(store, SimpleSelect(vars, MostConstrainedStatic(), BinaryIndomainRandom(config.nextRandom())))
            if (!result) {
                if (search.timeOutOccured) throw TimeoutException(timeout)
                else throw UnsatisfiableException()
            }
            return toLabeling(config.labelingBuilder)
        } finally {
            store.removeLevel(store.level)
            store.setLevel(store.level - 1)
        }
    }

    /**
     * This method is not lazy due to limitations in jacop. Use another solver or [iterateSolutions] instead.
     * If timeout is not set it will only terminate once all solutions are exhausted, which is probably never.
     */
    override fun sequence(contextLiterals: Literals): Sequence<Labeling> {
        val list = ArrayList<Labeling>()
        iterateSolutions(Integer.MAX_VALUE, contextLiterals) { list.add(it) }
        return list.asSequence()
    }

    fun iterateSolutions(limit: Int, contextLiterals: Literals, labelingConsumer: (Labeling) -> Unit) {
        try {
            store.setLevel(store.level + 1)
            if (contextLiterals.isNotEmpty()) {
                for (l in contextLiterals) store.impose(XeqC(vars[l.asIx()], if (l.asBoolean()) 1 else 0))
            }

            val select = SimpleSelect(vars, MostConstrainedStatic(), BinaryIndomainRandom(config.nextRandom()))
            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
                setTimeout(this)
            }
            search.setSolutionListener(object : SimpleSolutionListener<BooleanVar>() {
                override fun recordSolution() {
                    super.recordSolution()
                    labelingConsumer.invoke(toLabeling(config.labelingBuilder))
                }
            })
            search.getSolutionListener().setSolutionLimit(limit)
            search.getSolutionListener().searchAll(true)
            search.labeling(store, select)
        } finally {
            store.removeLevel(store.level)
            store.setLevel(store.level - 1)
        }
    }

    override fun optimizeOrThrow(weights: Vector, contextLiterals: Literals): Labeling {
        try {
            store.setLevel(store.level + 1)
            if (contextLiterals.isNotEmpty()) {
                for (l in contextLiterals) store.impose(XeqC(vars[l.asIx()], if (l.asBoolean()) 1 else 0))
            }
            val max = weights.array.asSequence().map { abs(it) }.sum()
            val cost = FloatVar(store, -max, max)
            store.impose(LinearFloat(optimizeVars, weights.array, "=", cost))
            val objective =
                    if (config.maximize) {
                        val negCost = FloatVar(store, -max, max)
                        store.impose(PmulCeqR(cost, -1.0, negCost))
                        negCost
                    } else cost
            val search = DepthFirstSearch<BooleanVar>().apply {
                setPrintInfo(false)
                setTimeout(this)
            }
            val result = search.labeling(store, SimpleSelect<BooleanVar>(
                    vars, MostConstrainedDynamic<BooleanVar>(), IndomainMax<BooleanVar>()), objective)
            if (!result) {
                if (search.timeOutOccured) {
                    throw TimeoutException(timeout)
                } else {
                    throw UnsatisfiableException()
                }
            }
            return toLabeling(config.labelingBuilder)
        } finally {
            store.removeLevel(store.level)
            store.setLevel(store.level - 1)
        }
    }

    private fun toLabeling(builder: LabelingBuilder<*>): Labeling {
        val nbrPos = vars.count { it.value() == 1 }
        val lits = IntArray(nbrPos)
        var k = 0
        vars.forEachIndexed { i, v -> if (v.value() == 1) lits[k++] = i.asLiteral(true) }
        return builder.build(vars.size, lits)
    }

    private fun setTimeout(search: DepthFirstSearch<*>) {
        if (timeout >= 0L) {
            search.setTimeOut(ceil(timeout / 1000.0).toLong())
            try {
                val cls = Class.forName("org.jacop.search.DepthFirstSearch")
                val fld = cls.getDeclaredField("timeOut")
                fld.isAccessible = true
                fld.set(search, millis() + timeout)
            } catch (e: Exception) {
            }
        }
    }

    class BinaryIndomainRandom(private val rng: Random) : Indomain<BooleanVar> {
        override fun indomain(v: BooleanVar) = rng.nextInt(2)
    }
}


