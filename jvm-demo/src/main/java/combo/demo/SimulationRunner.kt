package combo.demo

import combo.bandit.BanditBuilder
import combo.bandit.ParallelMode
import combo.bandit.RandomBandit
import combo.bandit.dt.DecisionTreeBandit
import combo.bandit.dt.RandomForestBandit
import combo.bandit.glm.*
import combo.bandit.nn.DL4jNetwork
import combo.bandit.nn.NeuralLinearBandit
import combo.bandit.univariate.BinomialPosterior
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.demo.models.autocomplete.AutoCompleteSurrogate
import combo.demo.models.topcategory.TopCategorySurrogate
import combo.math.*
import combo.model.Assignment
import combo.model.Model
import combo.model.Model.Companion.model
import combo.model.PredictionModelBandit
import combo.sat.InitializerType
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.ObjectiveFunction
import combo.sat.optimizers.OptimizerBuilder
import combo.util.EmptyCollection
import combo.util.nanos
import org.apache.log4j.Level
import org.apache.log4j.Logger
import picocli.CommandLine
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random
import kotlin.system.exitProcess


@CommandLine.Command(name = "simulation", mixinStandardHelpOptions = true,
        version = ["Version 0.1"],
        description = ["Runs simulation setup."])
class SimulationRunner : Runnable {

    companion object {
        @JvmStatic
        fun main(vararg args: String) {
            val exitCode = CommandLine(SimulationRunner()).setCaseInsensitiveEnumValuesAllowed(true)
                    .execute(*args)
            if (exitCode != 0) exitProcess(exitCode)
        }

        val tc: TopCategorySurrogate by lazy { TopCategorySurrogate() }
        val ac: AutoCompleteSurrogate by lazy { AutoCompleteSurrogate(false) }
    }

