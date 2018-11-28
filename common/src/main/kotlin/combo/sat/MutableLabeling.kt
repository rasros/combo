package combo.sat

interface MutableLabeling : Labeling {
    fun flip(ix: Ix) = set(ix, !get(ix))
    fun set(literal: Literal) = set(literal.asIx(), literal.asBoolean())
    fun setAll(literals: Literals) = literals.forEach { set(it) }
    override fun copy(): MutableLabeling
    operator fun set(ix: Ix, value: Boolean)
}