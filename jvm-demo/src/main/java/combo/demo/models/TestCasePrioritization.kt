package combo.demo.models

import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.model.Model.Companion.model
import combo.model.ModelBandit
import combo.sat.ValidationException
import combo.sat.constraints.Relation

val testModel = model {

    val tests = arrayOf(
            flag("test A", 10),
            flag("test B", 20),
            flag("test C", 15),
            flag("test D", 5),
            flag("test E", 4))

    val s1 = model("system 1") {
        bool("component 1")
        bool("component 2")
        bool("component 3")
    }

    val s2 = model("system 2") {
        bool("component 1")
        bool("component 2")
        bool("component 3")
    }

    val systemTestCoverage = mapOf(
            s1 to arrayOf(tests[0], tests[1]),
            s2 to arrayOf(tests[1], tests[2], tests[3], tests[4])
    )

    for ((s, ts) in systemTestCoverage) {
        impose { s equivalent disjunction(*ts) }
    }

    // At least one of the tests must be run each step
    impose { disjunction(*tests) }

    // Add a time budget variable that can be dynamically specified
    val testDurations = tests.map { it.value }.toIntArray()
    val timeBudget = int("timeBudget", 0, testDurations.sum())
    impose { linear(timeBudget, Relation.LE, testDurations, tests) }
}

fun main() {

    val bandit = ModelBandit.randomForestBandit(testModel, ThompsonSampling(NormalPosterior))

    // No input, algorithm chooses what it should test freely
    // The reward from a simulation will be arbitrarily chosen to 2 here
    run {
        val actions = bandit.chooseOrThrow()
        println(actions)
        // Looks something like this
        // > {system 1=true, system 2=true, timeBudget=30, test D=5, testE=4, test B=20}
        bandit.update(actions, 2f)
    }

    // Force run system 1
    run {
        val actions = bandit.chooseOrThrow(testModel["system 1"])
        println(actions)
        bandit.update(actions, 3f)
    }

    // Force run system 1 and disable system 2
    run {
        val actions = bandit.chooseOrThrow(testModel["system 1"], !testModel["system 2"])
        println(actions)
        bandit.update(actions, 0f)
    }

    // Force test case D
    run {
        val actions = bandit.chooseOrThrow(testModel["test D"])
        println(actions)
        bandit.update(actions, -1.5f)
    }

    // Set a time constraint
    run {
        val actions = bandit.chooseOrThrow(testModel["timeBudget", 20])
        println(actions)
        bandit.update(actions, -0.5f)
    }

    // This assignment will not be possible
    run {
        try {
            bandit.chooseOrThrow(testModel["test D"], !testModel["system 2"])
        } catch (e: ValidationException) {
        }
    }
}