    enum class Algorithm {
        ALL {
            override fun banditBuilder(simulationProblem: SimulationProblem): BanditBuilder<*> = throw UnsupportedOperationException()
        },
        DT {
            override fun banditBuilder(simulationProblem: SimulationProblem) = when (simulationProblem) {
                SimulationProblem.AC ->
                    DecisionTreeBandit.Builder(simulationProblem.surrogateModel.model, ThompsonSampling(NormalPosterior))
                            .delta(1e-5f).deltaDecay(1e-6f).tau(.3f)
                else ->
                    DecisionTreeBandit.Builder(simulationProblem.surrogateModel.model, ThompsonSampling(BinomialPosterior, BinarySum(0.08f, 1f)))
                            .delta(0.15f).deltaDecay(1e-6f).tau(.1f)
            }

            override val hyperParameters = model {
                int("delta", -256, 0)
                int("deltaDecay", -128, 127)
                int("tau", -16, 0)
            }

            override fun configureWith(metaAssignment: Assignment, builder: BanditBuilder<*>) = (builder as DecisionTreeBandit.Builder)
                    .delta(metaAssignment.getIntFloat("delta"))
                    .deltaDecay(metaAssignment.getIntFloat("deltaDecay"))
                    .tau(metaAssignment.getIntFloat("tau"))
        },
        RF {
            override fun banditBuilder(simulationProblem: SimulationProblem) = when (simulationProblem) {
                SimulationProblem.AC ->
                    RandomForestBandit.Builder(simulationProblem.surrogateModel.model, ThompsonSampling(NormalPosterior))
                            .trees(200)
                            .instanceSamplingMean(1.0f)
                            .viewedVariables(simulationProblem.surrogateModel.model.nbrVariables / 5)
                            .delta(1e-20f).deltaDecay(1e-5f).tau(.80f)
                else ->
                    RandomForestBandit.Builder(simulationProblem.surrogateModel.model, ThompsonSampling(NormalPosterior))
                            .trees(200)
                            .viewedVariables(simulationProblem.surrogateModel.model.nbrVariables / 20)
                            .delta(6.3e-21f).deltaDecay(1.5e-9f).tau(.60f)
            }

            override val hyperParameters = model {
                int("delta", -256, 0)
                int("deltaDecay", -128, 127)
                int("tau", -32, 0)
                int("samplingMean", -8, 7)
                int("nbrVariables", -16, 0)
            }

            override fun configureWith(metaAssignment: Assignment, builder: BanditBuilder<*>) = (builder as RandomForestBandit.Builder)
                    .delta(metaAssignment.getIntFloat("delta"))
                    .deltaDecay(metaAssignment.getIntFloat("deltaDecay"))
                    .tau(metaAssignment.getIntFloat("tau"))
                    .instanceSamplingMean(metaAssignment.getIntFloat("samplingMean"))
                    .viewedVariables(max(1, metaAssignment.getInt("nbrVariables") * builder.model.nbrVariables))

            override val useOptimizer = true
        },
        RF20 {
            override fun banditBuilder(simulationProblem: SimulationProblem) =
                    (RF.banditBuilder(simulationProblem) as RandomForestBandit.Builder).trees(20)

            override val useOptimizer = true
        },
        GLMDIAG {
            override fun banditBuilder(simulationProblem: SimulationProblem) = LinearBandit.Builder(simulationProblem.surrogateModel.model.problem)
                    .linearModel(DiagonalizedLinearModel.Builder(simulationProblem.surrogateModel.model.problem).let {
                        when (simulationProblem) {
                            SimulationProblem.AC -> it.family(NormalVariance).exploration(0.02f).regularizationFactor(1e-20f).priorPrecision(20f)
                            else -> it.family(BinomialVariance).exploration(0.1f).regularizationFactor(1e-6f).priorPrecision(1e4f)
                        }.build()
                    })

            override val hyperParameters = model {
                int("precision", 0, 63)
                int("exploration", -64, 0)
                int("regularizationFactor", -256, -10)
            }

            override fun configureWith(metaAssignment: Assignment, builder: BanditBuilder<*>) = (builder as LinearBandit.Builder)
                    .linearModel(DiagonalizedLinearModel.Builder(builder.problem)
                            .family((builder.linearModel as DiagonalizedLinearModel).family)
                            .priorPrecision(metaAssignment.getIntFloat("precision"))
                            .exploration(metaAssignment.getIntFloat("exploration"))
                            .regularizationFactor(metaAssignment.getIntFloat("regularizationFactor")).build())

            override val useOptimizer = true

        },
        GLMFULL {
            override fun banditBuilder(simulationProblem: SimulationProblem) = LinearBandit.Builder(simulationProblem.surrogateModel.model.problem)
                    .linearModel(CovarianceLinearModel.Builder(simulationProblem.surrogateModel.model.problem).let {
                        when (simulationProblem) {
                            SimulationProblem.AC -> it.family(NormalVariance).exploration(0.05f).regularizationFactor(1e-20f).priorVariance(0.005f)
                            else -> it.family(BinomialVariance).exploration(0.1f).regularizationFactor(1e-6f).priorVariance(1e-4f)
                        }.build()
                    })

            override val hyperParameters = model {
                int("variance", -64, 0)
                int("exploration", -64, 0)
                int("regularizationFactor", -256, -10)
            }

            override fun configureWith(metaAssignment: Assignment, builder: BanditBuilder<*>) = (builder as LinearBandit.Builder)
                    .linearModel(CovarianceLinearModel.Builder(builder.problem)
                            .family((builder.linearModel as CovarianceLinearModel).family)
                            .priorVariance(metaAssignment.getIntFloat("variance"))
                            .exploration(metaAssignment.getIntFloat("exploration"))
                            .regularizationFactor(metaAssignment.getIntFloat("regularizationFactor")).build())


            override val useOptimizer = true
        },
        NL {
            override val hyperParameters = model {
                nominal("regularizationFactor", 1e-10f, 1e-5f, 1e-3f, 1e-1f)
                nominal("hiddenLayers", 1, 2)
                nominal("epochs", 1, 3, 5)
                nominal("batchSize", 2, 4, 16, 32)
                nominal("learningRate", 1e-5f, 1e-4f, 1e-3, 1e-2f, 1e-1f)
                nominal("initWeightVariance", 1e-5f, 1e-3f, 1e-1f)

                scope("linearModelParameters") {
                    nominal("exploration", 1e-3f, 1e-2f, 1e-1f, 1f)
                    nominal("variance", 1e-3f, 1e-2f, 1e-1f, 1f)
                }
            }

            override fun configureWith(metaAssignment: Assignment, builder: BanditBuilder<*>): NeuralLinearBandit.Builder {
                builder as NeuralLinearBandit.Builder
                with(metaAssignment) {
                    builder.networkBuilder.regularizationFactor(getFloat("regularizationFactor"))
                            .hiddenLayers(getInt("hiddenLayers"))
                            .learningRate(getFloat("learningRate"))
                            .initWeightVariance(getFloat("initWeightVariance"))
                }
                return with(metaAssignment) {
                    builder.batchSize(getInt("batchSize"))
                            .epochs(getInt("epochs"))
                            .linearModel(with(metaAssignment.subAssignment("linearModelParameters")) {
                                builder.defaultLinear()
                                        .priorVariance(getFloat("variance"))
                                        .exploration(getFloat("exploration")).build()
                            })
                }
            }

            override fun banditBuilder(simulationProblem: SimulationProblem): NeuralLinearBandit.Builder {
                Logger.getRootLogger().level = Level.OFF
                return when (simulationProblem) {
                    SimulationProblem.AC -> NeuralLinearBandit.Builder(
                            DL4jNetwork.Builder(simulationProblem.surrogateModel.model.problem)
                                    .output(IdentityTransform)
                                    .learningRate(0.001f)
                                    .initWeightVariance(0.001f)
                                    .hiddenLayers(1)
                                    .hiddenLayerWidth(10)
                                    .regularizationFactor(0.001f))
                            .linearModel(CovarianceLinearModel.Builder(10)
                                    .family(NormalVariance)
                                    .exploration(0.001f)
                                    .priorVariance(0.1f)
                                    .build())
                            .batchSize(4)
                            .epochs(3)

                    else -> NeuralLinearBandit.Builder(
                            DL4jNetwork.Builder(simulationProblem.surrogateModel.model.problem)
                                    .output(LogitTransform)
                                    .learningRate(0.001f)
                                    .initWeightVariance(0.0001f)
                                    .hiddenLayers(2)
                                    .hiddenLayerWidth(10)
                                    .regularizationFactor(0.001f))
                            .linearModel(CovarianceLinearModel.Builder(10)
                                    .family(BinomialVariance)
                                    .loss(HuberLoss(0.01f))
                                    .exploration(0.001f)
                                    .priorVariance(0.1f)
                                    .build())
                            .batchSize(8)
                            .epochs(3)
                }
            }

            override val useOptimizer = true
        },
        ORACLE {
            override fun banditBuilder(simulationProblem: SimulationProblem) = OracleBandit.Builder(simulationProblem.surrogateModel)
            override val useOptimizer = true
        },
        RANDOM {
            override fun banditBuilder(simulationProblem: SimulationProblem) = RandomBandit.Builder(simulationProblem.surrogateModel.model.problem)
        };

