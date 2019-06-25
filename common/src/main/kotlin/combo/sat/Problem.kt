package combo.sat

import combo.sat.constraints.Conjunction
import combo.util.*
import kotlin.jvm.JvmOverloads

/**
 * This class sufficiently describes a SAT problem, with the constraints and a count of the number of variables. It also
 * holds an index of variable to constraints in [constraining].
 * @param constraints all constraints that the [Instance]s will be satisfied on.
 * @param encoders contain variable meta information used for encoding vectors for machine learning.
 */
class Problem @JvmOverloads constructor(val constraints: Array<out Constraint>, val encoders: Array<out VariableEncoder>) {

    constructor(binarySize: Int, constraints: Array<out Constraint> = emptyArray()) : this(constraints, arrayOf(BitsEncoder(0, 0, binarySize)))

    val nbrVariables get() = encoders.size
    val nbrConstraints get() = constraints.size
    val vectorSize = encoders.sumBy { it.vectorSize }
    val binarySize = encoders.sumBy { it.binarySize }

    init {
        assert(binarySize == encoders.sumBy { it.binarySize })
    }

    private val constraintIndex: Map<Int, IntArray> = let {
        val map = HashMap<Int, IntList>()
        for ((i, cons) in constraints.withIndex()) {
            for (lit in cons.literals) {
                val ix = lit.toIx()
                assert(ix < binarySize)
                if (!map.containsKey(ix)) map[ix] = IntList()
                map[ix]!!.add(i)
            }
        }
        map.mapValuesTo(HashMap()) { it.value.toArray() }
    }

    /**
     * @param oneHot whether bits/categorical/boolean variables should be encoded as [0,1] or [-1,1]. Default true.
     * @param normalize whether numerical variables should be normalized to [-1,1]. Default true.
     */
    @JvmOverloads
    fun toVector(instance: Instance, oneHot: Boolean = true, normalize: Boolean = true): FloatArray {
        val array = FloatArray(vectorSize)
        for (variable in encoders)
            variable.encode(array, instance, oneHot, normalize)
        return array
    }

    /**
     * Returns the index into the [constraints] array of all constraints with the given variable.
     */
    fun constraining(binaryVarIx: Int) = constraintIndex[binaryVarIx] ?: EMPTY_INT_ARRAY

    /**
     * This method is intended for testing, solvers use [Validator]s instead.
     * @return true if all constraints are satisfied.
     */
    fun satisfies(instance: Instance) = constraints.all { it.satisfies(instance) }

    /**
     * This method is intended for testing, solvers use [Validator]s instead.
     * @return sum of all constraint violations, this will be 0 if the problem is satisfied.
     */
    fun violations(instance: Instance) = constraints.sumBy { it.violations(instance) }

    /**
     * Performs unit propagation on all constraints. Additional unit variables can be added in the [unitLiterals] parameter.
     * New unitLiterals will be added to the set. This method does not change the original problem but can return
     * propagated constraints.
     */
    @JvmOverloads
    fun unitPropagation(unitLiterals: IntHashSet = IntHashSet(), returnConstraints: Boolean = false): Array<Constraint> {

        fun addUnit(units: IntHashSet, unit: Literal): Boolean {
            if (units.contains(!unit)) throw UnsatisfiableException("Unsatisfiable by unit propagation.", literal = unit)
            else return units.add(unit)
        }

        val unitConstraint = ArrayList<Int>()
        if (unitLiterals.isNotEmpty())
            unitConstraint.add(constraints.size + 1)

        val initial: Constraint = if (unitLiterals.isEmpty()) Empty else Conjunction(unitLiterals.copy())
        for (i in constraints.indices)
            if (constraints[i].isUnit()) {
                unitConstraint.add(i)
                for (l in constraints[i].unitLiterals())
                    addUnit(unitLiterals, l)
            }

        val copy = Array(constraints.size) { constraints[it] }

        while (!unitConstraint.isEmpty()) {
            val constraintId = unitConstraint.removeAt(0)
            val constraint = if (constraintId >= constraints.size) initial else copy[constraintId]
            for (unitLit in constraint.unitLiterals()) {
                val unitId = unitLit.toIx()
                val matching = constraining(unitId)
                for (i in matching.indices) {
                    val reduced = copy[matching[i]].unitPropagation(unitLit)
                    if (reduced is Empty) throw UnsatisfiableException("Unsatisfiable by unit propagation.")
                    copy[matching[i]] = reduced
                    if (reduced.isUnit())
                        if (reduced.unitLiterals().any { l -> addUnit(unitLiterals, l) }) unitConstraint.add(matching[i])
                }
            }
        }
        return if (returnConstraints) {
            copy.asSequence()
                    .filter { !it.isUnit() && it != Tautology }
                    .toList()
                    .toTypedArray()
        } else emptyArray()
    }
}
