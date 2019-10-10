package combo.bandit.glm

/*
class GLMBandit(val problem: Problem,
                val maximize: Boolean = true,
                val family: VarianceFunction,
                val link: Transform = family.canonicalLink(),
                val lambda: Double = 1.0,
                val randomSeed: Long = nanos(),
                val optimizer: Optimizer<LinearObjective>,
                override val rewards: DataSample = BucketSample(),
                override val trainAbsError: DataSample = BucketSample(),
                override val testAbsError: DataSample = BucketSample(),
        //bias: Double,
        //val weights: Vector = DoubleArray(problem.binarySize),
        //val sampler: DiagonalSampler = DiagonalSampler(problem.binarySize, lambda)
                val model: RegressionModel = DiagonalModel(problem.binarySize, lambda)
) : PredictionBandit {

    private val randomSequence = RandomSequence(randomSeed)
    var bias: Double = bias
        private set

    init {
        require(weights.size == problem.binarySize) { "Weight prior parameter must be same size as problem: ${problem.binarySize}." }
        require(lambda >= 1E-6) { "Lambda prior parameter should not be too close to zero (1E-6)." }
    }

    override fun predict(instance: Instance) = link.apply(bias + (instance dot weights))

    override fun train(instance: Instance, result: Double, weight: Double) {
        val pred = predict(instance)
        val diff = result - pred
        val varF = family.variance(pred)
        sampler.update(weights, instance, rewards.nbrSamples.toDouble(), lambda, diff, varF, 1.0)
    }

    override fun chooseOrThrow(assumptions: IntArray): Instance {
        val rng = randomSequence.next()
        val sampled = sampler.sample(weights, rng)
        return optimizer.optimizeOrThrow(LinearObjective(maximize, sampled), assumptions)
    }

}

/**
 * @param Cholesky decomposition
 * @param scale     L'*L*scale = scale*Sigma^-1
 */
fun Random.nextMultiGaussian(means: Vector, L: Matrix, scale: Double = 1.0) =
        means + DoubleArray(means.size) { nextGaussian() * scale } * L

sealed class RegressionModel(bias: Double,
                             nbrUpdates: Long,
                             val weights: Vector,
                             val lambda: Double,
                             val link: Transform,
                             val family: VarianceFunction,
                             val scaling: VarianceEstimator) {
    var bias: Double = bias
        protected set
    var nbrUpdates: Long = nbrUpdates
        protected set

    fun predict(instance: Instance) = link.apply(bias + (instance dot weights))

    abstract fun sample(rng: Random): Vector
    abstract fun update(instance: Instance, result: Double, weight: Double)
}

class DiagonalModel(bias: Double,
                    nbrUpdates: Long,
                    weights: Vector,
                    lambda: Double,
                    link: Transform,
                    family: VarianceFunction,
                    scaling: VarianceEstimator,
                    val covariance: Vector)
    : RegressionModel(bias, nbrUpdates, weights, lambda, link, family, scaling) {

    override fun update(instance: Instance, result: Double, weight: Double) {
        nbrUpdates++
        val pred = predict(instance)
        val diff = result - pred
        val varF = family.variance(pred)
        //     step = H_inv * (x*dlik-reg) / scaling;
        val itr = instance.truthIterator()
        while (itr.hasNext()) {
            val i = itr.nextInt().toIx()
            val reg = lambda * weights[i] / nbrUpdates
            val update = covariance[i] * (diff - reg) / scaling.mean
        }
    }

    override fun sample(rng: Random) = DoubleArray(weights.size) {
        rng.nextGaussian(weights[it], sqrt(covariance[it]))
    }
}

/**
 * @param cholesky Cholesky decomposition of covariance matrix
 */
/*
class FullSampler(val covariance: Matrix, val cholesky: Matrix, biasVar: Double) : Sampler {

    var biasVar: Double = biasVar
        private set

    override fun update(weights: Vector, instance: Instance, t: Double, lambda: Double, diff: Double, varF: Double, scaling: Double) {

        val v: Vector = instance.toDoubleArray().apply { multiply(sqrt(varF)) }
        val u: Vector = (covariance * v)
        u.divide(sqrt(1.0 + (u dot v)))

        for (i in 0 until covariance.size)
            for (j in 0 until covariance.size)
                covariance[i][j] -= u[i] * u[j]
        cholesky.cholDowndate(u)

        val x = instance.toDoubleArray()
        val reg: Vector = lambda * weights / t
        reg[0] = 0.0 // TODO parameter
        val step = covariance * (x.apply { multiply(diff) }.apply { sub(reg) }.apply { divide(scaling) })

        weights.add(step)
        //biasVar +=
    }

    constructor(size: Int, lambda: Double) : this(
            Array(size) { DoubleArray(size) }.apply {
                for (i in indices)
                    this[i][i] = lambda
            },
            Array(size) { DoubleArray(size) }.apply {
                for (i in indices)
                    this[i][i] = sqrt(lambda)
            }, lambda)

    override fun sample(weights: Vector, rng: Random) = rng.nextMultiGaussian(weights, cholesky)
}
*/
*/
