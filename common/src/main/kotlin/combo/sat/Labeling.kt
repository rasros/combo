package combo.sat

interface Labeling : Iterable<Int> {
    val size: Int
    val indices: IntRange
        get() = 0 until size

    fun copy(): Labeling
    fun asLiteral(ix: Ix): Literal = ix.asLiteral(this[ix])
    operator fun get(ix: Ix): Boolean

    override fun iterator() = object : IntIterator() {
        var i = 0
        override fun hasNext() = i < size
        override fun nextInt() = this@Labeling.asLiteral(i++)
    }

    fun truthIterator() = object : IntIterator() {
        var i = 0

        init {
            while (i < size && !this@Labeling[i]) i++
        }

        override fun hasNext() = i < size
        override fun nextInt() = this@Labeling.asLiteral(i).also {
            i++;
            while (i < size && !this@Labeling[i]) i++
        }
    }

    fun asLiterals() = IntArray(size) { asLiteral(it) }
}