package combo.bandit

import combo.bandit.univariate.BanditPolicy
import combo.math.DataSample
import combo.math.VarianceEstimator
import combo.math.VoidSample
import combo.sat.*
import combo.sat.constraints.Conjunction
import combo.sat.optimizers.ExhaustiveSolver
import combo.sat.optimizers.LocalSearch
import combo.util.*

/**
 * This bandit uses an independent univariate bandit policy for each of a pre-defined [Instance].
 * Use the sequence method in [combo.sat.optimizers.Optimizer] to generate the instances.
 *
 * @param instances all arms to use by the bandit.
 * @param banditPolicy the policy that the next bandit arm is selected with.
 * @param priors set specific priors or use to import historic data after restarts.
 * @param randomSeed Set the random seed to a specific value to have a reproducible algorithm.
 * @param maximize Whether the bandit should maximize or minimize the total rewards. By default true.
 * @param rewards All rewards are added to this for inspecting how well the bandit performs.
 */
class ListBandit<E : VarianceEstimator>(instances: Array<Instance>,
                                        val banditPolicy: BanditPolicy<E>,
                                        priors: Map<Instance, E> = emptyMap(),
                                        override val randomSeed: Int = nanos().toInt(),
                                        override val maximize: Boolean = true,
                                        override val rewards: DataSample = VoidSample) : Bandit<InstancesData<E>> {

    private val instanceData = LinkedHashMap<Instance, E>().apply {
        instances.associateTo(this) {
            val e = priors[it] ?: banditPolicy.baseData()
            banditPolicy.addArm(e)
            it to e
        }
    }

    private var randomSequence = RandomSequence(randomSeed)
    private val step = AtomicLong()

    override fun importData(data: InstancesData<E>) {
        for ((instance, e) in data.instances) {
            val old = this.instanceData[instance] ?: continue
            banditPolicy.removeArm(old)
            @Suppress("UNCHECKED_CAST") val combined = old.combine(e) as E
            this.instanceData[instance] = combined
            banditPolicy.addArm(combined)
        }
    }

    override fun exportData(): InstancesData<E> {
        val itr = instanceData.iterator()
        return InstancesData(List(instanceData.size) {
            val (l, t) = itr.next()
            InstanceData(l, t)
        })
    }

    override fun update(instance: Instance, result: Float, weight: Float) {
        rewards.accept(result, weight)
        val e = instanceData[instance]
        if (e != null) banditPolicy.update(e, result, weight)
    }

    override fun chooseOrThrow(assumptions: IntCollection): Instance {
        val con: Constraint = if (assumptions.isNotEmpty()) Conjunction(assumptions) else Tautology
        val t = step.getAndIncrement()
        val rng = randomSequence.next()
        val instance = instanceData.maxBy {
            if (con.satisfies(it.key)) {
                banditPolicy.evaluate(it.value, t, maximize, rng)
            } else Float.NEGATIVE_INFINITY
        }?.key
        if (instance == null || !con.satisfies(instance))
            throw UnsatisfiableException("No instance matching assumption literals.")
        return instance
    }

    class Builder<E : VarianceEstimator> private constructor(
            val banditPolicy: BanditPolicy<E>,
            private val problem: Problem? = null,
            private val limit: Int = 500,
            private val instances: MutableList<Instance>? = null)
        : BanditBuilder<InstancesData<E>> {

        private var randomSeed: Int = nanos().toInt()
        private var maximize: Boolean = true
        private var rewards: DataSample = VoidSample
        private var import: InstancesData<E>? = null

        /**
         * Use pre-generated [instances] as base.
         */
        constructor(instances: Collection<Instance>, banditPolicy: BanditPolicy<E>)
                : this(banditPolicy, instances = instances.toMutableList())

        /**
         * Generates up to [limit] instances using the specified constraints in the [problem].
         */
        constructor(problem: Problem, banditPolicy: BanditPolicy<E>, limit: Int = 500) : this(
                banditPolicy, problem, limit)

        override fun importData(data: InstancesData<E>) = apply { this.import = data }
        override fun randomSeed(randomSeed: Int) = apply { this.randomSeed = randomSeed }
        override fun maximize(maximize: Boolean) = apply { this.maximize = maximize }
        override fun rewards(rewards: DataSample) = apply { this.rewards = rewards }
        override fun parallel() = ParallelBandit.Builder(this)

        override fun build(): ListBandit<E> {
            val priors = if (import != null) {
                val priors = HashMap<Instance, E>()
                for ((instance, e) in import!!.instances)
                    @Suppress("UNCHECKED_CAST")
                    priors[instance] = e.copy() as E
                priors
            } else emptyMap<Instance, E>()
            val instances = if (instances != null) {
                if (import != null) {
                    val instanceSet = instances.toHashSet()
                    instanceSet.addAll(priors.keys)
                    instanceSet.toTypedArray()
                } else instances.toTypedArray()
            } else {
                problem!!
                val optimizer = if (problem.nbrValues <= 14) ExhaustiveSolver(problem, randomSeed)
                else LocalSearch.Builder(problem).restarts(Int.MAX_VALUE).randomSeed(randomSeed).build()
                val data = if (optimizer.complete) optimizer.asSequence().take(limit)
                else optimizer.asSequence().distinct().take(limit)
                if (import != null) {
                    (data + import!!.instances.asSequence().map { it.instance }).toSet().toTypedArray()
                } else data.toList().toTypedArray()
            }
            return ListBandit(instances = instances, banditPolicy = banditPolicy,
                    randomSeed = randomSeed, maximize = maximize, rewards = rewards, priors = priors)
        }
    }
}
