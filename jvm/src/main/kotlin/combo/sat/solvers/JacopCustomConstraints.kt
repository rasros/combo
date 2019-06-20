package combo.sat.solvers

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
        setScope(xs, arrayOf(y))
    }

    override fun getDefaultConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNotConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNestedNotConsistencyPruningEvent() = IntDomain.BOUND
    override fun getDefaultNestedConsistencyPruningEvent() = IntDomain.BOUND

    override fun consistency(store: Store) {
        prune(store, true)
    }

    override fun notConsistency(store: Store) {
        prune(store, false)
    }

    private fun prune(store: Store, sat: Boolean) {
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
        else if (!sat && min == max)
            y.domain.inComplement(store.level, y, min)
    }

    override fun notSatisfied() = entail(false)

    override fun satisfied(): Boolean {
        if (!grounded()) return false
        return entail(true)
    }

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

    override fun consistency(store: Store) {
        prune(store, true)
    }

    override fun notConsistency(store: Store) {
        prune(store, false)
    }

    private fun prune(store: Store, sat: Boolean) {
        var minBits = 0
        var maxBits = 0
        for (i in 0 until 31) {
            minBits = minBits or (xs[i].min() shl i)
            maxBits = maxBits or (xs[i].max() shl i)
        }
        if (maxBits and 0x7F800000 == 0x7F800000)
            maxBits = (maxBits and 0x7EFFFFFF) // Remove NaN and +/- Inf
        if (minBits and 0x7F800000 == 0x7F800000)
            minBits = (minBits and 0x7EFFFFFF) // Remove NaN and +/- Inf

        val maxUnsigned = Float.fromBits(maxBits)
        val minUnsigned = Float.fromBits(minBits)

        val max = if (xs[31].min() == 1) -minUnsigned else maxUnsigned
        val min = if (xs[31].max() == 1) -maxUnsigned else minUnsigned

        if (sat) p.domain.`in`(store.level, p, min.toDouble(), max.toDouble())
        else if (!sat && min == max)
            p.domain.inComplement(store.level, p, min.toDouble())

    }

    override fun notSatisfied() = entail(false)

    override fun satisfied(): Boolean {
        if (!grounded()) return false
        return entail(true)
    }

    private fun entail(sat: Boolean): Boolean {
        var minBits = 0
        var maxBits = 0
        for (i in 0 until 31) {
            minBits = minBits or (xs[i].min() shl i)
            maxBits = maxBits or (xs[i].max() shl i)
        }
        if (maxBits and 0x7F800000 == 0x7F800000)
            maxBits = (maxBits and 0x7EFFFFFF) // Remove NaN and +/- Inf
        if (minBits and 0x7F800000 == 0x7F800000)
            minBits = (minBits and 0x7EFFFFFF) // Remove NaN and +/- Inf

        val maxUnsigned = Float.fromBits(maxBits)
        val minUnsigned = Float.fromBits(minBits)

        val max = if (xs[31].min() == 1) -minUnsigned else maxUnsigned
        val min = if (xs[31].max() == 1) -maxUnsigned else minUnsigned

        // Use Float.compare to get exact -0.0f and 0.0f comparison
        val c1 = min.compareTo(p.max().toFloat())
        val c2 = max.compareTo(p.min().toFloat())
        return if (sat) c1 <= 0 && c2 >= 0
        else c1 > 0 || c2 < 0
    }

    override fun toString(): String {
        return id() + " (" + xs.joinToString() + ", " + p + " )"
    }
}