        abstract fun banditBuilder(simulationProblem: SimulationProblem): BanditBuilder<*>
        open fun configureWith(metaAssignment: Assignment, builder: BanditBuilder<*>): BanditBuilder<*> = builder
        open val hyperParameters: Model = model {}
        open val useOptimizer: Boolean = false

        fun Assignment.getIntFloat(name: String, scale: Float = 10f) = 10f.pow(getInt(name) / scale)
    }

    enum class SimulationProblem(val surrogateModel: SurrogateModel<*>,
                                 val contextProvider: ContextProvider) {

        AC(AutoCompleteSurrogate(),
                object : ContextProvider {
                    override fun context(rng: Random) = EmptyCollection
                }) {
            override fun defaultSolver() = LocalSearch.Builder(ac.model.problem).randomSeed(nanos().toInt()).fallbackCached()
            override fun defaultOptimizer() = LocalSearch.Builder(ac.model.problem).randomSeed(nanos().toInt()).fallbackCached()
        },

        TOP5(tc, tc.contextProvider(5)) {
            override fun defaultSolver() = LocalSearch.Builder(tc.model.problem)
                    .restarts(5)
                    .pRandomWalk(0.05f)
                    .initializer(InitializerType.RANDOM)
                    .initializerBias(0.05f)
                    .propagateFlips(false)
                    .sparse(true)
                    .maxConsideration(200)
                    .randomSeed(nanos().toInt())
                    .cached()
                    .pNew(1f)
                    .maxSize(50)

            override fun defaultOptimizer() =

                    LocalSearch.Builder(tc.model.problem)
                            .restarts(5)
                            .pRandomWalk(0.05f)
                            .initializer(InitializerType.RANDOM)
                            .initializerBias(0.05f)
                            .propagateFlips(true)
                            .sparse(true)
                            .maxConsideration(200)
                            .randomSeed(nanos().toInt())
                            .cached()
                            .pNew(1f)
                            .maxSize(50)
        },
        TOP20(tc, tc.contextProvider(20)) {
            override fun defaultSolver() = TOP5.defaultSolver()
            override fun defaultOptimizer() = TOP5.defaultOptimizer()
        },
        TOP100(tc, tc.contextProvider(100)) {
            override fun defaultSolver() = TOP5.defaultSolver()
            override fun defaultOptimizer() = TOP5.defaultOptimizer()
        };

        abstract fun defaultSolver(): OptimizerBuilder<ObjectiveFunction>
        abstract fun defaultOptimizer(): OptimizerBuilder<ObjectiveFunction>
    }

