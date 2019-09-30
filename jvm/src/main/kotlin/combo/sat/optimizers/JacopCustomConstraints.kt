package combo.sat.optimizers

import combo.util.AtomicInt
import org.jacop.constraints.PrimitiveConstraint
import org.jacop.core.BooleanVar
import org.jacop.core.IntDomain
import org.jacop.core.IntVar
import org.jacop.core.Store
import org.jacop.floats.core.FloatVar

class BinaryXeqY(val xs: Array<BooleanVar>, val y: IntVar, val isSigned: Boolean = y.min() < 0) : PrimitiveConstraint() {

    private companion object {
        private val idNumber = AtomicInt()
    }

    init {
        numberId = idNumber.incrementAndGet()
        require(xs.size <= 32)
        setScope(xs, arrayOf(y))
    }

    override fun getDefaultConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNotConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNestedNotConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNestedConsistencyPruningEvent() = IntDomain.BOUND

    override fun consistency(store: Store) = prune(store, true)
    override fun notConsistency(store: Store) = prune(store, false)

    private fun prune(store: Store, sat: Boolean) {
        do {
            var min = 0
            var max = 0
            val l = if (isSigned) xs.size - 1 else xs.size
            for (i in 0 until l) {
                min = min or (xs[i].min() shl i)
                max = max or (xs[i].max() shl i)
            }
            if (isSigned) {
                val sign = xs.last()
                if (sign.max() == 1) {
                    min = min or (-1 shl l)
                    if (sign.singleton(1))
                        max = max or (-1 shl l)
                }
            }

            if (sat) y.domain.`in`(store.level, y, min, max)
            else if (!sat && min == max) y.domain.inComplement(store.level, y, min)

            store.propagationHasOccurred = false

            val fixed = y.min() xor y.max()
            for (i in (l - 1) downTo 0) {
                if (((fixed ushr i) and 1) == 0) {
                    val value = (y.min() ushr i) and 1
                    xs[i].domain.`in`(store.level, xs[i], value, value)
                } else break
            }

        } while (store.propagationHasOccurred)
    }

    override fun notSatisfied() = entail(false)
    override fun satisfied() = entail(true)

    private fun entail(sat: Boolean): Boolean {
        var min = 0
        var max = 0
        val l = if (isSigned) xs.size - 1 else xs.size
        for (i in 0 until l) {
            min = min or (xs[i].min() shl i)
            max = max or (xs[i].max() shl i)
        }
        if (isSigned && xs.last().min() == 1) {
            val mask = -1 shl l
            min = min or mask
            max = max or mask
        }
        return if (sat) y.singleton(min) && min == max
        else y.min() > max || y.max() < min
    }

    override fun toString(): String {
        return id() + " (" + xs.joinToString() + ", " + y + " )"
    }
}

class BinaryXeqP(val xs: Array<BooleanVar>, val p: FloatVar) : PrimitiveConstraint() {

    private companion object {
        private val idNumber = AtomicInt()
    }

    init {
        require(xs.size == 32)
        numberId = idNumber.incrementAndGet()
        setScope(xs, arrayOf(p))
    }

    override fun getDefaultConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNotConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNestedNotConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNestedConsistencyPruningEvent() = IntDomain.BOUND

    override fun consistency(store: Store) = prune(store, true)
    override fun notConsistency(store: Store) = prune(store, false)

    private fun prune(store: Store, sat: Boolean) {
        do {
            val minUnsigned = if (xs[31].singleton()) bound(true) else 0.0f
            val maxUnsigned = bound(false)

            val min = if (xs[31].max() == 0) minUnsigned
            else -maxUnsigned
            val max = if (xs[31].min() == 1) -minUnsigned
            else maxUnsigned

            if (sat) p.domain.`in`(store.level, p, min.toDouble(), max.toDouble())
            else if (!sat && min == max) p.domain.inComplement(store.level, p, min.toDouble())
            store.propagationHasOccurred = false

            val fixed: Int = p.min().toFloat().toRawBits() xor p.max().toFloat().toRawBits()
            for (i in 31 downTo 0) {
                if (((fixed ushr i) and 1) == 0) {
                    val value = (p.min().toFloat().toRawBits() ushr i) and 1
                    xs[i].domain.`in`(store.level, xs[i], value, value)
                } else break
            }
        } while (store.propagationHasOccurred)
    }

    override fun notSatisfied() = entail(false)
    override fun satisfied() = entail(true)

    private fun entail(sat: Boolean): Boolean {
        val minUnsigned = if (xs[31].singleton()) bound(true) else 0.0f
        val maxUnsigned = bound(false)

        val min = if (xs[31].max() == 0) minUnsigned
        else -maxUnsigned
        val max = if (xs[31].min() == 1) -minUnsigned
        else maxUnsigned

        // Use Float.compare to get exact -0.0f and 0.0f comparison
        return if (sat) {
            val c1 = min.compareTo(p.min().toFloat())
            val c2 = max.compareTo(p.max().toFloat())
            c1 >= 0 && c2 <= 0
        } else {
            val c1 = min.compareTo(p.max().toFloat())
            val c2 = max.compareTo(p.min().toFloat())
            c1 > 0 || c2 < 0
        }
    }

    private fun bound(lower: Boolean): Float {
        var bits = 0
        if (lower) {
            for (i in 0 until 31)
                bits = bits or (xs[i].min() shl i)
        } else {
            for (i in 0 until 31)
                bits = bits or (xs[i].max() shl i)
        }
        if (bits and 0x7F800000 == 0x7F800000)
            bits = (bits and 0x7EFFFFFF) // Remove NaN and +/- Inf
        return Float.fromBits(bits)
    }

    override fun toString(): String {
        return id() + " (" + xs.joinToString() + ", " + p + " )"
    }
}
