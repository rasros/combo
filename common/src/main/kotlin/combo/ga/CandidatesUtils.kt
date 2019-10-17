@file:JvmName("CandidatesUtils")

package combo.ga

import combo.math.RunningVariance
import combo.sat.Instance
import kotlin.jvm.JvmName

fun Array<out Instance>.diversity(): Float {
    var sum = 0.0f
    for (i in 0 until get(0).size) {
        val v = RunningVariance()
        for (j in 0 until size)
            v.accept(if (this[j].isSet(i)) 1.0f else 0.0f)
        sum += v.squaredDeviations
    }
    return sum
}

fun Array<out Instance>.diversity2(): Float {
    val n = size
    val m = get(0).size
    var sum = 0.0f
    for (i in 0 until n) {
        val i1 = get(i)
        for (j in 0 until n) {
            if (i == j) continue
            val i2 = get(j)
            var overlap = 0
            for (k in 0 until m) if (i1.isSet(k) == i2.isSet(k)) overlap++
            sum += overlap / m.toFloat()
        }
    }
    return sum
}

fun Array<out Instance>.diversity3(): Float {
    val total = RunningVariance()
    for (i in 0 until get(0).size) {
        val v = RunningVariance()
        for (j in 0 until size)
            v.accept(if (this[j].isSet(i)) 1.0f else 0.0f)
        total.accept(4 * v.mean * (1 - v.mean))
    }
    return total.sum
}

fun Array<out Instance>.singularColumns(): Int {
    var singular = 0
    for (i in 0 until get(0).size) {
        var hasZero = false
        var hasOne = false
        for (j in 0 until size) {
            if (!this[j].isSet(i)) hasZero = true
            else hasOne = true
        }
        if (!hasOne && !hasZero) singular++
    }
    return singular
}