    enum class Mode { TIME, HYPER, REWARDS }

    @CommandLine.Parameters(index = "0", description = ["The algorithm to use: \${COMPLETION-CANDIDATES}"])
    lateinit var algorithm: Algorithm

    @CommandLine.Parameters(index = "1", description = ["The problem to use: \${COMPLETION-CANDIDATES}"])
    lateinit var problem: SimulationProblem

    @CommandLine.Option(names = ["-m", "--mode"], description = ["What mode to runValid values: \${COMPLETION-CANDIDATES}."])
    var mode: Mode = Mode.REWARDS

    @CommandLine.Option(names = ["-z", "--horizon"], description = ["How many steps to run each iteration."])
    var horizon: Int = 10_000

    @CommandLine.Option(names = ["-b", "--burn-in"], description = ["How many steps to skip before measuring anything."])
    var burnIn: Int = 0

    @CommandLine.Option(names = ["-r", "--repetitions"], description = ["How many iterations to repeat the simulation."])
    var repetitions: Int = 1

    @CommandLine.Option(names = ["-f", "--file"], description = ["Print to output file, if not used the output will be to standard out."])
    var output: String? = null

    @CommandLine.Option(names = ["-t", "--threads"], description = ["How many worker threads to use."])
    var threads: Int = max(1, Runtime.getRuntime().availableProcessors())

    @CommandLine.Option(names = ["-pt", "--print-timeline"], description = ["Print timeline."])
    var printTimeline: Boolean = false

    @CommandLine.Option(names = ["-pr", "--print-repetitions"], description = ["Print repetitions."])
    var printRepetitions: Boolean = false

    @CommandLine.Option(names = ["-v", "--verbose"], description = ["Print some debug information."])
    var verbose: Boolean = false

    override fun run() {
        fun r(algorithm: Algorithm) {
            when (mode) {
                Mode.TIME -> runMeasureTime(algorithm)
                Mode.HYPER -> hyperSearch(algorithm)
                Mode.REWARDS -> runSimulation(algorithm)
            }
        }
        if (algorithm == Algorithm.ALL) {
            val root = output
            for (a in Algorithm.values()) {
                if (a == Algorithm.ALL || a == Algorithm.ORACLE || a == Algorithm.RANDOM) continue
                if (output != null) {
                    output = a.name + "_" + root
                }
                val writer: PrintStream = if (output == null) System.out
                else PrintStream(FileOutputStream(output!!, true))
                writer.println(a)
                r(a)
            }
        } else {
            r(this.algorithm)
        }
    }

