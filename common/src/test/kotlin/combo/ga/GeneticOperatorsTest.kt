package combo.ga

import combo.math.DescriptiveStatistic
import combo.math.Rng
import combo.math.RunningVariance
import combo.sat.BitFieldLabeling
import kotlin.test.Test


// TODO more tests
class SelectTest {
    @Test
    fun select() {
        /*
        val scores = DoubleArray(10) { it.toDouble() }
        val ds = DescriptiveStatistic(RunningVariance()).apply { acceptAll(scores) }
        val state = PopulationState(Array(10) { BitFieldLabeling(0) }, IntArray(10) { it }, scores, ds)

        val ts = UniformSampling()
        val s = RunningVariance()
        for (i in 0 until 10000) {
            s.accept(ts.select(10, scores, Rng(), state).toDouble())
        }
        println(s)
        */
    }
}
