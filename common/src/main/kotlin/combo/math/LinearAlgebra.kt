@file:JvmName("LinearAlgebra")

package combo.math

import combo.util.*
import kotlin.jvm.JvmName
import kotlin.math.absoluteValue
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

/**
 * Perform cholesky decomposition downdate with vector x, such that A = L'*L - x*x'
 * The matrix is modified inline.
 * Ported from fortran dchdd.
 */
fun Matrix.choleskyDowndate(x: Vector): Float {
    val L = this
    val p = x.size

    val s = Vector(p)
    val c = Vector(p)

    // Solve the system L.T*s = x
    s[0] = x[0] / L[0, 0]
    if (p > 1) {
        for (j in 1 until p) {
            var sum = 0f
            for (i in 0 until j)
                sum += L[i, j] * s[i]
            s[j] = x[j] - sum
            s[j] = s[j] / L[j, j]
        }
    }

    var norm = sqrt(s.sumByFloat { it * it })
    return if (norm > 0f && norm < 1f) {
        var alpha = sqrt(1 - norm * norm)
        for (ii in 0 until p) {
            val i = p - ii - 1
            val scale = alpha + s[i].absoluteValue
            val a = alpha / scale
            val b = s[i] / scale
            norm = sqrt(a * a + b * b)
            c[i] = a / norm
            s[i] = b / norm
            alpha = scale * norm
        }
        for (j in 0 until p) {
            var xx = 0f
            for (ii in 0..j) {
                val i = j - ii
                val t = c[i] * xx + s[i] * L[i, j]
                L[i, j] = c[i] * L[i, j] - s[i] * xx
                xx = t
            }
        }
        0f
    } else norm
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
