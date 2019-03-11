package combo.sat

import combo.model.BasicExpression
import combo.util.EmptyCollection
import combo.util.IntCollection
import combo.util.IntRangeSet
import combo.util.bitCount
import kotlin.math.min
import kotlin.math.sign
import kotlin.random.Random

interface Expression


/**
 * A constraint must be satisfied during solving. See [Literal] for more information on the binary format of variables.
 */
interface Constraint : Expression, Iterable<Literal> {

    val literals: IntCollection
    val size get() = literals.size
    override fun iterator(): IntIterator = literals.iterator()

    val priority: Int

    /**
     * Offsets all the variable indices used by the literals in the constraint to the new ones given by [offset].
     */
    fun offset(offset: Int): Constraint

    fun isUnit(): Boolean = size == 1
    fun unitLiterals(): Literals = literals.toArray()

    /**
     * Returns the number of changes necessary for the constraint to be satisfied, based on a cached result.
     */
    fun violations(instance: Instance, cacheResult: Int): Int

    /**
     * Update the cached result with the changing literal [lit]. This method can only be called if the literal is
     * contained in [literals].
     */
    fun cacheUpdate(instance: Instance, cacheResult: Int, newLit: Literal) = cacheResult + if (newLit in literals) 1 else -1

    /**
     * Calculate the cached result of satisfy value. This will be updated with the [cacheUpdate] and used in the
     * [violations] method. The default implementation gathers the number of matching literals.
     */
    fun cache(instance: Instance): Int {
        var sum = 0
        if (literals is IntRangeSet) {
            val lits = literals as IntRangeSet
            if (lits.min.sign == lits.max.sign) {
                val ix = min(lits.min.toIx(), lits.max.toIx())
                val ints = (size shr 5) + if (size and 0x1F > 0) 1 else 0
                for (i in 0 until ints) {
                    val nbrBits = if (i == ints - 1) size and 0x1F else 32
                    val value = instance.getBits(ix, nbrBits)
                    sum += if (lits.min < 0) nbrBits - Int.bitCount(value) else Int.bitCount(value)
                }
                return sum
            }
        }
        for (it in literals.iterator())
            sum += if (instance.literal(it.toIx()) == it) 1 else 0
        return sum
    }

    /**
     * Returns the number of changes necessary for the constraint to be satisfied.
     * This method is only used for test and debug, in actual solving the cached version is used instead.
     */
    fun violations(instance: Instance): Int = violations(instance, cache(instance))

    /**
     * Returns whether the constraint satisfies the instance.
     */
    fun satisfies(instance: Instance) = violations(instance) == 0

    /**
     * Change the constraint based on the value of a unit literal.
     */
    fun unitPropagation(unit: Literal): Constraint

    fun coerce(instance: MutableInstance, rng: Random)
}

/**
 * A logic constraint can be negated "for free" without increasing the cost solving.
 */
interface NegatableConstraint : Constraint {
    override fun offset(offset: Int): NegatableConstraint
    override fun unitPropagation(unit: Literal): NegatableConstraint
    fun not(): NegatableConstraint
}

/**
 * This constraint is never satisfied.
 */
object Empty : NegatableConstraint, BasicExpression {
    override val priority: Int get() = 0
    override val literals get() = EmptyCollection
    override fun violations(instance: Instance, cacheResult: Int) = Int.MAX_VALUE
    override fun offset(offset: Int) = this
    override operator fun not() = Tautology
    override fun unitPropagation(unit: Literal) = this
    override fun coerce(instance: MutableInstance, rng: Random) {}
    override fun toString() = "Empty"
}

/**
 * This constraint is always satisfied.
 */
object Tautology : NegatableConstraint, BasicExpression {
    override val priority: Int get() = 0
    override val literals get() = EmptyCollection
    override fun violations(instance: Instance, cacheResult: Int) = 0
    override fun offset(offset: Int) = this
    override operator fun not() = Empty
    override fun unitPropagation(unit: Literal) = this
    override fun coerce(instance: MutableInstance, rng: Random) {}
    override fun toString() = "Tautology"
}

