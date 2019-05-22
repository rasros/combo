package combo.sat

import combo.math.ImplicationDigraph
import combo.math.IntPermutation
import combo.math.nextGeometric
import combo.math.nextNormal
import combo.sat.constraints.Cardinality
import combo.sat.constraints.Disjunction
import combo.sat.constraints.Relation
import combo.sat.solvers.LinearObjective
import combo.sat.solvers.ObjectiveFunction
import kotlin.jvm.JvmOverloads
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

interface InstanceInitializer<in O : ObjectiveFunction?> {
    fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: O)
}

class ConstraintCoercer<in O : ObjectiveFunction?>(val problem: Problem, val randomizer: InstanceInitializer<O>) : InstanceInitializer<O> {

    private val prioritizedConstraints = problem.constraints.copyOf().apply {
        sortWith(Comparator { a, b ->
            if (a.priority == b.priority) a.literals.size.compareTo(b.literals.size)
            else a.priority.compareTo(b.priority)
        })
    }

    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: O) {
        randomizer.initialize(mi, assumption, rng, function)
        assumption.coerce(mi, rng)
        for (c in prioritizedConstraints) c.coerce(mi, rng)
        assumption.coerce(mi, rng)
    }
}

class ImplicationConstraintCoercer<in O : ObjectiveFunction?>(val problem: Problem,
                                                              val implicationDigraph: ImplicationDigraph,
                                                              val randomizer: InstanceInitializer<O>) : InstanceInitializer<O> {

    private fun is2sat(constraint: Constraint) = (constraint is Disjunction && constraint.literals.size == 2) ||
            (constraint is Cardinality && constraint.degree == 1 && constraint.relation == Relation.LE) ||
            (constraint is Cardinality && constraint.degree == 2 && constraint.relation == Relation.LT)

    val complexConstraints = problem.constraints.filter { !is2sat(it) }.toTypedArray().apply {
        sortWith(Comparator { a, b ->
            if (a.priority == b.priority) a.literals.size.compareTo(b.literals.size)
            else a.priority.compareTo(b.priority)
        })
    }

    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: O) {
        randomizer.initialize(mi, assumption, rng, function)
        assumption.coerce(mi, rng)
        for (i in IntPermutation(problem.nbrVariables, rng)) {
            val lit = mi.literal(i)
            implicationDigraph.propagate(lit, mi)
        }
        for (c in complexConstraints)
            c.coerce(mi, rng)
    }
}

object NoInitializer : InstanceInitializer<ObjectiveFunction?> {
    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: ObjectiveFunction?) {
    }
}

class WeightSet @JvmOverloads constructor(val noiseStd: Float = 0.5f) : InstanceInitializer<LinearObjective> {
    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: LinearObjective) {
        for (i in mi.indices) {
            mi[i] = if (function.maximize) function.weights[i] + rng.nextNormal(0.0f, noiseStd) >= 0
            else function.weights[i] + rng.nextNormal(0.0f, noiseStd) < 0
        }
    }
}

class RandomSet(val pBias: Float = .5f) : InstanceInitializer<ObjectiveFunction?> {
    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: ObjectiveFunction?) {
        for (j in mi.indices) mi[j] = rng.nextFloat() < pBias
    }
}

/**
 * Initializes an [Instance] a whole word (32 bit int) at a time. This approximates pBias to the closest multiple of
 * 1/2^n. For n=1, i.e. pBias = 0.5f the speedup compared to[RandomSet] is about 50x.
 * For n=3 (pBias=0.125 or pBias=0.875) the speedup is 25x.
 */
class WordRandomSet(val start: Int, val steps: Int) : InstanceInitializer<ObjectiveFunction?> {

    constructor(pBias: Float = .5f) : this(if (pBias > 0.5f) 0 else -1, min(sqrt(1 / min(pBias, 1 - pBias)).roundToInt(), 8))

    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: ObjectiveFunction?) {
        val ints = (mi.size shr 5) + if (mi.size and 0x1F > 0) 1 else 0
        var offset = 0
        for (i in 0 until ints) {
            var k = start
            for (j in 1..steps) {
                if (start == 0) {
                    k = k or rng.nextInt()
                    if (k == -1) break
                } else {
                    k = k and rng.nextInt()
                    if (k == 0) break
                }
            }
            if (k == 0) continue
            if (i == ints - 1) {
                val nbrBits = mi.size and 0x1F
                mi.setBits(offset, nbrBits, k and (-1 shl nbrBits).inv())
            } else mi.setBits(offset, 32, k)
            offset += 32
        }
    }
}

/**
 * Useful for very sparse problems where [pBias] is close to 0.
 */
class GeometricRandomSet(val pBias: Float) : InstanceInitializer<ObjectiveFunction?> {
    override fun initialize(mi: MutableInstance, assumption: Constraint, rng: Random, function: ObjectiveFunction?) {
        var index = rng.nextGeometric(pBias) - 1
        while (index < mi.size) {
            mi.flip(index)
            index += rng.nextGeometric(pBias)
        }
    }
}