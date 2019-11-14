package combo.demo.models

import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.model.Flag
import combo.model.Model.Companion.model
import combo.model.ModelBandit
import combo.sat.ValidationException
import combo.sat.constraints.Relation

val testModel = model {

    val system1 = model("system 1") {
        flag("test A", 10)
        flag("test B", 20)
        flag("test C", 15)

        // This constraint makes it so that if we select "system 1" one of the sub-variables (A,B,C) are enabled.
        // Otherwise, only the opposite will be true (A=>system 1 and so on).
        impose { "system 1" implies disjunction(*scope.variables.toTypedArray()) }
    }

    val system2 = model("system 2") {
        flag("test D", 5)
        flag("test E", 4)
        impose { "system 2" implies disjunction(*scope.variables.toTypedArray()) }
    }

    // At least one of the tests must be run each step
    impose { disjunction(system1, system2) }

    // Add a time budget variable that can be dynamically specified
    val testVariables: Array<Flag<Int>> = (system1.scope.variables + system2.scope.variables).map { it as Flag<Int> }.toTypedArray()
    val testDurations = testVariables.map { it.value }.toIntArray()
    val timeBudget = int("timeBudget", 0, testDurations.sum())
    impose { linear(timeBudget, Relation.LE, testDurations, testVariables) }
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