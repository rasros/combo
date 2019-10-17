package combo.bandit.glm

import combo.bandit.BanditData
import combo.math.cholesky
import combo.math.vectors

sealed class LinearData(val weights: FloatArray, val bias: Float, val biasPrecision: Float) : BanditData {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}

class FullCovarianceData(weights: FloatArray, val covariance: Array<FloatArray>, val covarianceL: Array<FloatArray>, bias: Float, biasPrecision: Float)
    : LinearData(weights, bias, biasPrecision) {
    constructor(weights: FloatArray, covariance: Array<FloatArray>, bias: Float, biasPrecision: Float) : this(
            weights, covariance, vectors.matrix(covariance).cholesky().toArray(), bias, biasPrecision)
}

class DiagonalCovarianceData(weights: FloatArray, val precision: FloatArray, bias: Float, biasPrecision: Float)
    : LinearData(weights, bias, biasPrecision)
