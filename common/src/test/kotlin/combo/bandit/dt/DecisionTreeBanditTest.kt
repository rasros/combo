package combo.bandit.dt

import combo.bandit.PredictionBanditTest
import combo.bandit.TestParameters
import combo.bandit.univariate.UCB1Tuned
import combo.model.Model
import combo.model.Model.Companion.model
import kotlin.test.Test
import kotlin.test.assertTrue

class DecisionTreeBanditTest : PredictionBanditTest<DecisionTreeBandit<*>>() {

    override fun bandit(model: Model, parameters: TestParameters) = DecisionTreeBandit.Builder(model, parameters.thompsonPolicy())
            .rewards(parameters.rewards)
            .randomSeed(parameters.randomSeed)
            .maximize(parameters.maximize)
            .maxRestarts(Int.MAX_VALUE)
            .build()

    override fun infeasibleBandit(model: Model, parameters: TestParameters) = DecisionTreeBandit.Builder(model, parameters.thompsonPolicy())
            .rewards(parameters.rewards)
            .randomSeed(parameters.randomSeed)
            .maximize(parameters.maximize)
            .maxRestarts(1)
            .build()

    @Test
    fun exportEmpty() {
        val bandit = DecisionTreeBandit.Builder(model { bool() }, UCB1Tuned()).build()
        assertTrue(bandit.exportData().nodes.isEmpty())
    }
}
