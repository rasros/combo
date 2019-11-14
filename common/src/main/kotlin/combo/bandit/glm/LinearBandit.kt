package combo.bandit.glm

import combo.bandit.ParallelPredictionBandit
import combo.bandit.PredictionBandit
import combo.bandit.PredictionBanditBuilder
import combo.math.DataSample
import combo.math.VoidSample
import combo.sat.BitArray
import combo.sat.Instance
import combo.sat.Problem
import combo.sat.optimizers.LinearObjective
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.RandomSequence
import combo.util.nanos

/**
 * Generalized linear model (GLM) bandit with Thompson sampling. The most important configuration parameter is [family],
 * which should be set according to the type of rewards the bandit receives.
 *
 * @param problem The problem contains the [combo.sat.Constraint]s and the number of variables.
 * @param model Initial weight data to start with.
 * @param randomSeed Set the random seed to a specific value to have a reproducible algorithm.
 * @param maximize Whether the bandit should maximize or minimize the total rewards. By default true.
 * @param optimizer Which optimizer to use for maximization of the linear function.
 * @param rewards All rewards are added to this for inspecting how well the bandit performs.
 * @param trainAbsError The total absolute error obtained on a prediction before update.
 * @param testAbsError The total absolute error obtained on a prediction after update.
 */
class LinearBandit(val problem: Problem,
                   val model: LinearModel,
                   override val randomSeed: Int = nanos().toInt(),
                   override val maximize: Boolean = true,
                   val optimizer: Optimizer<LinearObjective> = LocalSearch.Builder(problem).randomSeed(randomSeed).build(),
                   override val rewards: DataSample = VoidSample,
                   override val trainAbsError: DataSample = VoidSample,
                   override val testAbsError: DataSample = VoidSample)
    : PredictionBandit<LinearData> {

    private val randomSequence = RandomSequence(randomSeed)

    init {
        require(model.weights.size == problem.nbrValues) {
            "Weight prior parameter must be same size as problem: ${problem.nbrValues}."
        }
    }

    override fun predict(instance: Instance) = model.predict(instance)

    override fun train(instance: Instance, result: Float, weight: Float) {
        model.train(instance, result, weight)
    }

    override fun trainAll(instances: Array<Instance>, results: FloatArray, weights: FloatArray?) {
        model.trainAll(instances, results, weights)
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        if (problem.nbrValues == 0) return BitArray(0)
        val rng = randomSequence.next()
        val sampled = model.sample(rng)
        return optimizer.optimizeOrThrow(LinearObjective(maximize, sampled), assumptions)
    }

    override fun optimalOrThrow(assumptions: IntCollection): Instance {
        return optimizer.optimizeOrThrow(LinearObjective(maximize, model.weights), assumptions)
    }

    override fun importData(data: LinearData) {
        val ratio = if (data.step <= 0L) 1f
        else data.step.toFloat() / (model.step + data.step).toFloat()
        model.importData(data, ratio, ratio)
    }

    override fun exportData() = model.exportData()

    class Builder(val problem: Problem) : PredictionBanditBuilder<LinearData> {

        private var randomSeed: Int = nanos().toInt()
        private var rewards: DataSample = VoidSample
        private var maximize: Boolean = true
        private var optimizer: Optimizer<LinearObjective>? = null
        private var trainAbsError: DataSample = VoidSample
        private var testAbsError: DataSample = VoidSample
        private var _model: LinearModel? = null

        val linearModel: LinearModel get() = _model ?: initLinearModel()

        private fun initLinearModel() = PrecisionLinearModel.Builder(problem).build()

        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun trainAbsError(trainAbsError: DataSample) = apply { this.trainAbsError = trainAbsError }
        override fun testAbsError(testAbsError: DataSample) = apply { this.testAbsError = testAbsError }
        override fun parallel() = ParallelPredictionBandit.Builder(this)
        override fun importData(data: LinearData) = apply { linearModel.importData(data) }
        fun linearModel(linearModel: LinearModel) = apply { _model = linearModel }

        /** Which optimizer to use for maximization of the linear function. */
        fun optimizer(optimizer: Optimizer<LinearObjective>) = apply { this.optimizer = optimizer }

        private fun defaultOptimizer() = optimizer ?: LocalSearch.Builder(problem)
                .randomSeed(randomSeed).fallbackCached().build()

        override fun build(): LinearBandit = LinearBandit(problem, linearModel, randomSeed, maximize,
                defaultOptimizer(), rewards, trainAbsError, testAbsError)
    }
}