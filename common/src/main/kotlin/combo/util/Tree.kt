package combo.util

interface Tree<out E, T : Tree<E, T>> {

    val value: E
    val children: List<T>

    val isLeaf get() = children.isEmpty()

    fun leaves(): Sequence<T> = asSequence().filter { it.isLeaf }

    fun asSequence(): Sequence<T> = iterator().asSequence()

    fun depth(): Int = 1 + (children.asSequence().map { it.depth() }.max() ?: 0)

    val size: Int get() = children.fold(1) { acc, t: T -> acc + t.size }

    fun contains(element: T) = asSequence().any { it == element }

    fun containsAll(elements: Collection<T>) = !elements.any { !contains(it) }

    fun isEmpty() = false

    @Suppress("UNCHECKED_CAST")
    fun iterator(): Iterator<T> = TreeIterator(this as T)

    private class TreeIterator<T : Tree<*, T>>(root: T) : Iterator<T> {
        private val stack: MutableList<T> = ArrayList<T>().apply {
            this.add(root)
        }

        override fun hasNext() = stack.isNotEmpty()
        override fun next(): T {
            val n = stack.removeAt(0)
            stack.addAll(n.children)
            return n
        }
    }
}