package combo.bandit.nn

import combo.math.LogitTransform
import combo.math.nextNormal
import combo.model.TestModels
import combo.sat.optimizers.ExhaustiveSolver
import combo.util.intListOf
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertTrue

@Ignore
class DL4jNetworkTest {
    @Test
    fun experiment() {
        val problem = TestModels.MODEL1.problem
        val network = DL4jNetwork.Builder(problem).randomSeed(0).output(LogitTransform).learningRate(0.1f).build()
        val instances1 = ExhaustiveSolver(problem, 0).asSequence(intListOf(1)).take(50)
        val instances2 = ExhaustiveSolver(problem, 1).asSequence(intListOf(-1)).take(50)

        val rng = Random(0)
        val input = (instances1 + instances2).toList().toTypedArray()
        val results = FloatArray(100) { -1f + rng.nextNormal() } + FloatArray(100) { 1f + rng.nextNormal() }
        network.trainAll(input, results)

        val pred1 = instances1.map { network.predict(it) }.sum()
        val pred2 = instances2.map { network.predict(it) }.sum()
        assertTrue(pred1 < pred2)
    }
}
