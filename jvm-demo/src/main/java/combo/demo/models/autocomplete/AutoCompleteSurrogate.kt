package combo.demo.models.autocomplete

import combo.demo.SurrogateModel
import combo.math.RunningVariance
import combo.math.nextNormal
import combo.math.sample
import combo.math.vectors
import combo.model.Literal
import combo.model.Root
import combo.model.Select
import combo.model.Variable
import combo.sat.BitArray
import combo.sat.Instance
import combo.sat.optimizers.JacopSolver
import combo.sat.optimizers.ObjectiveFunction
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.IntHashSet
import java.io.InputStreamReader
import kotlin.random.Random

class AutoCompleteSurrogate(val removeInteractions: Boolean = false) : SurrogateModel<ObjectiveFunction> {

    val dataSet = AutoCompleteDataSet()
    override val model = autoCompleteModel(false)
    val solver = JacopSolver(dataSet.model.problem)
    val weights = vectors.vector(readWeights())

    val stdErr = dataSet.sites.mapIndexed { i, assignment ->
        val p = predict(remap(assignment.instance))
        (p - dataSet.scores[i])
    }.asSequence().sample(RunningVariance()).standardDeviation

    override fun optimal(optimizer: Optimizer<ObjectiveFunction>, assumptions: IntCollection): Instance? {
        val autoCompleteObjective = object : ObjectiveFunction {
            override fun value(instance: Instance) = model.problem.satisfies(instance).let {
                if (!it) model.problem.violations(instance).toFloat()
                else -predict(instance)
            }
        }
        return optimizer.optimize(autoCompleteObjective, assumptions)
    }

    override fun reward(instance: Instance, prediction: Float, rng: Random) = rng.nextNormal(prediction, stdErr)

    /** Translate in the other direction compared to [predict] */
    fun remap(interactionInstance: Instance): Instance {
        val instance = BitArray(model.problem.nbrValues)
        for (i in 0 until dataSet.model.nbrVariables) {
            val variable = dataSet.model.index.variable(i)
            val realVariable = model.scope.find<Variable<*, *>>(variable.name) ?: continue
            val varIx = dataSet.model.index.valueIndexOf(variable)
            val realVarIx = model.index.valueIndexOf(realVariable)
            for (vi in 0 until variable.nbrValues) {
                if (interactionInstance.isSet(varIx + vi))
                    instance[realVarIx + vi] = true
            }
        }
        return instance
    }

    override fun predict(instance: Instance): Float {
        // Need to translate between the surrogate model and the real model.
        // The surrogate model has additional variables compared to the real model, the interaction terms are kept
        // and the other terms are deleted.
        val assignment = model.toAssignment(instance)
        val literals = ArrayList<Literal>()
        for (i in 0 until model.nbrVariables) {
            val variable = model.index.variable(i)
            val surrogateVariable = dataSet.model.scope[variable.name]

            if (variable.optional || variable.parent !is Root) {
                if (variable.name in assignment) literals.add(surrogateVariable)
                else literals.add(surrogateVariable.not())
            }

            if (variable is Select) {
                if ((!variable.optional && variable.parent is Root) || variable.name in assignment) {
                    for (v in variable.values) {
                        @Suppress("UNCHECKED_CAST")
                        if (v in assignment)
                            literals.add((surrogateVariable as Select<Any, Any>).value(v.value as Any))
                    }
                }
            }
        }

        val set = IntHashSet()
        literals.forEach { it.collectLiterals(dataSet.model.index, set) }
        val x = solver.witnessOrThrow(set)

        // Clear blocking factors for optimization rewards
        val cleared = mutableListOf("Gender", "Person", "Relevant suggestions", "Diversified suggestions")
        if (removeInteractions) cleared.add("Interactions")
        for (name in cleared) {
            val variable = dataSet.model[name]
            val ix = dataSet.model.index.valueIndexOf(variable)
            for (i in 0 until variable.nbrValues) {
                x[ix + i] = false
            }
        }
        return x dot weights
    }

    private fun readWeights() = InputStreamReader(javaClass.getResourceAsStream("/combo/demo/models/ac_model_weights.csv"))
            .useLines { it.map { f -> f.toFloat() }.toList().toFloatArray() }
}