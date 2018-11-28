package combo.sat

interface Sentence : Iterable<Literal> {

    val literals: Literals
    val size get() = literals.size

    override fun iterator() = literals.iterator()

    fun validate() = literals.validate()
    fun isUnit(): Boolean = size == 1
    fun toDimacs(): String = toCnf().map { it.toDimacs() }.joinToString(separator = "\n")

    fun flipsToSatisfy(l: Labeling, s: Labeling? = null): Int
    fun satisfies(l: Labeling, s: Labeling? = null) = flipsToSatisfy(l, s) == 0

    fun propagateUnit(unit: Literal): Sentence
    fun toCnf(): Sequence<Disjunction>

    /**
     * Reuses the clause if possible.
     */
    fun remap(remappedIds: IntArray): Sentence {
        for ((i, l) in literals.withIndex()) {
            literals[i] = remappedIds[literals[i].asIx()].asLiteral(l.asBoolean())
            if (literals[i] < 0)
                throw IllegalArgumentException()
            require(literals[i] >= 0)
        }
        return this
    }

}