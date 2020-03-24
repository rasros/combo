package combo.demo.models.autocomplete

import combo.bandit.glm.*
import combo.math.RunningVariance
import combo.math.sample
import combo.model.*
import combo.sat.optimizers.JacopSolver
import java.io.InputStreamReader

fun main() {
    val a = AutoCompleteDataSet()

    val lm = SGDLinearModel.Builder(a.model.problem)
            .regularizationFactor(1e-2f)
            .loss(HuberLoss(0.01f))
            .updater(SGD(ExponentialDecay(0.001f, 1e-5f)))
            .build()
    repeat(1000) {
        for (i in a.sites.indices)
            lm.train(EffectCodedVector(a.model, a.sites[i].instance), a.scores[i], 1f)
    }
    lm.weights.toFloatArray().forEach { println(it) }
}

class AutoCompleteDataSet {
    val model = autoCompleteModel(true)
    val sites: Array<Assignment>
    val scores: FloatArray

    init {
        val sitesLiterals = readSiteAttributes(model)
        val solver = ModelOptimizer(model, JacopSolver(model.problem))
        val siteScores = readScores()
        scores = FloatArray(siteScores.size)
        val ve = siteScores.asSequence().map { it.score.toFloat() }.sample(RunningVariance())
        sites = Array(siteScores.size) {
            val e = siteScores[it]
            val assumptions = (sitesLiterals[e.id] ?: error("Not found ${e.id}")) + model["Person", e.person]
            scores[it] = (e.score - ve.mean) / ve.variance
            solver.witnessOrThrow(*assumptions)
        }
    }

    fun printHeader() {
        var result = "Score,"
        for (i in 0 until model.nbrVariables) {
            val variable = model.index.variable(i)
            if (variable.nbrValues == 1) result += variable.name + ","
            else {
                val offset = if (variable.optional) {
                    result += variable.name + ","
                    1
                } else 0
                for (j in offset until variable.nbrValues) {
                    if (variable is Select) result += variable.name + "_" + variable.values[j - offset].value
                    else result += variable.name + "_" + (j - offset)
                    result += ","
                }
            }
        }
        println(result.slice(0 until result.length - 1))
    }

    fun printTrainingSet(effectCoding: Boolean = false) {
        for (i in sites.indices)
            if (effectCoding)
                println("${scores[i]}," + EffectCodedVector(model, sites[i].instance).toFloatArray().joinToString(","))
            else
                println("${scores[i]}," + sites[i].instance.toIntArray().joinToString(","))
    }

    private fun readSiteAttributes(m: Model): Map<String, Array<Literal>> {
        val lines = InputStreamReader(javaClass.getResourceAsStream("/combo/demo/models/ac_attributes.csv")).readLines()
        val h = lines[0].split(",")
        return lines.subList(1, lines.size).associate { line ->
            val values = line.split(",")
            val lits = ArrayList<Literal>()
            for (i in 2 until h.size) {
                if (model.scope.find<Variable<*, *>>(h[i]) == null) continue
                val value = values[i].trim()
                if (value.isEmpty() || value == "No") lits.add(!m[h[i]])
                else if (value.trim() == "Yes") lits.add(m[h[i]])
                else {
                    val v: Any = if (value.matches(Regex("[0-9]+"))) value.toInt()
                    else value
                    lits.add(m[h[i], v])
                }
            }
            values[1] to lits.toTypedArray()
        }
    }

    private data class Example(val id: String, val score: Int, val person: String)

    private fun readScores(): List<Example> {
        val lines = InputStreamReader(javaClass.getResourceAsStream("/combo/demo/models/ac_scores.csv")).readLines()
        return lines.subList(1, lines.size).map { line ->
            val values = line.split(",")
            val id = values[0].trim()
            val score = values[2].trim()
            val person = values[3].trim()
            Example(id, score.toInt(), person)
        }
    }
}