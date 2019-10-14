package combo.bandit.glm

import combo.bandit.BanditData
import combo.math.Matrix
import combo.math.Vector
import combo.math.cholesky

sealed class LinearData(val weights: Vector, val bias: Float, val biasVariance: Float) : BanditData {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}

class FullCovarianceData(weights: Vector, val covariance: Matrix, val cholesky: Matrix, bias: Float, biasVariance: Float)
    : LinearData(weights, bias, biasVariance) {
    constructor(weights: Vector, covariance: Matrix, bias: Float, biasVariance: Float) : this(
            weights, covariance, covariance.cholesky(), bias, biasVariance)
}

class DiagonalCovarianceData(weights: Vector, val variance: Vector, bias: Float, biasVariance: Float)
    : LinearData(weights, bias, biasVariance)
