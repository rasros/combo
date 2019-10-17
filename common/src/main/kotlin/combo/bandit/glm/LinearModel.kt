package combo.bandit.glm

import combo.math.*
import kotlin.math.sqrt
import kotlin.random.Random

abstract class LinearModel<L : LinearData>(
        val family: VarianceFunction,
        val link: Transform,
        val weights: Vector,
        var bias: Float,
        var biasPrecision: Float,
        var learningRate: Float) {

    fun predict(input: VectorView) = link.apply(bias + (input dot weights))
    abstract fun sample(rng: Random): Vector
    abstract fun train(input: VectorView, result: Float, weight: Float)
    abstract fun importData(data: L)
    abstract fun exportData(): L
    abstract fun trainAll(inputs: Array<out VectorView>, results: FloatArray, weights: FloatArray?)
}

class DiagonalModel(varianceFunction: VarianceFunction,
                    link: Transform,
                    weights: Vector,
                    val precision: Vector,
                    bias: Float,
                    biasPrecision: Float,
                    learningRate: Float)
    : LinearModel<DiagonalCovarianceData>(varianceFunction, link, weights, bias, biasPrecision, learningRate) {


    override fun sample(rng: Random) = vectors.zeroVector(weights.size).apply {
        transformIndexed { i, _ ->
            rng.nextNormal(weights[i], sqrt(1f / precision[i]))
        }
    }

    override fun train(input: VectorView, result: Float, weight: Float) {
        val pred = predict(input)
        val diff = result - pred
        val varF = family.variance(pred)
        val alpha = learningRate * weight
        for (i in input) {
            precision[i] += varF * alpha
            weights[i] += (diff * alpha) / precision[i]
        }

        biasPrecision += varF * alpha
        bias += (diff * alpha) / biasPrecision
    }

    override fun trainAll(inputs: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {
        val precisionUpdate = vectors.zeroVector(this.weights.size)
        val weightsUpdate = vectors.zeroVector(this.weights.size)
        var biasPrecisionUpdate = 0.0f
        var biasUpdate = 0.0f
        for (i in inputs.indices) {
            val input = inputs[i]
            val result = results[i]
            val weight = weights?.get(i) ?: 1.0f
            val pred = predict(input)
            val diff = result - pred
            val varF = family.variance(pred)
            val alpha = learningRate * weight
            for (j in input) {
                precisionUpdate[j] += varF * alpha
                weightsUpdate[j] += diff * alpha
            }
            biasPrecisionUpdate += varF * alpha
            biasUpdate += diff * alpha
        }

        precision.add(precisionUpdate)
        weightsUpdate.divide(precision)
        this.weights.add(weightsUpdate)

        biasPrecision += biasPrecisionUpdate
        bias += biasUpdate / biasPrecision
    }

    override fun importData(data: DiagonalCovarianceData) {
        bias = data.bias
        biasPrecision = data.biasPrecision
        for (i in weights.indices) {
            weights[i] = data.weights[i]
            precision[i] = data.precision[i]
        }
    }

    override fun exportData() = DiagonalCovarianceData(weights.toFloatArray(), precision.toFloatArray(), bias, biasPrecision)
}

class FullModel(
        family: VarianceFunction,
        link: Transform,
        weights: Vector,
        val covariance: Matrix,
        val covarianceL: Matrix,
        bias: Float,
        biasPrecision: Float,
        learningRate: Float)
    : LinearModel<FullCovarianceData>(family, link, weights, bias, biasPrecision, learningRate) {

    override fun sample(rng: Random) =
            (covarianceL * vectors.vector(FloatArray(weights.size) { rng.nextNormal() })).apply { add(weights) }

    override fun train(input: VectorView, result: Float, weight: Float) {
        val pred = predict(input)
        val diff = result - pred
        val varF = family.variance(pred)
        val alpha = learningRate * weight

        val varianceUpdate = covariance * input
        varianceUpdate.multiply(varF)
        varianceUpdate.divide(sqrt((1 + (input dot varianceUpdate) * sqrt(varF))))
        varianceUpdate.multiply(alpha)

        var norm = covarianceL.choleskyDowndate(varianceUpdate)
        if (norm > 1f) {
            // Numerical errors have been accrued to fix we recalculate cholesky decomposition
            val L = covariance.cholesky()
            for (i in 0 until L.rows)
                for (j in 0 until L.rows)
                    covarianceL[j, i] = L[i, j] // L is lower triangulate, cholesky is upper

            norm = covarianceL.choleskyDowndate(varianceUpdate)
            while (norm > 1f) {
                // Still failed cholesky downdate due to numerical instability
                // Take smaller step instead
                varianceUpdate.divide(norm + 1e-4f)
                norm = covarianceL.choleskyDowndate(varianceUpdate)
            }
        }

        // Down date covariance matrix
        for (i in 0 until covariance.rows)
            for (j in 0 until covariance.rows)
                covariance[i, j] -= varianceUpdate[i] * varianceUpdate[j]

        val step = covariance * (input * (diff * alpha))
        weights.add(step)

        biasPrecision += varF * alpha
        bias += (diff * alpha) / biasPrecision
    }

    override fun trainAll(inputs: Array<out VectorView>, results: FloatArray, weights: FloatArray?) {
        for (i in inputs.indices)
            train(inputs[i], results[i], weights?.get(i) ?: 1.0f)
    }

    override fun importData(data: FullCovarianceData) {
        bias = data.bias
        biasPrecision = data.biasPrecision
        for (i in weights.indices) {
            weights[i] = data.weights[i]
            covariance[i] = vectors.vector(data.covariance[i])
            covarianceL[i] = vectors.vector(data.covarianceL[i])
        }
    }

    override fun exportData() = FullCovarianceData(
            weights.toFloatArray(), covariance.toArray(), covarianceL.toArray(), bias, biasPrecision)
}

