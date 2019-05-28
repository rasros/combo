package combo.model

import combo.math.gcd
import combo.math.gcdAll
import combo.sat.Constraint
import combo.sat.Empty
import combo.sat.PropositionalConstraint
import combo.sat.Tautology
import combo.sat.constraints.*
import combo.sat.constraints.Relation.*
import combo.util.*

/**
 * The class is intended to be used as part of the [Model.Builder] and not be instantiated directly (although it can).
 * This class builds constraints in [CNF] form using with 0th order logic. It also contain some standard constraint
 * extensions where [CNF] form would be inefficient, for example cardinality and reification.
 */
@ModelMarker
class ConstraintBuilder(val index: VariableIndex) {

    infix fun Proposition.or(prop: Proposition) = or(this, prop)
    infix fun Proposition.and(prop: Proposition) = and(this, prop)
    infix fun Proposition.implies(prop: Proposition) = !this or prop
    infix fun Proposition.equivalent(prop: Proposition) = (this implies prop) and (prop implies this)
    infix fun Proposition.xor(prop: Proposition) = (this or prop) and (!this or !prop)

    infix fun Proposition.or(ref: String) = or(this, index.resolve(ref))
    infix fun Proposition.and(ref: String) = and(this, index.resolve(ref))
    infix fun Proposition.implies(ref: String) = !this or index.resolve(ref)
    infix fun Proposition.equivalent(ref: String) = index.resolve(ref).let { (this implies it) and (it implies this) }
    infix fun Proposition.xor(ref: String) = index.resolve(ref).let { (this or it) and (!this or !it) }

    infix fun String.or(prop: Proposition) = or(index.resolve(this), prop)
    infix fun String.and(prop: Proposition) = and(index.resolve(this), prop)
    infix fun String.implies(prop: Proposition) = !index.resolve(this) or prop
    infix fun String.equivalent(prop: Proposition) = with(index.resolve(this)) { (this implies prop) and (prop implies this) }
    infix fun String.xor(prop: Proposition) = with(index.resolve(this)) { (this or prop) and (!this or !prop) }

    infix fun String.or(ref: String) = or(index.resolve(this), index.resolve(ref))
    infix fun String.and(ref: String) = and(index.resolve(this), index.resolve(ref))
    infix fun String.implies(ref: String) = !index.resolve(this) or index.resolve(ref)
    infix fun String.equivalent(ref: String) = with(index.resolve(this)) { index.resolve(ref).let { (this implies it) and (it implies this) } }
    infix fun String.xor(ref: String) = with(index.resolve(this)) { index.resolve(ref).let { (this or it) and (!this or !it) } }

    infix fun Value.reifiedImplies(constraint: Constraint) = ReifiedImplies(toLiteral(index), constraint)
    infix fun Value.reifiedEquivalent(constraint: PropositionalConstraint) = ReifiedEquivalent(toLiteral(index), constraint)

    infix fun String.reifiedImplies(constraint: Constraint) = ReifiedImplies(index.resolve(this).toLiteral(index), constraint)
    infix fun String.reifiedEquivalent(constraint: PropositionalConstraint) = ReifiedEquivalent(index.resolve(this).toLiteral(index), constraint)

    fun disjunction(vararg variables: Value): PropositionalConstraint =
            if (variables.isEmpty()) Empty else Disjunction(toLiterals(variables))

    fun conjunction(vararg variables: Value): PropositionalConstraint =
            if (variables.isEmpty()) Tautology else Conjunction(toLiterals(variables))

    fun cardinality(degree: Int, relation: Relation, variables: Array<out Value>): PropositionalConstraint {
        val literals = toLiterals(variables)
        if (relation.isTautology(0, literals.size, degree)) return Tautology
        if (relation.isEmpty(0, literals.size, degree)) return Empty
        return Cardinality(literals, degree, relation)
    }

