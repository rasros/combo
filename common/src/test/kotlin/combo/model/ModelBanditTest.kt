package combo.model

import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.math.nextNormal
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test


class ModelBanditTest {
    @Test
    @Ignore
    fun dtBandit() {
        // TODO this test does nothing yet
        val model = TestModels.MODEL1
        val dtBandit = ModelBandit.treeBandit(model, ThompsonSampling(NormalPosterior))
        dtBandit.bandit.randomSeed = 0
        val rng = Random(1)
        for (i in 1..1000) {
            val assignment = dtBandit.chooseOrThrow(model.index["f1"])
            dtBandit.update(assignment, rng.nextNormal(0.2f, 0.3f))
            val assignment1 = dtBandit.chooseOrThrow(!model.index["f1"])
            dtBandit.update(assignment1, rng.nextNormal(0.1f, 0.5f))
        }

        println(dtBandit.predict(dtBandit.chooseOrThrow(model.index["f1"])))
        println(dtBandit.predict(dtBandit.chooseOrThrow(!model.index["f1"])))
    }
}