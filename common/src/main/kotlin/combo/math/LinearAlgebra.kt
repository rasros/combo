@file:JvmName("LinearAlgebra")

package combo.math

import combo.util.mapArray
import combo.util.transformArray
import combo.util.transformArrayIndexed
import kotlin.jvm.JvmName
import kotlin.math.*

typealias Vector = DoubleArray
typealias Matrix = Array<DoubleArray>

fun matrix(size: Int) = Array(size) { DoubleArray(size) }

val Matrix.rows: Int
    get() = this.size
val Matrix.cols: Int
    get() = this[0].size

operator fun Matrix.get(i: Int, j: Int) = this[i][j]
operator fun Matrix.set(i: Int, j: Int, s: Double) {
    this[i][j] = s
}

/**
 * must be square matrix
 */
fun Matrix.transpose() {
    require(rows == cols)
    for (i in 0 until rows - 1)
        for (j in (i + 1) until rows) {
            val tmp = this[i][j]
            this[i][j] = this[j][i]
            this[j][i] = tmp
        }
}

val Matrix.T: Matrix
    get() = Array(size) { this[it].copyOf() }.also { transpose() }

operator fun Double.plus(v: Vector) = v + this
operator fun Double.plus(A: Matrix) = A + this
operator fun Double.times(v: Vector) = v * this
operator fun Double.times(A: Matrix) = A * this
operator fun Double.minus(v: Vector) = v.mapArray { this - it }
operator fun Double.minus(A: Matrix): Matrix = A.mapMatrix { this - it }
operator fun Double.div(v: Vector) = v.mapArray { this / it }
operator fun Double.div(A: Matrix) = A.mapMatrix { this / it }

operator fun Vector.unaryMinus() = mapArray { d -> -d }
operator fun Vector.plus(s: Double) = mapArray { d -> d + s }
fun Vector.add(s: Double) = transformArray { d -> d + s }
operator fun Vector.minus(s: Double) = mapArray { d -> d - s }
fun Vector.sub(s: Double) = transformArray { d -> d - s }
operator fun Vector.times(s: Double) = mapArray { d -> d * s }
fun Vector.multiply(s: Double) = transformArray { d -> d * s }
operator fun Vector.div(s: Double) = mapArray { d -> d / s }
fun Vector.divide(s: Double) = transformArray { d -> d / s }

infix fun Vector.dot(v: Vector) = foldIndexed(0.0) { i, dot, d -> dot + d * v[i] }
infix fun Vector.outer(v: Vector) = Array(size) { DoubleArray(size) }.also {
    for (i in 0 until size)
        for (j in 0 until size)
            it[i, j] = this[i] * v[j]
}

private inline fun Matrix.mapMatrix(transform: (Double) -> Double) = Array(size) { i ->
    DoubleArray(size) { j -> transform(this[i, j]) }
}

private inline fun Matrix.mapMatrixIndexed(transform: (i: Int, j: Int, Double) -> Double) = Array(size) { i ->
    DoubleArray(size) { j -> transform(i, j, this[i, j]) }
}

private inline fun Matrix.transformMatrix(transform: (Double) -> Double) = forEach { it.transformArray(transform) }
private inline fun Matrix.transformMatrixIndexed(transform: (i: Int, j: Int, Double) -> Double) =
        forEachIndexed { i, v -> v.transformArrayIndexed { j, d -> transform(i, j, d) } }

operator fun Matrix.unaryMinus() = mapMatrix { d -> -d }
operator fun Matrix.plus(s: Double) = mapMatrix { it + s }
fun Matrix.add(s: Double) = transformMatrix { it + s }
fun Matrix.add(a: Matrix) = transformMatrixIndexed { i, j, d -> d + a[i, j] }
operator fun Matrix.minus(s: Double) = mapMatrix { d -> d - s }
fun Matrix.sub(s: Double) = transformMatrix { d -> d - s }
fun Matrix.sub(a: Matrix) = transformMatrixIndexed { i, j, d -> d - a[i, j] }
operator fun Matrix.times(s: Double) = mapMatrix { d -> d * s }
fun Matrix.multiply(s: Double) = transformMatrix { d -> d * s }
fun Matrix.multiply(a: Matrix) = transformMatrixIndexed { i, j, d -> d * a[i, j] }
operator fun Matrix.div(s: Double) = mapMatrix { d -> d / s }
fun Matrix.divide(s: Double) = transformMatrix { d -> d / s }
fun Matrix.divide(a: Matrix) = transformMatrixIndexed { i, j, d -> d / a[i, j] }

/**
 * @return Column vector A*v
 */
operator fun Matrix.times(v: Vector) = Vector(rows).also {
    for (i in 0 until rows)
        for (j in 0 until cols)
            it[i] += this[i][j] * v[j]
}

/**
 * @return Row vector v*A
 */
operator fun Vector.times(A: Matrix) = Vector(A.cols).also {
    for (i in 0 until size)
        for (j in 0 until A.rows)
            it[j] += A[i, j] * this[i]
}

/**
 * Perform a rank 1 update of symmetric matrix inverse.
 * This uses the Sherman-Morrison formula:
 * inv(A + v*v') = inv(A) - inv(A)*v*v'*inv(A) / (1+v'*inv(A)*v)
 * <p>
 *
 * @return vector whose outer product should be added to inverse
 */
fun Matrix.shermanUpdater(v: Vector): Vector {
    val t: Vector = this * v
    return t.apply { divide(sqrt(1.0 + (t dot v))) }
}

fun Matrix.cholDowndate(v: Vector) {
    val L = this
    for (i in 0 until rows) {
        val r: Double = sqrt(L[i, i] * L[i, i] - v[i] * v[i])
        val c: Double = r / L[i][i]
        val s: Double = v[i] / L[i][i]
        L[i][i] = r
        for (j in i + 1 until rows)
            L[i, j] = (L[i, j] - s * v[j]) / c
        for (j in i + 1 until rows)
            v[j] = c * v[j] - s * L[i][j]
    }
}

fun Vector.toRoundedArray(eps: Double = 1.0 / Int.MAX_VALUE * size): DoubleArray {
    val arr = DoubleArray(size)
    val sorted = sortedArray()
    val max = max(abs(sorted[size - 1]), abs(sorted[0]))
    if (max < eps) return arr
    val normalized = sorted / max
    val minDelta: Double = normalized.minBy { abs(it) }!!.let {
        var tmp = it
        for (i in 1 until size)
            tmp = min(tmp, normalized[i] - normalized[i - 1])
        max(eps, tmp)
    }
    val scale = 1 / minDelta
    for (i in 0 until size)
        arr[i] = round(this[i] * scale)
    return arr
}

fun Vector.toIntArray(): IntArray {
    val ints = IntArray(size)
    for ((i, d) in toRoundedArray().withIndex())
        ints[i] = d.toInt()
    return ints
}
