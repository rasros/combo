package combo.math

actual var vectors: VectorFactory = run {
    try {
        Class.forName("org.nd4j.linalg.factory.Nd4j", false, null)
        Nd4jVectorFactory
    } catch (e: Exception) {
        FallbackVectorFactory
    }
}
