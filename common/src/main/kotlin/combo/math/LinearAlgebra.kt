@file:JvmName("LinearAlgebra")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.*

fun DoubleArray.toVector() = Vector(this)
fun Array<DoubleArray>.toMatrix() = Matrix(this)

operator fun Double.plus(v: Vector) = v + this
operator fun Double.plus(A: Matrix) = A + this
operator fun Double.times(v: Vector) = v * this
operator fun Double.times(A: Matrix) = A * this
operator fun Double.minus(v: Vector) = v.map { -it } + this
operator fun Double.minus(A: Matrix) = A.map { -it } + this
operator fun Double.div(v: Vector) = v.map { 1 / it } * this
operator fun Double.div(A: Matrix) = A.map { 1 / it } * this

class Vector(val array: DoubleArray) {

    constructor(n: Int) : this(DoubleArray(n))

    val size get() = array.size

    val indices get() = array.indices

    fun copy(): Vector = Vector(array.copyOf())

    operator fun set(i: Int, d: Double) {
        array[i] = d
    }

    operator fun get(i: Int) = array[i]

    inline fun map(transform: (Double) -> Double) = copy().apply {
        for (i in array.indices)
            this[i] = transform(array[i])
    }

    inline fun mapIndexed(transform: (index: Int, Double) -> Double) = copy().apply {
        for (i in array.indices)
            this[i] = transform(i, array[i])
    }

    operator fun unaryMinus() = map { d -> -d }
    operator fun plus(v: Vector) = mapIndexed { i, d -> d + v[i] }
    operator fun plus(s: Double) = map { d -> d + s }
    operator fun minus(v: Vector) = mapIndexed { i, d -> d - v[i] }
    operator fun minus(s: Double) = map { d -> d - s }
    operator fun times(v: Vector) = mapIndexed { i, d -> d * v[i] }
    operator fun times(s: Double) = map { d -> d * s }
    operator fun div(v: Vector) = mapIndexed { i, d -> d / v[i] }
    operator fun div(s: Double) = map { d -> d / s }

    /**
     * @return Row vector v*A
     */
    operator fun times(A: Matrix) = Vector(A.size.second).apply {
        for (i in 0 until size)
            for (j in 0 until A.size.rows)
                this[j] += A[i, j] * this@Vector[i]
    }

    infix fun outer(v: Vector) = Matrix(size).apply {
        for (i in 0 until this.size.rows)
            for (j in 0 until this.size.cols)
                this[i, j] = this@Vector[i] * v[j]
    }

    fun sum() = array.reduce { sum, d -> d + sum }

    infix fun dot(v: Vector) = dot(v.array)
    //infix fun dot(v: DoubleArray) = array.foldIndexed(0.0) { i, dot, d -> dot + d * v[i] }
    infix fun dot(v: DoubleArray) = array.foldIndexed(0.0) { i, dot, d -> dot + d * v[i] }

    override fun equals(other: Any?) = if (other is Vector) other.array.contentEquals(array) else false
    override fun hashCode() = array.contentHashCode()
    override fun toString() = "[" + array.joinToString(",") + "]"

    fun toRoundedArray(eps: Double = 1.0 / Int.MAX_VALUE * size): DoubleArray {
        val arr = DoubleArray(size)
        val sorted = array.sortedArray().toVector()
        val max = max(abs(sorted[size - 1]), abs(sorted[0]))
        if (max < eps) return arr
        val normalized = sorted / max
        var minDelta: Double = normalized.array.map { abs(it) }.min()!!
        for (i in 1 until size)
            minDelta = min(minDelta, normalized[i] - normalized[i - 1])
        minDelta = max(eps, minDelta)
        val scale = 1 / minDelta
        for (i in 0 until size)
            arr[i] = round(this[i] * scale)
        return arr

    }

    fun toIntArray(): IntArray {
        val ints = IntArray(size)
        for ((i, d) in toRoundedArray().withIndex())
            ints[i] = d.toInt()
        return ints
    }
}

typealias MatrixSize = Pair<Int, Int>

val MatrixSize.rows get() = component1()
val MatrixSize.cols get() = component2()

class Matrix(val matrix: Array<DoubleArray>) {

    constructor(n: Int, m: Int = n) : this(Array(n) { DoubleArray(m) })

    fun copy() = Matrix(Array(size.rows) { matrix[it].copyOf() })

    val size get() = Pair(matrix.size, matrix[0].size)
    val T get() = copy().apply { transposed() }

    operator fun set(i: Int, j: Int, s: Double) {
        matrix[i][j] = s
    }

    operator fun get(i: Int, j: Int) = matrix[i][j]
    operator fun get(i: Int) = matrix[i]

    fun col(j: Int) = DoubleArray(size.rows).also {
        for (i in 0 until size.rows)
            it[i] = this[i][j]
    }

    inline fun map(transform: (Double) -> Double) = copy().apply {
        for (i in 0 until size.rows)
            for (j in 0 until size.cols)
                this[i, j] = transform(matrix[i][j])
    }

    inline fun mapIndexed(transform: (i: Int, j: Int, Double) -> Double) = copy().apply {
        for (i in 0 until size.rows)
            for (j in 0 until size.cols)
                this[i, j] = transform(i, j, matrix[i][j])
    }

    operator fun plus(s: Double) = map { d -> d + s }
    operator fun minus(s: Double) = map { d -> d - s }
    operator fun times(s: Double) = map { d -> d * s }
    operator fun div(s: Double) = map { d -> d / s }
    operator fun plus(A: Matrix) = mapIndexed { i, j, d -> d + A[i, j] }
    operator fun minus(A: Matrix) = mapIndexed { i, j, d -> d - A[i, j] }
    operator fun times(A: Matrix) = mapIndexed { i, j, d -> d * A[i, j] }
    operator fun div(A: Matrix) = mapIndexed { i, j, d -> d / A[i, j] }

    /**
     * @return Column vector A*v
     */
    operator fun times(v: Vector): Vector {
        val copy = Vector(size.rows)
        for (i in 0 until size.rows) {
            for (j in 0 until size.cols) {
                copy[i] += matrix[i][j] * v[j]
            }
        }
        return copy
    }

    /**
     * must be square matrix
     */
    fun transposed() {
        require(size.rows == size.cols)
        for (i in 0 until size.rows - 1)
            for (j in (i + 1) until size.rows) {
                val tmp = matrix[i][j]
                matrix[i][j] = matrix[j][i]
                matrix[j][i] = tmp
            }
    }

    override fun equals(other: Any?) = if (other is Matrix) other.matrix.contentDeepEquals(matrix) else false

    override fun hashCode() = matrix.contentDeepHashCode()
    override fun toString(): String {
        val b = StringBuilder("[")
        for (a in matrix)
            b.append("[").append(a.joinToString(",")).append("]")
        return b.append("]").toString()
    }
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
    val t = this * v
    return t / sqrt(1.0 + (t dot v))
}

fun Matrix.cholDowndate(v: Vector) {
    val L = this
    for (i in 0 until size.rows) {
        val r = sqrt(L[i, i] * L[i, i] - v[i] * v[i])
        val c = r / L[i][i];
        val s = v[i] / L[i][i];
        L[i][i] = r;
        for (j in i + 1 until size.rows)
            L[i, j] = (L[i, j] - s * v[j]) / c
        for (j in i + 1 until size.rows)
            v[j] = c * v[j] - s * L[i][j]
    }
}
