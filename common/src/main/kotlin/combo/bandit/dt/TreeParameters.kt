package combo.bandit.dt

import combo.bandit.univariate.BanditPolicy
import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.model.Model
import combo.model.Root
import combo.model.Variable
import combo.sat.optimizers.Optimizer

/**
 * This encapsulates all parameters to either decision trees or random forest, for two reasons.
 * 1) so that we make sure that each tree in a random forest have the same parameters
 * 2) sharing some code between decision tree and random forest construction
 */
interface TreeParameters<E : VarianceEstimator> {
    val model: Model
    val banditPolicy: BanditPolicy<E>
    val optimizer: Optimizer<*>
    val randomSeed: Int
    val maximize: Boolean
    val rewards: DataSample
    val trainAbsError: DataSample
    val testAbsError: DataSample
    val splitMetric: SplitMetric
    val delta: Float
    val deltaDecay: Float
    val tau: Float
    val maxNodes: Int
    val maxDepth: Int
    val maxLiveNodes: Int
    val viewedValues: Int
    val splitPeriod: Int
    val minSamplesSplit: Float
    val minSamplesLeaf: Float
    val propagateAssumptions: Boolean
    val splitters: Map<Variable<*, *>, ValueSplitter>
    val filterMissingData: Boolean
    val blockQueueSize: Int
}

class ExtendedTreeParameters<E : VarianceEstimator>(
        override val model: Model,
        override val banditPolicy: BanditPolicy<E>,
        override val optimizer: Optimizer<*>,
        override val randomSeed: Int,
        override val maximize: Boolean,
        override val rewards: DataSample,
        override val trainAbsError: DataSample,
        override val testAbsError: DataSample,
        override val splitMetric: SplitMetric,
        override val delta: Float,
        override val deltaDecay: Float,
        override val tau: Float,
        override val maxNodes: Int,
        override val maxDepth: Int,
        override val maxLiveNodes: Int,
        override val viewedValues: Int,
        override val splitPeriod: Int,
        override val minSamplesSplit: Float,
        override val minSamplesLeaf: Float,
        override val propagateAssumptions: Boolean,
        override val splitters: Map<Variable<*, *>, ValueSplitter>,
        override val filterMissingData: Boolean,
        override val blockQueueSize: Int,
        val maxRestarts: Int) : TreeParameters<E> {

    val reifiedLiterals: IntArray? = if (filterMissingData) IntArray(model.problem.nbrValues) else null
    // TODO could be a range with binary search
    // like so : private data class ReifiedLiteral(val index: Int, val range: IntRange)

    init {
        if (reifiedLiterals != null) {
            for (variable in model.index) {
                val ix = model.index.valueIndexOf(variable)
                val offset = if (variable.optional) {
                    reifiedLiterals[ix] = variable.parentLiteral(model.index)
                    1
                } else {
                    0
                }
                val valueReification = if (variable.reifiedValue is Root) 0
                else variable.reifiedValue.toLiteral(model.index)
                if (valueReification != 0)
                    for (i in offset until variable.nbrValues) {
                        reifiedLiterals[ix + i] = valueReification
                    }
            }
        }
    }
}

