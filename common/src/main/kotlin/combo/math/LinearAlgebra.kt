@file:JvmName("LinearAlgebra")

package combo.math

import combo.util.assert
import combo.util.mapArray
import combo.util.transformArray
import combo.util.transformArrayIndexed
import kotlin.jvm.JvmName
import kotlin.math.roundToInt
import kotlin.math.sqrt

typealias Vector = FloatArray
typealias Matrix = Array<FloatArray>

fun matrix(size: Int) = Array(size) { FloatArray(size) }

val Matrix.rows: Int
    get() = this.size
val Matrix.cols: Int
    get() = if (size == 0) 0 else this[0].size

operator fun Matrix.get(i: Int, j: Int) = this[i][j]
operator fun Matrix.set(i: Int, j: Int, s: Float) {
    this[i][j] = s
}

/**
 * Inline transpose. Must be square matrix.
 */
fun Matrix.transpose() {
    assert(rows == cols)
    for (i in 0 until rows - 1)
        for (j in (i + 1) until rows) {
            val tmp = this[i][j]
            this[i][j] = this[j][i]
            this[j][i] = tmp
        }
}

val Matrix.T: Matrix
    get() = Array(size) { this@T[it].copyOf() }.apply { transpose() }

operator fun Float.plus(v: Vector) = v + this
operator fun Float.plus(A: Matrix) = A + this
operator fun Float.times(v: Vector) = v * this
operator fun Float.times(A: Matrix) = A * this
operator fun Float.minus(v: Vector) = v.mapArray { this - it }
operator fun Float.minus(A: Matrix): Matrix = A.mapMatrix { this - it }
operator fun Float.div(v: Vector) = v.mapArray { this / it }
operator fun Float.div(A: Matrix) = A.mapMatrix { this / it }

operator fun Vector.unaryMinus() = mapArray { d -> -d }
operator fun Vector.plus(s: Float) = mapArray { d -> d + s }
fun Vector.add(s: Float) = transformArray { d -> d + s }
operator fun Vector.minus(s: Float) = mapArray { d -> d - s }
fun Vector.sub(s: Float) = transformArray { d -> d - s }
operator fun Vector.times(s: Float) = mapArray { d -> d * s }
fun Vector.multiply(s: Float) = transformArray { d -> d * s }
operator fun Vector.div(s: Float) = mapArray { d -> d / s }
fun Vector.divide(s: Float) = transformArray { d -> d / s }

fun Vector.add(v: Vector) = transformArrayIndexed { i, d -> d + v[i] }
fun Vector.sub(v: Vector) = transformArrayIndexed { i, d -> d - v[i] }

infix fun Vector.dot(v: Vector) = foldIndexed(0.0f) { i, dot, d -> dot + d * v[i] }
infix fun Vector.outer(v: Vector) = Array(size) { FloatArray(size) }.also {
    for (i in 0 until size)
        for (j in 0 until size)
            it[i, j] = this[i] * v[j]
}

private inline fun Matrix.mapMatrix(transform: (Float) -> Float) = Array(size) { i ->
    FloatArray(size) { j -> transform(this[i, j]) }
}

private inline fun Matrix.mapMatrixIndexed(transform: (i: Int, j: Int, Float) -> Float) = Array(size) { i ->
    FloatArray(size) { j -> transform(i, j, this[i, j]) }
}

private inline fun Matrix.transformMatrix(transform: (Float) -> Float) = forEach { it.transformArray(transform) }
private inline fun Matrix.transformMatrixIndexed(transform: (i: Int, j: Int, Float) -> Float) =
        forEachIndexed { i, v -> v.transformArrayIndexed { j, d -> transform(i, j, d) } }

operator fun Matrix.unaryMinus() = mapMatrix { d -> -d }
operator fun Matrix.plus(s: Float) = mapMatrix { it + s }
fun Matrix.add(s: Float) = transformMatrix { it + s }
fun Matrix.add(a: Matrix) = transformMatrixIndexed { i, j, d -> d + a[i, j] }
operator fun Matrix.minus(s: Float) = mapMatrix { d -> d - s }
fun Matrix.sub(s: Float) = transformMatrix { d -> d - s }
fun Matrix.sub(a: Matrix) = transformMatrixIndexed { i, j, d -> d - a[i, j] }
operator fun Matrix.times(s: Float) = mapMatrix { d -> d * s }
fun Matrix.multiply(s: Float) = transformMatrix { d -> d * s }
fun Matrix.elementMultiply(a: Matrix) = transformMatrixIndexed { i, j, d -> d * a[i, j] }
operator fun Matrix.div(s: Float) = mapMatrix { d -> d / s }
fun Matrix.divide(s: Float) = transformMatrix { d -> d / s }
fun Matrix.elementDivide(a: Matrix) = transformMatrixIndexed { i, j, d -> d / a[i, j] }

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

fun Vector.toIntArray(delta: Float, gcd: Boolean): IntArray {
    val array = IntArray(size) {
        (this[it] / delta).roundToInt()
    }
    if (gcd) {
        val g = gcdAll(*array)
        if (g > 1) array.transformArray { it / g }
    }
    return array
}

fun Matrix.choleskyDowndate(v: Vector) {
    val L = this
    for (i in 0 until rows) {
        val r: Float = sqrt(L[i, i] * L[i, i] - v[i] * v[i])
        val c: Float = r / L[i][i]
        val s: Float = v[i] / L[i][i]
        L[i][i] = r
        for (j in i + 1 until rows)
            L[i, j] = (L[i, j] - s * v[j]) / c
        for (j in i + 1 until rows)
            v[j] = c * v[j] - s * L[i][j]
    }
}

fun Matrix.cholesky(): Matrix {
    val N = size
    val L = Matrix(N) { Vector(N) }
    for (i in 0 until N) {
        for (j in 0..i) {
            var sum = 0.0f
            for (k in 0 until j)
                sum += L[i][k] * L[j][k]

            if (i == j) L[i][i] = sqrt(this[i][i] - sum)
            else L[i][j] = 1.0f / L[j][j] * (this[i][j] - sum)
        }
        if (L[i][i] <= 0) error("Matrix not positive definite");
    }
    return L
}
