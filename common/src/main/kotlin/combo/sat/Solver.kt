package combo.sat

import combo.model.ValidationException

interface Solver : Iterable<Labeling> {

    fun witness(contextLiterals: Literals = intArrayOf()): Labeling? {
        return try {
            witnessOrThrow(contextLiterals)
        } catch (e: ValidationException) {
            null
        }
    }

    /**
     * @throws ValidationException
     */
    fun witnessOrThrow(contextLiterals: Literals = intArrayOf()): Labeling

    override fun iterator() = sequence().iterator()

    fun sequence(contextLiterals: Literals = intArrayOf()): Sequence<Labeling> {
        return generateSequence { witness(contextLiterals) }
    }

    val config: SolverConfig

}