    fun runSimulation(algorithm: Algorithm) {
        val writer: PrintStream = if (output == null) System.out
        else PrintStream(FileOutputStream(output!!, true))
        val timeline = Array(horizon) { RunningVariance() }
        val overall = RunningVariance()
        if (printRepetitions && printTimeline) {
            writer.println("REPETITIONS")
        }
        if (printRepetitions) {
            writer.println("Iteration,Mean rewards,Standard deviation in iteration")
        }
        for (t in 0 until repetitions) {
            val optimizer = if (algorithm.useOptimizer) problem.defaultOptimizer()
            else problem.defaultSolver()
            val banditBuilder = algorithm.banditBuilder(problem)
                    .suggestOptimizer(optimizer.randomSeed(nanos().toInt()).build())
            val bandit = banditBuilder
                    .randomSeed(nanos().toInt())
                    .parallel().copies(1).mode(ParallelMode.BLOCKING).build()
            val s = Simulation(problem.surrogateModel,
                    bandit,
                    workers = threads,
                    horizon = horizon,
                    verbose = verbose,
                    expectedRewards = FullSample(),
                    contextProvider = problem.contextProvider)
            s.start()
            s.awaitCompletion()
            val values = s.expectedRewards.values()
            for (i in values.indices) {
                timeline[i].accept(values[i])
            }
            val total = values.asSequence().sample(RunningVariance())
            timeline[t].accept(total.mean)
            overall.accept(total.mean)
            if (printRepetitions) {
                writer.println("$t,${total.mean},${total.standardDeviation}")
                writer.flush()
            }
        }
        if (printTimeline && printTimeline) {
            writer.println("TIMELINE")
            writer.println("Step,Mean rewards,Standard deviation")
            for (t in 0 until horizon) {
                writer.println("$t,${timeline[t].mean},${timeline[t].standardDeviation}")
            }
        }
        writer.println("Mean rewards,Standard deviation")
        writer.println("${overall.mean},${overall.standardDeviation}")
        if (output != null) writer.close()
    }

    fun hyperSearch(algorithm: Algorithm) {

        val metaRewards = WindowedEstimator(10, RunningVariance())
        val metaBandit = PredictionModelBandit(algorithm.hyperParameters,
                RandomForestBandit.Builder(algorithm.hyperParameters, ThompsonSampling(NormalPosterior))
                        .trees(200)
                        .splitPeriod(1)
                        .rewards(metaRewards)
                        .build())

        for (t in 1..repetitions) {
            val metaAssignment = metaBandit.chooseOrThrow()
            val optimizer = if (algorithm.useOptimizer) problem.defaultOptimizer()
            else problem.defaultSolver()
            val banditBuilder = algorithm.banditBuilder(problem)
                    .suggestOptimizer(optimizer.randomSeed(nanos().toInt()).build())
            val bandit = algorithm.configureWith(metaAssignment, banditBuilder).randomSeed(nanos().toInt())
                    .parallel().copies(1).mode(ParallelMode.BLOCKING).build()

            val s = Simulation(problem.surrogateModel, bandit,
                    horizon = horizon,
                    verbose = verbose,
                    expectedRewards = RunningVariance(),
                    workers = threads,
                    contextProvider = problem.contextProvider)
            s.start()
            s.awaitCompletion()
            val score = if ((s.expectedRewards as VarianceEstimator).nbrWeightedSamples < horizon * .99f) {
                println("Failed experiment: $metaAssignment")
                0f
            } else s.expectedRewards.values()[0]
            metaBandit.update(metaAssignment, score)
            val opt = metaBandit.optimalOrThrow()
            val optMean = metaBandit.predict(opt)
            println("Predicted optimal: $opt")
            println("$t, mean-windowed-10: ${String.format("%.4f", metaRewards.values().average())}, predicted optimal score: ${String.format("%.4f", optMean)}")
        }
    }

    fun runMeasureTime(algorithm: Algorithm) {
        val writer: PrintStream = if (output == null) System.out
        else PrintStream(FileOutputStream(output!!, true))

        for (i in 1..repetitions) {
            val chooseTime = RunningVariance()
            val updateTime = RunningVariance()
            val optimizer = if (algorithm.useOptimizer) problem.defaultOptimizer()
            else problem.defaultSolver()
            val banditBuilder = algorithm.banditBuilder(problem)
                    .suggestOptimizer(optimizer.randomSeed(nanos().toInt()).build())
            val bandit = banditBuilder.build()
            val sm = problem.surrogateModel
            for (t in 1..burnIn) {
                val instance = bandit.chooseOrThrow(problem.contextProvider.context(Random))
                bandit.update(instance, sm.reward(instance, sm.predict(instance), Random))
            }
            for (t in 1..horizon) {
                val context = problem.contextProvider.context(Random)

                val t0 = nanos()
                val instance = bandit.chooseOrThrow(context)
                val t1 = nanos()

                chooseTime.accept((t1.toFloat() - t0.toFloat()) / 1000000f)
                val result = sm.reward(instance, sm.predict(instance), Random)

                val t2 = nanos()
                bandit.update(instance, result)
                val t3 = nanos()

                updateTime.accept((t3.toFloat() - t2.toFloat()) / 1000000f)
            }
            writer.println("${chooseTime.mean},${updateTime.mean}")
        }
    }
}
