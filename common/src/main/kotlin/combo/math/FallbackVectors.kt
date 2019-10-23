package combo.math

import kotlin.math.sqrt

class FallbackVector(val array: FloatArray) : Vector {

    constructor(size: Int) : this(FloatArray(size))

    override val size: Int get() = array.size
    override val sparse: Boolean get() = false

    override fun get(i: Int) = array[i]
    override fun set(i: Int, x: Float) {
        array[i] = x
    }

    override infix fun dot(v: VectorView) = array.foldIndexed(0f) { i, dot, d -> dot + d * v[i] }
    override fun norm2() = sqrt(sumBy { it * it })
    override fun sum() = array.sum()

    override fun toFloatArray() = array.copyOf()
    override fun copy() = FallbackVector(array.copyOf())
    override fun vectorCopy() = copy()
    override fun asVector() = this
}

class FallbackMatrix(val array: Array<FloatArray>) : Matrix {

    constructor(size: Int) : this(Array(size) { FloatArray(size) })
    constructor(rows: Int, cols: Int) : this(Array(rows) { FloatArray(cols) })

    override val rows: Int get() = array.size
    override val cols: Int get() = if (array.isEmpty()) 0 else array[0].size

    override operator fun get(i: Int, j: Int) = array[i][j]
    override fun get(row: Int): VectorView = FallbackVector(array[row])
    override operator fun set(i: Int, j: Int, x: Float) {
        array[i][j] = x
    }

    override fun set(row: Int, values: VectorView) {
        array[row] = values.toFloatArray()
    }

    override operator fun times(v: VectorView) =
            FallbackVector(FloatArray(rows) {
                v dot this@FallbackMatrix[it]
            })

    override fun transpose() {
        for (i in 0 until rows - 1)
            for (j in (i + 1) until rows) {
                val tmp = this[i, j]
                this[i, j] = this[j, i]
                this[j, i] = tmp
            }
    }

    override fun toArray() = Array(rows) { array[it].copyOf() }
    override fun copy() = FallbackMatrix(toArray())
}

object FallbackVectorFactory : VectorFactory {
    override fun zeroMatrix(rows: Int, columns: Int) = FallbackMatrix(rows, columns)
    override fun zeroVector(size: Int) = FallbackVector(size)
    override fun matrix(values: Array<FloatArray>) = FallbackMatrix(values)
    override fun vector(values: FloatArray) = FallbackVector(values)
}
