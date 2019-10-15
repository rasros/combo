package combo.bandit.glm

import combo.bandit.BanditData
import combo.math.Matrix
import combo.math.Vector
import combo.math.cholesky

sealed class LinearData(val weights: Vector, val bias: Float, val biasPrecision: Float) : BanditData {
    override fun migrate(from: IntArray, to: IntArray): BanditData {
        TODO("not implemented")
    }
}

class FullCovarianceData(weights: Vector, val covariance: Matrix, val cholesky: Matrix, bias: Float, biasPrecision: Float)
    : LinearData(weights, bias, biasPrecision) {
    constructor(weights: Vector, covariance: Matrix, bias: Float, biasPrecision: Float) : this(
            weights, covariance, covariance.cholesky(), bias, biasPrecision)
}

class DiagonalCovarianceData(weights: Vector, val precision: Vector, bias: Float, biasPrecision: Float)
    : LinearData(weights, bias, biasPrecision)
