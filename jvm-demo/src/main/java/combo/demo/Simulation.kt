package combo.demo

import combo.bandit.Bandit
import combo.bandit.BanditBuilder
import combo.bandit.ParallelBandit
import combo.math.DataSample
import combo.math.FullSample
import combo.math.RunningVariance
import combo.math.VoidSample
import combo.model.Model
import combo.sat.Instance
import combo.sat.UnsatisfiableException
import combo.sat.ValidationException
import combo.sat.optimizers.LocalSearch
import combo.sat.optimizers.ObjectiveFunction
import combo.sat.optimizers.Optimizer
import combo.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.random.Random

interface SurrogateModel<O : ObjectiveFunction> {
    val model: Model
    fun reward(instance: Instance, prediction: Float, rng: Random): Float
    fun predict(instance: Instance): Float
    fun optimal(optimizer: Optimizer<O>, assumptions: IntCollection = EmptyCollection): Instance?
}

class OracleBandit<O : ObjectiveFunction>(val optimizer: Optimizer<O>, val surrogateModel: SurrogateModel<O>, override val maximize: Boolean = true, override val rewards: DataSample = VoidSample) : Bandit<Nothing> {

    override fun chooseOrThrow(assumptions: IntCollection) = surrogateModel.optimal(optimizer)
            ?: throw UnsatisfiableException("Failed to generate optimal instance.")

    override fun optimalOrThrow(assumptions: IntCollection) = surrogateModel.optimal(optimizer)
            ?: throw UnsatisfiableException("Failed to generate optimal instance.")

    override fun update(instance: Instance, result: Float, weight: Float) {}
    override fun importData(data: Nothing) {}
    override fun exportData() = error("Cannot export.")

    override val randomSeed: Int get() = optimizer.randomSeed

    class Builder<O : ObjectiveFunction>(val surrogateModel: SurrogateModel<O>) : BanditBuilder<Nothing> {

        private var rewards: DataSample = VoidSample
        private var maximize: Boolean = true
        private var randomSeed: Int = nanos().toInt()
        private var optimizer: Optimizer<O>? = null

        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        fun optimizer(optimizer: Optimizer<O>) = apply { this.optimizer = optimizer }

        override fun importData(data: Nothing) = this
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }

        override fun parallel() = ParallelBandit.Builder(this)

        override fun build() = OracleBandit(optimizer
                ?: LocalSearch.Builder(surrogateModel.model.problem).randomSeed(randomSeed).fallbackCached().build(),
                surrogateModel, maximize, rewards)
    }
}

interface ContextProvider {
    fun context(rng: Random): IntCollection
}

class Simulation(val surrogateModel: SurrogateModel<*>,
                 val bandit: ParallelBandit<*>,
                 val horizon: Int = Int.MAX_VALUE,
                 val endTime: Long = Long.MAX_VALUE,
                 val workers: Int = max(1, Runtime.getRuntime().availableProcessors()),
                 val expectedRewards: DataSample = FullSample(),
                 val duration: RunningVariance = RunningVariance(),
                 val log: Boolean = true,
                 val contextProvider: ContextProvider = object : ContextProvider {
                     override fun context(rng: Random) = EmptyCollection
                 },
                 val abortThreshold: Float = 0.99f) {

    private val updateThread = object : Thread() {
        override fun run() {
            while (!isInterrupted) {
                try {
                    bandit.processUpdates(true)
                    if (cdl.count == 0L) interrupt()
                } catch (e: InterruptedException) {
                    interrupt()
                }
            }
            if (log)
                println("Updater thread interrupted.")
            while (bandit.processUpdates(false) > 0) {
            }
            updaterCdl.countDown()
            if (log)
                println("Updater thread done.")
        }
    }
    private val steps = AtomicInt(0)
    private val failedSteps = AtomicInt(0)

    private val durationLock = ReentrantLock()
    val cdl = CountDownLatch(workers)
    val updaterCdl = CountDownLatch(1)

    private val rewardLock = ReentrantLock()

    private inner class Worker(val it: Int) : Runnable {
        val rng = Random(nanos() + it)
        override fun run() {
            while (!Thread.currentThread().isInterrupted) {
                val t = steps.incrementAndGet()
                if (t > horizon || endTime < millis()) Thread.currentThread().interrupt()
                else {
                    try {
                        step(rng)
                    } catch (e: ValidationException) {
                        if (log) e.printStackTrace()
                        val fails = failedSteps.incrementAndGet()
                        if (fails >= 5 && fails >= t.toFloat() * abortThreshold) {
                            if (log) println("Simulation failed due to many errors.")
                            cancelSimulation()
                        }
                    } catch (e: Exception) {
                        println("Worker thread $it failed.")
                        e.printStackTrace()
                        cancelSimulation()
                    }
                }
            }
            cdl.countDown()
            if (log)
                println("Worker thread $it done.")
            if (cdl.count == 0L)
                updateThread.interrupt()
        }
    }

    private val workerThreads = Array(workers) { Thread(Worker(it)) }

    fun start() {
        updateThread.start()
        workerThreads.forEach { it.start() }
    }

    fun step(rng: Random) {
        val context = contextProvider.context(rng)
        val t0 = nanos()
        val instance = bandit.chooseOrThrow(context)
        val t1 = nanos()
        durationLock.withLock {
            duration.accept((t1 - t0).toFloat())
        }
        var p: Float? = null
        rewardLock.withLock {
            p = surrogateModel.predict(instance)
            expectedRewards.accept(p!!)
        }
        bandit.update(instance, surrogateModel.reward(instance, p!!, rng))
    }

    fun awaitCompletion() {
        cdl.await()
        updaterCdl.await()
    }

    fun cancelSimulation() {
        if (log)
            println("Cancel simulation.")
        updateThread.interrupt()
        workerThreads.forEach { it.interrupt() }
    }
}
