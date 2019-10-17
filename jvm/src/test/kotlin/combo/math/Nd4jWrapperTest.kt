package combo.math

class Nd4jMatrixTest : MatrixTest() {
    override fun vector(vararg xs: Float) = Nd4jVectorFactory.vector(xs)
    override fun matrix(vararg xs: FloatArray) = Nd4jVectorFactory.matrix(xs.toList().toTypedArray())
}

class Nd4jVectorTest : VectorTest() {
    override fun vector(vararg xs: Float) = Nd4jVectorFactory.vector(xs)
}

