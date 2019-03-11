package combo.sat.constraints

import combo.sat.*
import combo.util.*
import kotlin.math.min
import kotlin.random.Random

sealed class ReifiedConstraint(val literal: Literal, open val constraint: Constraint) : Constraint {

    override val priority: Int = 1000

    override fun cacheUpdate(instance: Instance, cacheResult: Int, newLit: Literal) =
            constraint.cacheUpdate(instance, cacheResult, newLit)

    override fun cache(instance: Instance) = constraint.cache(instance)

    override fun isUnit() = false
}

/**
 * ReifiedEquivalent encodes the constraint [literal] <=> [constraint]. That is, the constraint is satisfied when both the
 * [constraint] and [literal] is satisfied or when neither of them are.
 */
class ReifiedEquivalent(literal: Literal, override val constraint: NegatableConstraint) : ReifiedConstraint(literal, constraint), NegatableConstraint {

    init {
        assert(constraint.literals.isNotEmpty()) { "Literals in clause should not be empty." }
        if (literal in constraint.literals || !literal in constraint.literals)
            throw IllegalArgumentException("Literal appears in clause for reified.")
    }

    override val literals: IntCollection = unionCollection(constraint.literals, literal)

    override operator fun not() = ReifiedEquivalent(!literal, constraint)
    override fun offset(offset: Int) = ReifiedEquivalent(literal.offset(offset), constraint.offset(offset))

    override fun violations(instance: Instance, cacheResult: Int): Int {
        val constraintViolations = constraint.violations(instance, cacheResult)
        return if (literal in instance) min(1, constraintViolations)
        else return if (constraintViolations == 0) 1 else 0
    }

    override fun unitPropagation(unit: Literal): NegatableConstraint {
        return when (unit) {
            literal -> constraint
            !literal -> constraint.not()
            else -> {
                val propagated = constraint.unitPropagation(literal)
                when (propagated) {
                    constraint -> this
                    is Tautology -> Conjunction(collectionOf(literal))
                    is Empty -> Conjunction(collectionOf(!literal))
                    else -> ReifiedEquivalent(literal, propagated)
                }
            }
        }
    }

    override fun coerce(instance: MutableInstance, rng: Random) {
        if (literal in instance) constraint.coerce(instance, rng)
        else if (constraint.satisfies(instance)) instance.set(literal)
    }

    fun toCnf(): Sequence<Disjunction> {
        return when (constraint) {
            is Disjunction -> {
                val c1 = constraint.literals.asSequence().map { Disjunction(IntList(intArrayOf(literal, !it))) }
                val c2 = sequenceOf(Disjunction(constraint.literals.mutableCopy().apply { add(!literal) }))
                c1 + c2
            }
            is Conjunction -> {
                val c1 = constraint.literals.asSequence().map { Disjunction(IntList(intArrayOf(!literal, it))) }
                val c2 = sequenceOf(Disjunction((constraint.literals.mutableCopy().map { !it }.apply { add(literal) })))
                c1 + c2
            }
            else -> throw IllegalArgumentException("Cannot convert arbitrary constraint to CNF.")
        }
    }

    override fun toString() = "ReifiedEquivalent($literal <=> $constraint)"
}

/**
 * ReifiedImplies encodes the constraint [literal] => [constraint]. This is also known as the IfThen constraint, i.e.
 * if [literal] then [constraint].
 */
class ReifiedImplies(literal: Literal, constraint: Constraint) : ReifiedConstraint(literal, constraint) {

    init {
        assert(constraint.literals.isNotEmpty()) { "Literals in clause should not be empty." }
        if (literal in constraint.literals || !literal in constraint.literals)
            throw IllegalArgumentException("Literal appears in clause for reified.")
    }

    override val literals: IntCollection = unionCollection(constraint.literals, literal)

    override fun offset(offset: Int) = ReifiedImplies(literal.offset(offset), constraint.offset(offset))

    override fun violations(instance: Instance, cacheResult: Int): Int {
        return if (literal in instance) min(1, constraint.violations(instance, cacheResult))
        else 0
    }

    override fun unitPropagation(unit: Literal): Constraint {
        return when {
            unit == literal -> constraint
            unit.toIx() == literal.toIx() -> Tautology
            else -> {
                val propagated = constraint.unitPropagation(literal)
                when (propagated) {
                    constraint -> this
                    is Tautology -> Tautology
                    is Empty -> Conjunction(collectionOf(!literal))
                    else -> ReifiedImplies(literal, propagated)
                }
            }
        }
    }

    override fun coerce(instance: MutableInstance, rng: Random) {
        if (literal in instance) constraint.coerce(instance, rng)
    }

    override fun toString() = "ReifiedImplies($literal => $constraint)"
}
