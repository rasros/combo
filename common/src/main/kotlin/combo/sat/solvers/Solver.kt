package combo.sat.solvers

import combo.sat.Labeling
import combo.sat.Literals
import combo.sat.ValidationException
import combo.util.EMPTY_INT_ARRAY

interface Solver : Iterable<Labeling> {

    fun witness(assumptions: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            witnessOrThrow(assumptions)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * @throws ValidationException
     */
    fun witnessOrThrow(assumptions: Literals = EMPTY_INT_ARRAY): Labeling

    override fun iterator() = sequence().iterator()

    fun sequence(assumptions: Literals = EMPTY_INT_ARRAY): Sequence<Labeling> {
        return generateSequence { witness(assumptions) }
    }
}


