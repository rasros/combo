package combo.bandit

import combo.math.*
import combo.sat.Conjunction
import combo.sat.Labeling
import combo.sat.UnsatisfiableException
import combo.sat.SolverConfig
import kotlin.jvm.JvmOverloads

class MultiArmedBandit @JvmOverloads constructor(bandits: Array<Labeling>,
                                                 override val config: SolverConfig,
                                                 val posterior: Posterior,
                                                 override val rewards: DataSample = GrowingDataSample(20),
                                                 val prior: VarianceStatistic = posterior.defaultPrior()) : Bandit {

    val banditMap: Map<Labeling, VarianceStatistic> = bandits.associate { Pair(it, prior.copy()) }

    override fun update(labeling: Labeling, result: Double, weight: Double) {
        rewards.accept(result)
        posterior.update(banditMap[labeling]!!, result, weight)
    }

    override fun chooseOrThrow(contextLiterals: IntArray): Labeling {
        val rng = config.nextRandom()
        val con = Conjunction(contextLiterals)
        val labeling = if (config.maximize) {
            banditMap.maxBy { if (con.satisfies(it.key)) posterior.sample(rng, it.value) else Double.NEGATIVE_INFINITY }
        } else {
            banditMap.minBy { if (con.satisfies(it.key)) posterior.sample(rng, it.value) else Double.POSITIVE_INFINITY }
        }?.key
        if (labeling == null || !con.satisfies(labeling)) throw UnsatisfiableException("No labeling matching context literals.")
        return labeling
    }

    val data: Array<VarianceStatistic> = Array(bandits.size) { RunningVariance() }
}
