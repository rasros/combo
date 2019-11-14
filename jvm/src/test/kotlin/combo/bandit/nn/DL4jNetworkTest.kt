package combo.bandit.nn

import combo.math.LogitTransform
import combo.model.TestModels
import combo.sat.optimizers.ExhaustiveSolver
import org.junit.Test
import kotlin.test.assertTrue

class DL4jNetworkTest {
    @Test
    fun experiment() {
        val problem = TestModels.MODEL1.problem
        val network = DL4jNetwork.Builder(problem).randomSeed(0).output(LogitTransform).build()
        val instance1 = ExhaustiveSolver(problem, 0).witnessOrThrow()
        val instance2 = ExhaustiveSolver(problem, 1).witnessOrThrow()

        network.trainAll(Array(100) { instance1 } + Array(100) { instance2 },
                FloatArray(100) { 0f } + FloatArray(100) { 1f })

        val pred1 = network.predict(instance1)
        val pred2 = network.predict(instance2)
        assertTrue(pred1 < pred2)
    }
}
