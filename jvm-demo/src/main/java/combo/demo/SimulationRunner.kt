package combo.demo

import combo.bandit.Bandit
import combo.bandit.BanditBuilder
import combo.bandit.ParallelMode
import combo.bandit.ga.GeneticAlgorithmBandit
import combo.bandit.ga.SignificanceTestElimination
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.ga.TournamentSelection
import combo.math.FullSample
import combo.math.RunningVariance
import combo.math.VarianceEstimator
import combo.math.WindowedEstimator
import combo.model.BanditHyperParameters
import combo.model.ModelBandit
import combo.util.EmptyCollection
import combo.util.nanos
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.random.Random

fun runSimulation(banditBuilders: () -> BanditBuilder<*>,
                  surrogateModel: SurrogateModel<*>,
                  horizon: Int = 100_000,
                  repetitions: Int = 100,
                  fileName: String,
                  contextProvider: ContextProvider = object : ContextProvider {
                      override fun context(rng: Random) = EmptyCollection
                  }): VarianceEstimator {
    val writer = PrintStream(FileOutputStream(fileName, true))
    val rewards = RunningVariance()
    for (t in 1..repetitions) {
        val banditBuilder = banditBuilders.invoke()
        val bandit = banditBuilder
                .randomSeed(nanos().toInt())
                .parallel().mode(ParallelMode.BLOCKING).batchSize(10..50).build()
        val s = Simulation(surrogateModel, bandit, horizon = horizon, log = false, expectedRewards = FullSample(), contextProvider = contextProvider)
        s.start()
        s.awaitCompletion()
        val values = s.expectedRewards.values()
        val sum = RunningVariance()
        for (f in values)
            sum.accept(f)

        println("$t ${sum.mean} ")
        rewards.accept(sum.mean)
        writer.println(values.joinToString(",") { it.toString() })
    }
    writer.close()
    return rewards
}

fun hyperSearch(hyperParameters: BanditHyperParameters<*>, surrogateModel: SurrogateModel<*>, horizon: Int = 100_000, repetitions: Int = 20_000) {

    val metaRewards = WindowedEstimator(100, RunningVariance())
    val m = hyperParameters.metaModel
    val metaBandit = ModelBandit(hyperParameters.metaModel,
            GeneticAlgorithmBandit.Builder(hyperParameters.metaModel.problem, ThompsonSampling(NormalPosterior))
                    .elimination(SignificanceTestElimination(0.1f))
                    .selection(TournamentSelection(5))
                    .rewards(metaRewards)
                    .allowDuplicates(false)
                    .candidateSize(60).elimination(SignificanceTestElimination()).build())

    for (t in 1..repetitions) {
        val metaAssignment = metaBandit.chooseOrThrow()
        val bandit = hyperParameters.toBandit(metaAssignment).randomSeed(nanos().toInt())
                .parallel().copies(1).mode(ParallelMode.BLOCKING).build()

        val s = Simulation(surrogateModel, bandit, horizon = horizon, log = false, expectedRewards = RunningVariance())
        s.start()
        s.awaitCompletion()
        val score = if ((s.expectedRewards as VarianceEstimator).nbrWeightedSamples < horizon * .99f) {
            println("Failed experiment: $metaAssignment")
            0f
        } else s.expectedRewards.values()[0]
        metaBandit.update(metaAssignment, score)
        val opt = metaBandit.bandit.candidates.estimators.maxBy { it.value.mean }!!
        val maxN = metaBandit.bandit.candidates.estimators.maxBy { it.value.nbrWeightedSamples }!!
        println("$t, average: ${String.format("%.3f", metaRewards.values().average())}, opt: ${String.format("%.3f", opt.value.mean)}, n: ${opt.value.nbrSamples} \t ${hyperParameters.metaModel.toAssignment(opt.key)} \t " +
                "maxN: ${maxN.value.nbrSamples} \t ${hyperParameters.metaModel.toAssignment(maxN.key)}")
    }
}

fun measureTime(bandit: Bandit<*>, model: SurrogateModel<*>, burnIn: Int = 2048, horizon: Int = 10240) {
    val chooseTime = RunningVariance()
    val updateTime = RunningVariance()
    for (t in 1..burnIn) {
        val instance = bandit.chooseOrThrow()
        bandit.update(instance, model.reward(instance, model.predict(instance), Random))
    }
    for (t in 1..horizon) {
        val t0 = nanos()
        val instance = bandit.chooseOrThrow()
        val t1 = nanos()
        chooseTime.accept((t1.toFloat() - t0.toFloat()) / 1000000f)
        val result = model.reward(instance, model.predict(instance), Random)
        val t2 = nanos()
        bandit.update(instance, result)
        val t3 = nanos()
        updateTime.accept((t3.toFloat() - t2.toFloat()) / 1000000f)
    }
    println("" + chooseTime.mean + "," + updateTime.mean)
}