package combo.bandit.glm

import combo.math.*
import kotlin.math.sqrt
import kotlin.random.Random

abstract class LinearModel(val link: Transform,
                           val loss: Transform,
                           val regularization: Transform,
                           val regularizationFactor: Float,
                           val exploration: Float,
                           var step: Long,
                           val weights: Vector,
                           var bias: Float) {

    fun predict(input: VectorView) = link.apply(bias + (input dot weights))
    abstract fun sample(rng: Random): VectorView

    abstract fun train(input: VectorView, result: Float, weight: Float)
    open fun trainAll(inputs: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {
        for (i in inputs.indices)
            train(inputs[i], results[i], weights?.get(i) ?: 1.0f)
    }

    /** Create a reset copy of the model with the given variance parameter and cleared weights. */
    abstract fun blank(variance: Float): LinearModel

    /**
     * @param varianceMixin ratio between 0-1 of how much data should change variance estimate
     * @param weightMixin ratio between 0-1 of how much data should change weights
     */
    abstract fun importData(data: LinearData, varianceMixin: Float = 1f, weightMixin: Float = 1f)

    abstract fun exportData(): LinearData
}

class GreedyLinearModel(link: Transform,
                        loss: Transform,
                        regularization: Transform,
                        regularizationFactor: Float,
                        val updater: SGDAlgorithm,
                        exploration: Float,
                        step: Long,
                        weights: Vector,
                        val biasRate: LearningRateSchedule = ExponentialDecay(),
                        bias: Float)
    : LinearModel(link, loss, regularization, regularizationFactor, exploration, step, weights, bias) {

    override fun sample(rng: Random): VectorView {
        return if (exploration > 0f)
            vectors.zeroVector(weights.size).apply {
                transformIndexed { i, _ ->
                    rng.nextNormal(weights[i], sqrt(exploration))
                }
            }
        else weights
    }

    override fun train(input: VectorView, result: Float, weight: Float) {
        step++
        val yhat = predict(input)
        val diff = yhat - result
        val loss = loss.apply(diff)
        for (i in input) {
            val reg = regularizationFactor * regularization.apply(weights[i])
            val grad = input[i] * (loss + reg) * weight
            weights[i] = updater.step(weights[i], i, grad, step)
        }
        bias -= diff * biasRate.rate(step)
    }

    override fun importData(data: LinearData, varianceMixin: Float, weightMixin: Float) {
        step += data.step
        bias = combineMean(bias, data.bias, 1 - weightMixin, weightMixin)
        for (i in weights.indices)
            weights[i] = combineMean(weights[i], data.weights[i], 1 - weightMixin, weightMixin)
        updater.importData(vectors.matrix(data.updaterData), varianceMixin, weightMixin)
    }

    override fun exportData() = LinearData(weights.toFloatArray(), bias, 0f, step, updater.exportData().toArray())
    override fun blank(variance: Float) = GreedyLinearModel(
            link, loss, regularization, regularizationFactor, updater.copyReset(), exploration, 0L, vectors.zeroVector(weights.size), biasRate, bias)
}

class PrecisionLinearModel(val family: VarianceFunction,
                           link: Transform,
                           loss: Transform,
                           regularization: Transform,
                           regularizationFactor: Float,
                           val learningRate: LearningRateSchedule,
                           exploration: Float,
                           step: Long,
                           weights: Vector,
                           val precision: Vector,
                           bias: Float,
                           var biasPrecision: Float)
    : LinearModel(link, loss, regularization, regularizationFactor, exploration, step, weights, bias) {

    override fun sample(rng: Random) =
            vectors.zeroVector(weights.size).apply {
                transformIndexed { i, _ ->
                    rng.nextNormal(weights[i], sqrt(exploration / precision[i]))
                }
            }

    override fun train(input: VectorView, result: Float, weight: Float) {
        step++
        val yhat = predict(input)
        val diff = yhat - result
        val loss = loss.apply(diff)
        val varF = family.variance(yhat)
        val lr = learningRate.rate(step)
        for (i in input) {
            val reg = regularizationFactor * regularization.apply(weights[i])
            precision[i] += varF * input[i]
            val grad = input[i] * (loss + reg) * weight
            weights[i] -= lr * grad / precision[i]
        }
        biasPrecision += varF
        bias -= lr * weight * loss / biasPrecision
    }

    override fun importData(data: LinearData, varianceMixin: Float, weightMixin: Float) {
        require(data.updaterData.size == 1) { "Expected updaterData to have one row." }
        step += data.step
        bias = combineMean(bias, data.bias, 1 - weightMixin, weightMixin)
        biasPrecision = combinePrecision(biasPrecision, data.biasPrecision, bias, data.bias, 1 - varianceMixin, varianceMixin)
        for (i in weights.indices) {
            weights[i] = combineMean(weights[i], data.weights[i], 1 - weightMixin, weightMixin)
            precision[i] = combinePrecision(precision[i], data.updaterData[0][i], weights[i], data.weights[i], 1 - varianceMixin, varianceMixin)
        }
    }

    override fun exportData() = LinearData(weights.toFloatArray(), bias, biasPrecision, step, arrayOf(precision.toFloatArray()))
    override fun blank(variance: Float) = PrecisionLinearModel(
            family, link, loss, regularization, regularizationFactor, learningRate, exploration, 0L, vectors.zeroVector(weights.size),
            vectors.zeroVector(precision.size).apply { add(1f / variance) }, bias, 1f / variance)
}

/**
 * Laplace approximation to the full bayesian linear regression.
 */
class CovarianceLinearModel(val family: VarianceFunction,
                            link: Transform,
                            loss: Transform,
                            regularization: Transform,
                            regularizationFactor: Float,
                            val learningRate: LearningRateSchedule,
                            exploration: Float,
                            step: Long,
                            weights: Vector,
                            val covariance: Matrix,
                            val covarianceL: Matrix,
                            bias: Float,
                            var biasPrecision: Float)
    : LinearModel(link, loss, regularization, regularizationFactor, exploration, step, weights, bias) {

    override fun sample(rng: Random): Vector {
        val u = vectors.vector(FloatArray(weights.size) { rng.nextNormal(0f, sqrt(exploration)) })
        val wHat = covarianceL * u
        wHat.add(weights)
        return wHat
    }

    override fun train(input: VectorView, result: Float, weight: Float) {
        step++
        val yhat = predict(input)
        val diff = yhat - result
        val loss = loss.apply(diff)
        val varF = family.variance(yhat)
        val lr = learningRate.rate(step)

        // H is a matrix of precision
        // x is the input
        // H^-1 is the covariance matrix
        // g is the gradient wrt to the weights, g2 is the 2nd order gradient
        // Note that g2 = x'*x/var
        // The update rule is:
        // H_t = H_t-1 + g2_t-1
        // w_t = w_t-1 - H^-1_t * g_t-1
        //
        // The matrix H is not stored explicitly, but its inverse can be updated incrementally with the
        // Sherman-Woodbury-Morrison formula, like so:
        // (A + uv^T)^-1 = A^-1 - (A^-1*uv^T*A^-1) / (1 + v^T*A^-1*u)
        // Here, uv^T=g2=x'*x/var <=> u=v=x/sqrt(var), so it simplifies to:
        // (H + g2*g2^T)^-1 = H^-1 - (H^-1*g2*g2^T*H^-1) / (1 + g2^T*H^-1*g2) =
        // (H + g2*g2^T)^-1 = H^-1 - z*z^T, where z = H^-1*x / sqrt(var + x^T*H^-1*x)
        // Also, L, the cholesky decomposition of the covariance matrix is needed to generate sample from the model. It
        // is also updated incrementally.

        // Compute z as above
        val z = let {
            val vec = covariance * input
            val denom = sqrt(varF + (input dot vec))
            if (denom == 0f) return
            vec.divide(denom)
            vec
        }

        // Downdate L, L = chol(H^-1)
        var norm = covarianceL.choleskyDowndate(z)
        if (norm > 1f) {
            // Numerical errors have been accrued, to fix we recalculate cholesky decomposition
            val L = covariance.cholesky()
            for (i in 0 until L.rows)
                for (j in 0 until L.rows)
                    covarianceL[j, i] = L[i, j] // L is lower triangulate, cholesky is upper

            norm = covarianceL.choleskyDowndate(z)
            while (norm > 1f) {
                // Still failed cholesky downdate due to numerical instability
                // Take smaller step instead
                z.divide(norm + 1e-4f)
                norm = covarianceL.choleskyDowndate(z)
            }
        }

        // Downdate covariance matrix, H^-1 = H^-1 - z*z' (outer product)
        for (i in 0 until z.size)
            for (j in 0 until z.size)
                covariance[i, j] -= z[i] * z[j]

        // w_t = w_t-1 - H^-1_t * g_t-1
        val reg = weights.copy().apply { transform { regularization.apply(it) * regularizationFactor } }
        val grad = (input + reg) * (loss * lr)
        val step = covariance * grad
        weights.subtract(step)

        biasPrecision += weight * varF
        bias -= lr * weight * loss / biasPrecision
    }

    override fun importData(data: LinearData, varianceMixin: Float, weightMixin: Float) {
        require(data.updaterData.size == weights.size * 2) { "Expected updaterData to contain covariance and covarianceL." }
        step += data.step
        bias = combineMean(bias, data.bias, 1 - weightMixin, weightMixin)
        biasPrecision = combinePrecision(biasPrecision, data.biasPrecision, bias, data.bias, 1 - varianceMixin, varianceMixin)
        for (i in weights.indices) {
            weights[i] = combineMean(weights[i], data.weights[i], 1 - weightMixin, weightMixin)
            for (j in weights.indices) {
                covariance[i, j] = combineVariance(covariance[i, j], data.updaterData[i][j], weights[i], data.weights[j], 1 - varianceMixin, varianceMixin)
            }
        }
        val L = covariance.cholesky()
        for (i in 0 until L.rows)
            for (j in 0 until L.rows)
                covarianceL[j, i] = L[i, j] // L is lower triangulate, cholesky is upper
    }

    override fun exportData() = LinearData(weights.toFloatArray(), bias, biasPrecision, step, covariance.toArray() + covarianceL.toArray())

    override fun blank(variance: Float): CovarianceLinearModel {
        val covariance = vectors.zeroMatrix(covariance.rows)
        val covarianceL = vectors.zeroMatrix(covarianceL.rows)
        for (i in 0 until covariance.rows) {
            covariance[i, i] = variance
            covarianceL[i, i] = sqrt(variance)
        }
        return CovarianceLinearModel(
                family, link, loss, regularization, regularizationFactor, learningRate, exploration, 0L, vectors.zeroVector(weights.size),
                covariance, covarianceL, bias, 1f / variance)
    }
}

