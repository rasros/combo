package combo.bandit.univariate

import combo.bandit.ParallelMode
import combo.math.BucketSample
import combo.math.nextNormal
import combo.util.RandomSequence
import combo.util.millis
import org.junit.Test
import java.lang.Thread.sleep
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals

class ConcurrentUnivariateBanditTest {

    @Test
    fun parallelUpdate() {
        val nbrBandits = 2
        val nbrSimulators = 8
        val nbrUpdators = 2
        val nbrArms = 1000

        val bandit = MultiArmedBandit(nbrArms, ThompsonSampling(NormalPosterior)).parallel(10..50, ParallelMode.BOUNDED_QUEUE, nbrBandits)
        //val bandit = MultiArmedBandit(nbrArms, ThompsonSampling(NormalPosterior)).parallel(10..50, ParallelMode.BOUNDED_QUEUE, nbrBandits)
        bandit.rewards = BucketSample()

        val outer = 1_000
        val inner = 1_000
        val n = inner * outer
        var sims = 0
        var upds = 0
        val simulators = Executors.newFixedThreadPool(nbrSimulators, { Thread(it, "Sim-${sims++}") })
        val updators = Executors.newFixedThreadPool(nbrUpdators, { Thread(it, "Upd-${upds++}") })
        val t0 = millis()
        val rns = RandomSequence(0)
        val futures = Array(nbrUpdators) {
            updators.submit {
                while (!Thread.currentThread().isInterrupted) {
                    if (bandit.processUpdates(true) == 0)
                        sleep(5L)
                }
            }
        }
        val cdl = CountDownLatch(outer)
        for (i in 0 until outer) {
            simulators.submit {
                val rng = rns.next()
                var j = 0
                while (j < inner) {
                    if (rng.nextFloat() < 0.1f) {
                        val samples = rng.nextInt(200) + 1
                        if (samples + j < inner) {
                            val indices = IntArray(samples) { rng.nextInt(nbrArms) }
                            val results = FloatArray(samples) { rng.nextFloat() }
                            bandit.choose()
                            bandit.updateAll(indices, results, null)
                            j += samples
                        }
                    } else {
                        bandit.choose()
                        bandit.update(i % nbrArms, (i + j).toFloat())
                        j++
                    }
                }
                cdl.countDown()
            }
        }

        cdl.await()
        simulators.shutdown()
        simulators.awaitTermination(1L, TimeUnit.HOURS)

        while (bandit.processUpdates(false) > 0) {
        }

        futures.forEach { it.cancel(true) }
        updators.shutdown()
        updators.awaitTermination(1L, TimeUnit.HOURS)

        while (bandit.processUpdates(false) > 0) {
        }

        println(millis() - t0)

        if (bandit.rewards.nbrSamples < n) {
            println()
            println(bandit.processUpdates(false))
        }

        assertEquals(n.toLong(), bandit.rewards.nbrSamples)
        assertEquals(n, bandit.exportData().map { it.nbrSamples }.sum().toInt())
    }

    /*
    @Test
    fun competeVsNonParallel() {
        val outerLoops = 1000
        val parallelScores = FloatArray(outerLoops)
        val nonParallelScores = FloatArray(outerLoops)
        val parallellPool = Executors.newFixedThreadPool(10)
        val nonParallellPool = Executors.newFixedThreadPool(1)
        try {
            for (outer in 0 until outerLoops) {
                val parallelBandit = UnivariateBandit(20, ThompsonSampling(NormalPosterior))
                parallelBandit.rewards = FullSample()
                parallelBandit.lock = StampedLock()

                val nonParallelBandit = UnivariateBandit(20, ThompsonSampling(NormalPosterior))
                nonParallelBandit.rewards = FullSample()
                val cdl1 = CountDownLatch(1000)
                val cdl2 = CountDownLatch(1000)

                val rns1 = RandomSequence(nanos().toInt())
                val rns2 = RandomSequence(nanos().toInt())

                for (i in 0 until 1000) {
                    parallellPool.submit {
                        val a = parallelBandit.choose()
                        parallelBandit.update(a, rns1.next().nextNormal(0.001f * a.toFloat(), 2.0f))
                        cdl1.countDown()
                    }
                    nonParallellPool.submit {
                        val a = nonParallelBandit.choose()
                        nonParallelBandit.update(a, rns2.next().nextNormal(0.001f * a.toFloat(), 2.0f))
                        cdl2.countDown()
                    }
                }
                cdl1.await()
                cdl2.await()
                parallelScores[outer] = parallelBandit.rewards.toArray().sum()
                nonParallelScores[outer] = nonParallelBandit.rewards.toArray().sum()

            }
        } finally {
            parallellPool.shutdownNow()
            nonParallellPool.shutdownNow()
        }
        parallellPool.shutdown()
        nonParallellPool.shutdown()
        parallellPool.awaitTermination(10L, TimeUnit.SECONDS)
        nonParallellPool.awaitTermination(10L, TimeUnit.SECONDS)


        val v1 = parallelScores.asSequence().sample(RunningVariance())
        val v2 = nonParallelScores.asSequence().sample(RunningVariance())
        println("parallel")
        println(v1)
        println("non-parallel")
        println(v2)
    }

     */
    private fun <D> UnivariateBandit<D>.simulate(rng: Random) = Runnable {
        val a = choose()
        update(a, rng.nextNormal(a.toFloat() * 0.001f, 2.0f))
    }

}