    fun linear(degree: Int, relation: Relation, weights: IntArray, variables: Array<out Value>): PropositionalConstraint {

        val gcd = gcd(degree, gcdAll(*weights))
        val simplifiedDegree: Int
        val simplifiedWeights: IntArray
        if (gcd > 1) {
            simplifiedDegree = degree / gcd
            simplifiedWeights = weights.mapArray { it / gcd }
        } else {
            simplifiedDegree = degree
            simplifiedWeights = weights
        }

        val literals = IntHashMap()
        var k = 0
        variables.forEach { literals[it.toLiteral(index)] = k++ }

        val linear = Linear(literals, simplifiedWeights, simplifiedDegree, relation)
        if (relation.isTautology(linear.lowerBound, linear.upperBound, simplifiedDegree)) return Tautology
        if (relation.isEmpty(linear.lowerBound, linear.upperBound, simplifiedDegree)) return Empty
        return linear
    }

    fun exactly(degree: Int, variables: Array<out Value>) = cardinality(degree, EQ, variables)
    fun atMost(degree: Int, variables: Array<out Value>) = cardinality(degree, LE, variables)
    fun atLeast(degree: Int, variables: Array<out Value>) = cardinality(degree, GE, variables)

    fun exactly(degree: Int, weights: IntArray, variables: Array<out Value>) = linear(degree, EQ, weights, variables)
    fun atMost(degree: Int, weights: IntArray, variables: Array<out Value>) = linear(degree, LE, weights, variables)
    fun atLeast(degree: Int, weights: IntArray, variables: Array<out Value>) = linear(degree, GE, weights, variables)

    /**
     * Declares all [variables] to be mutually exclusive.
     */
    fun excludes(vararg variables: Value) = cardinality(1, LE, variables)

    fun or(vararg propositions: Proposition): Proposition {
        val literals = IntHashSet()
        var ands: ArrayList<CNF>? = null
        for (prop in propositions) {
            when (prop) {
                is Value -> literals.add(prop.toLiteral(index))
                is Disjunction -> literals.addAll(prop.literals)
                is Conjunction -> {
                    if (ands == null) ands = ArrayList()
                    ands.add(CNF(prop))
                }
                is CNF -> {
                    if (ands == null) ands = ArrayList()
                    ands.add(prop)
                }
                is Tautology -> return Tautology
                is Empty -> {
                }
                else -> throw UnsupportedOperationException("Cannot handle logic expression $prop.")
            }
        }
        val or: Proposition = if (literals.isEmpty()) Tautology else Disjunction(collectionOf(*literals.toArray()))
        return if (ands == null) {
            or
        } else {
            var result = CNF(ands[0].disjunctions)
            for (i in 1 until ands.size)
                result = result.distribute(ands[i])
            if (or is Disjunction)
                result = result.pullIn(or)
            if (result.disjunctions.size == 1) result.disjunctions[0]
            else result
        }
    }

    fun and(vararg propositions: Proposition): Proposition {
        if (propositions.size <= 1) {
            return or(*propositions)
        }
        val disjunctions = ArrayList<Disjunction>()
        for (prop in propositions) {
            when (prop) {
                is Value -> disjunctions.add(Disjunction(collectionOf(prop.toLiteral(index))))
                is Disjunction -> disjunctions.add(prop)
                is Conjunction -> prop.literals.forEach { disjunctions.add(Disjunction(collectionOf(it))) }
                is CNF -> disjunctions.addAll(prop.disjunctions)
                is Tautology -> return Tautology
                is Empty -> {
                }
                else -> throw UnsupportedOperationException("Cannot handle expression $prop.")
            }
        }
        for (d in disjunctions)
            if (d.size > 1) return CNF(disjunctions)

        // All disjunctions have size 1 so we convert to basic constraint conjunction
        val literals = IntArray(disjunctions.size)
        for ((i, d) in disjunctions.withIndex())
            literals[i] = d.literals.first()
        return Conjunction(collectionOf(*literals))
    }

    private fun toLiterals(vars: Array<out Value>): IntCollection {
        // Remove any duplicates
        val set = IntHashSet()
        vars.forEach { set.add(it.toLiteral(index)) }
        return collectionOf(*set.toArray())
    }
}
