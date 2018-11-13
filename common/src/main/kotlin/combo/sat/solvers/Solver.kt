package combo.sat.solvers

import combo.model.ValidationException
import combo.sat.Labeling
import combo.sat.Literals
import combo.util.EMPTY_INT_ARRAY

interface Solver : Iterable<Labeling> {

    fun witness(contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling? {
        return try {
            witnessOrThrow(contextLiterals)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * @throws ValidationException
     */
    fun witnessOrThrow(contextLiterals: Literals = EMPTY_INT_ARRAY): Labeling

    override fun iterator() = sequence().iterator()

    fun sequence(contextLiterals: Literals = EMPTY_INT_ARRAY): Sequence<Labeling> {
        return generateSequence { witness(contextLiterals) }
    }

    val config: SolverConfig

}


