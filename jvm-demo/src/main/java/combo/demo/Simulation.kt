package combo.demo

import combo.bandit.ParallelBandit
import combo.math.DataSample
import combo.math.FullSample
import combo.math.RunningVariance
import combo.sat.Instance
import combo.sat.optimizers.ObjectiveFunction
import combo.sat.optimizers.Optimizer
import combo.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max

interface SurrogateModel<O : ObjectiveFunction> {
    fun reward(instance: Instance): Float
    fun predict(instance: Instance): Float
    fun optimal(optimizer: Optimizer<O>, assumptions: IntCollection = EmptyCollection): Instance?
}

class Simulation(val surrogateModel: SurrogateModel<*>,
                 val bandit: ParallelBandit<*>,
                 val horizon: Int = Int.MAX_VALUE,
                 val endTime: Long = Long.MAX_VALUE,
                 val workers: Int = max(1, Runtime.getRuntime().availableProcessors()),
                 val expectedRewards: DataSample = FullSample(),
                 val duration: RunningVariance = RunningVariance()) {

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
            println("Updater thread interrupted.")
            while (bandit.processUpdates(false) > 0) {
            }
            updaterCdl.countDown()
            println("Updater thread done.")
        }
    }
    private val steps = AtomicInt(0)

    private val durationLock = ReentrantLock()
    val cdl = CountDownLatch(workers)
    val updaterCdl = CountDownLatch(1)

    private val rewardLock = ReentrantLock()

    private inner class Worker(val it: Int) : Runnable {
        override fun run() {
            while (!Thread.currentThread().isInterrupted) {
                val t = steps.incrementAndGet()
                if (t > horizon || endTime < millis()) Thread.currentThread().interrupt()
                else step()
            }
            cdl.countDown()
            println("Worker thread $it done.")
        }
    }

    private val workerThreads = Array(workers) { Thread(Worker(it)) }

    fun start() {
        updateThread.start()
        workerThreads.forEach { it.start() }
    }

    fun step() {
        while (true) {
            val t0 = nanos()
            val instance = bandit.choose() ?: continue
            val t1 = nanos()
            durationLock.withLock {
                duration.accept((t1 - t0).toFloat())
            }
            rewardLock.withLock {
                expectedRewards.accept(surrogateModel.predict(instance))
            }
            bandit.update(instance, surrogateModel.reward(instance))
            return
        }
    }

    fun awaitCompletion() {
        cdl.await()
        updaterCdl.await()
    }

    fun interrupt() {
        updateThread.interrupt()
        workerThreads.forEach { it.interrupt() }
    }
}
