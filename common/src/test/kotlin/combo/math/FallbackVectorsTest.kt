package combo.math

class FallbackVectorTest : VectorTest() {
    override fun vector(vararg xs: Float) = FallbackVector(xs)
}

class FallbackMatrixTest : MatrixTest() {
    override fun vector(vararg xs: Float) = FallbackVector(xs)
    override fun matrix(vararg xs: FloatArray) = FallbackMatrix(xs.toList().toTypedArray())
}