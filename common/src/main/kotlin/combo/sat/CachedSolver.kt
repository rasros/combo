package combo.sat

import combo.math.Rng
import kotlin.jvm.JvmOverloads

class CachedSolver @JvmOverloads constructor(val baseSolver: Solver,
                                             val size: Int = 100,
                                             val tries: Int = 10,
                                             val pEviction: Double = 0.05,
                                             val rng: Rng = Rng()) : Solver {

    private val cache: Array<Labeling> = Array(size) { config.labelingBuilder.build(0) }
    private var currentSize = 0

    override fun witnessOrThrow(contextLiterals: Literals): Labeling {
        val c = Conjunction(contextLiterals)
        var labeling: Labeling? = null
        var ix = 0
        val rng = config.nextRng()
        for (i in 0 until tries) {
            ix = rng.int(size)
            if (ix >= currentSize) {
                labeling = baseSolver.witnessOrThrow(contextLiterals)
                cache[currentSize] = labeling
                currentSize++
                return labeling
            } else {
                labeling = cache[ix]
                if (c.satisfies(labeling)) break
            }
        }

        if (labeling == null || !c.satisfies(labeling)) {
            labeling = baseSolver.witnessOrThrow(contextLiterals)
            cache[ix] = labeling
        } else if (rng.double() < pEviction) {
            cache[ix] = cache[currentSize - 1]
            currentSize--
        }

        return labeling
    }

    override val config: SolverConfig
        get() = baseSolver.config
}

