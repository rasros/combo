package combo.demo.models

import combo.bandit.BanditBuilder
import combo.bandit.ParallelMode
import combo.bandit.dt.DecisionTreeBandit
import combo.bandit.dt.RandomForestBandit
import combo.bandit.glm.LinearBandit
import combo.bandit.univariate.NormalPosterior
import combo.bandit.univariate.ThompsonSampling
import combo.demo.Simulation
import combo.demo.SurrogateModel
import combo.math.RunningVariance
import combo.math.dot
import combo.math.nextNormal
import combo.math.sample
import combo.model.*
import combo.sat.*
import combo.sat.constraints.Relation.*
import combo.sat.optimizers.JacopSolver
import combo.sat.optimizers.LinearObjective
import combo.sat.optimizers.Optimizer
import combo.util.IntCollection
import combo.util.IntHashSet
import combo.util.RandomSequence
import combo.util.nanos
import java.io.InputStreamReader
import kotlin.math.sqrt

fun main() {
    val acm = AutoCompleteSurrogate()

    fun BanditBuilder<*>.parallelBuild() = rewards(RunningVariance()).parallel().mode(ParallelMode.BLOCKING).build()
    val bandits = arrayOf(
            //{ "GA" to GeneticAlgorithmBandit.Builder(acm.model.problem, ThompsonSampling(NormalPosterior)).parallelBuild() },
            { "DT" to DecisionTreeBandit.Builder(acm.model, ThompsonSampling(NormalPosterior)).parallelBuild() },
            { "RF" to RandomForestBandit.Builder(acm.model, ThompsonSampling(NormalPosterior)).trees(200).parallelBuild() },
            { "GLMd" to LinearBandit.diagonalCovarianceBuilder(acm.model.problem).batchThreshold(Int.MAX_VALUE).parallelBuild() },
            { "GLMf" to LinearBandit.fullCovarianceBuilder(acm.model.problem).batchThreshold(Int.MAX_VALUE).parallelBuild() })

    for (bandit in bandits) {
        val rewards = RunningVariance()
        val n = 100
        var name: String? = null
        for (k in 0 until n) {
            val (bname, b) = bandit.invoke()
            name = bname
            val s = Simulation(acm, b, horizon = 100_000, log = false)
            s.start()
            s.awaitCompletion()
            rewards.accept((b.rewards as RunningVariance).mean)
        }
        println(name + " " + rewards.mean + "+/-" + (1.984f * rewards.standardDeviation / sqrt(n.toFloat())))
    }
}

fun autoCompleteModel(trainingSurrogate: Boolean) = Model.model("Auto complete") {
    // Context variables
    if (trainingSurrogate) {
        nominal("Gender", "Women", "Men", "Mixed", "Unisex")
    }
    val width = nominal("Width", "S", "M", "L")
    val height = nominal("Height", "S", "M", "L")
    val area = nominal("Area", "S", "M", "L")

    bool("Split bar")
    bool("Row separator")
    bool("Stripes")
    optionalNominal("Magnifier glass", "Right", "Left")
    bool("Reset")
    bool("All caps")
    if (trainingSurrogate) {
        bool("Relevant suggestions")
        bool("Diversified suggestions")
    }
    model("Highlight match") {
        bool("Inverse highlight")
        bool("Stylize highlight")
    }
    nominal("Match type", "Loose", "Term prefix", "Word prefix")
    bool("Term counts")

    val searchSuggestions = optionalNominal("Search suggestions", 3, 5, 10)
    model(searchSuggestions) {
        bool("Inline filter")
    }
    val categorySuggestions = optionalNominal("Category suggestions", 3, 5)
    val brandSuggestions = optionalNominal("Brand suggestions", 3)
    impose { searchSuggestions or categorySuggestions }

    val productCards = optionalNominal("Product cards", 3, 5)
    impose { atMost(3, searchSuggestions, brandSuggestions, categorySuggestions, productCards) }

    model(productCards) {
        bool("Horizontal cards")
        val placement = bool("Image left")
        bool("Cut outs")
        bool("Relevant cards")

        model("Two-column") {
            bool("Products left")
        }
        impose { productCards.value(5) implies "Two-column" }

        model("Text box") {
            bool("Horizontal text")
            bool("Product titles")
            bool("Category/brand info")
        }
        impose { placement implies "Text box" }
        impose { placement implies (!"Horizontal cards") }
        impose { !("Horizontal cards" and scope.parent["Horizontal text"]) }


        model(bool("Prices")) {
            bool("Sales")
        }
    }

    if (trainingSurrogate)
        nominal("Person", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P")

    if (trainingSurrogate) {
        // Interaction terms
        val interactionable = arrayOf(
                *width.values, *height.values,
                this["Two-column"], productCards, categorySuggestions, brandSuggestions, searchSuggestions,
                this["Text box"])
        val interactionTerms = ArrayList<Pair<Value, Value>>()
        for (i in 0 until interactionable.size) {
            val v1 = interactionable[i]
            for (j in (i + 1) until interactionable.size) {
                val v2 = interactionable[j]
                interactionTerms.add(v1 to v2)
            }
        }
        val interactionVar = bits("Interactions", interactionTerms.size)
        var inti = 0
        for ((v1, v2) in interactionTerms) {
            impose { interactionVar.value(inti++) reifiedEquivalent conjunction(v1, v2) }
        }
    }

    run {
        // Calculate width
        impose { width.value("S") equivalent !("Product cards" or "Split bar" or scope["Two-column"] or scope["Inline filter"] or scope["Term counts"]) }
        impose { width.value("L") equivalent ((scope["Horizontal cards"] or scope["Image left"]) and scope["Two-column"]) }
        // M otherwise
    }

    run {
        // Calculate area
        val ix = IntArray(3) { it } + IntArray(3) { it }
        val vars = arrayOf(*area.values, *width.values, *height.values)
        impose { linear(0, LE, intArrayOf(-1, -2, -4) + ix, vars) }
        impose { linear(0, GE, intArrayOf(0, -2, -3) + ix, vars) }
    }

    run {
        // Calculate height
        // The calculation is split based on whether there are two columns or not
        // The constraints are based on number of rows, where an image is 3 row
        // Each type of suggestions also have a header row, so that is where +2 comes from below

        // Height in rows is given by this:
        // 4 <= S <= 6
        // 7 <= M <= 14
        // 15<= L <= 22

        val options = arrayOf(
                *searchSuggestions.values,
                *categorySuggestions.values,
                *brandSuggestions.values)
        val optionWeights = options.map { it.value + 2 }.toIntArray()

        val col2 = this["Two-column"]
        val horizontal = this["Horizontal cards"]
        // Image height is used to because they can be arranged horizontally in which case they have fixed height
        val imHeight = optionalNominal("Image height", "S", "M", "L")
        impose { imHeight implies productCards }
        impose { horizontal implies imHeight.value("S") }
        impose { (!horizontal and productCards.value(3)) implies imHeight.value("M") }
        impose { (!horizontal and productCards.value(5)) implies imHeight.value("L") }
        val imWeights = intArrayOf(5, 11, 17)

        val allOptions = arrayOf(*options, *imHeight.values)
        val allWeights = optionWeights + imWeights

        run {
            // Set height for single column layout
            val adjusted = arrayOf(*allOptions, col2)
            val adjustedLE = intArrayOf(*allWeights, -100)
            val adjustedGE = intArrayOf(*allWeights, 100)

            impose { linear(5, GE, adjustedGE, adjusted) }
            impose { height.value("S") reifiedImplies linear(7, LE, adjustedLE, adjusted) }
            impose { height.value("M") reifiedImplies linear(7, GT, adjustedGE, adjusted) }
            impose { height.value("M") reifiedImplies linear(15, LE, adjustedLE, adjusted) }
            impose { height.value("L") reifiedImplies linear(15, GT, adjustedGE, adjusted) }
            impose { linear(24, LE, adjustedLE, adjusted) }
        }

        run {
            // Set height for two column layout, the columns must be balanced
            val adjustedIm = arrayOf(*imHeight.values, !col2)
            val adjustedImLE = intArrayOf(*imWeights, -100)
            val adjustedImGE = intArrayOf(*imWeights, 100)

            impose { height.value("S") reifiedImplies linear(7, LE, adjustedImLE, adjustedIm) }
            impose { height.value("M") reifiedImplies linear(7, GT, adjustedImGE, adjustedIm) }
            impose { height.value("M") reifiedImplies linear(15, LE, adjustedImLE, adjustedIm) }
            impose { height.value("L") reifiedImplies linear(15, GT, adjustedImGE, adjustedIm) }

            val adjusted = arrayOf(*options, !col2)
            val adjustedLE = intArrayOf(*optionWeights, -100)
            val adjustedGE = intArrayOf(*optionWeights, 100)

            impose { height.value("S") reifiedImplies linear(7, LE, adjustedLE, adjusted) }
            impose { height.value("M") reifiedImplies linear(7, GT, adjustedGE, adjusted) }
            impose { height.value("M") reifiedImplies linear(15, LE, adjustedLE, adjusted) }
            impose { height.value("L") reifiedImplies linear(15, GT, adjustedGE, adjusted) }
        }
    }
}

class AutoCompleteSurrogate(randomSeed: Int = nanos().toInt()) : SurrogateModel<LinearObjective> {

    private val randomSequence = RandomSequence(randomSeed)
    val dataSet = AutoCompleteDataSet()
    val model = autoCompleteModel(false)
    val solver = JacopSolver(dataSet.model.problem, randomSeed)
    val weights = readWeights()
    val stdErr = dataSet.sites.mapIndexed { i, assignment ->
        val d = assignment.instance dot weights
        (d - dataSet.scores[i])
    }.asSequence().sample(RunningVariance()).standardDeviation

    override fun optimal(optimizer: Optimizer<LinearObjective>, assumptions: IntCollection): Instance? {
        return optimizer.optimize(LinearObjective(true, weights), assumptions)
    }

    override fun reward(instance: Instance) = randomSequence.next().nextNormal(predict(instance), stdErr)

    /** Translate in the other direction compared to [predict] */
    fun remap(interactionInstance: Instance): Instance {
        val instance = BitArray(model.problem.nbrValues)
        for (i in 0 until dataSet.model.nbrVariables) {
            val variable = dataSet.model.index.variable(i)
            val realVariable = model.scope.find<Variable<*, *>>(variable.name) ?: continue
            val varIx = dataSet.model.index.valueIndexOf(variable)
            val realVarIx = model.index.valueIndexOf(realVariable)
            for (vi in 0 until variable.nbrValues) {
                if (interactionInstance[varIx + vi])
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
        val x = solver.witnessOrThrow(set).toFloatArray()

        // Clear blocking factors for optimization rewards
        for (name in arrayOf("Gender", "Person", "Relevant suggestions", "Diversified suggestions")) {
            val variable = dataSet.model[name]
            val ix = dataSet.model.index.valueIndexOf(variable)
            for (i in 0 until variable.nbrValues) {
                x[ix + i] = 0.0f
            }
        }
        return x dot weights
    }

    private fun readWeights() = InputStreamReader(javaClass.getResourceAsStream("ac_model_weights.csv"))
            .useLines { it.map { f -> f.toFloat() }.toList().toFloatArray() }
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

    fun printTrainingSet() {
        for (i in sites.indices)
            println("${scores[i]}," + sites[i].instance.toIntArray().joinToString(","))
    }

    private fun readSiteAttributes(m: Model): Map<String, Array<Literal>> {
        val lines = InputStreamReader(javaClass.getResourceAsStream("ac_attributes.csv")).readLines()
        val h = lines[0].split(",")
        return lines.subList(1, lines.size).associate { line ->
            val values = line.split(",")
            val lits = ArrayList<Literal>()
            for (i in 2 until h.size) {
                if (model.scope.find<Variable<*, *>>(h[i]) == null) continue
                if (values[i].isEmpty() || values[i] == "No") lits.add(!m[h[i]])
                else if (values[i] == "Yes") lits.add(m[h[i]])
                else {
                    val v: Any = if (values[i].matches(Regex("[0-9]+"))) values[i].toInt()
                    else values[i]
                    lits.add(m[h[i], v])
                }
            }
            values[1] to lits.toTypedArray()
        }
    }

    private data class Example(val id: String, val score: Int, val person: String)

    private fun readScores(): List<Example> {
        val lines = InputStreamReader(javaClass.getResourceAsStream("ac_scores.csv")).readLines()
        return lines.subList(1, lines.size).map { line ->
            val values = line.split(",")
            val id = values[0]
            val score = values[2]
            val person = values[3]
            Example(id, score.toInt(), person)
        }
    }
}

/*
val rand1Lits = lits + arrayOf(model["All caps"], model["Highlight match"], !model["Inverse highlight"], !model["Inline filter"], model["Reset"], !model["Split bar"], !model["Stripes"], !model["Row separator"], model["Magnifier glass", "Right"],
        model["Match type", "Loose"], model["Category suggestions", 3], model["Brand suggestions", 3], model["Search suggestions", 3], !model["Product cards"], model["Stylize highlight"], !model["Term counts"])
val rand2Lits = lits + arrayOf(!model["All caps"], !model["Highlight match"], model["Inline filter"], !model["Reset"], model["Relevant cards"], !model["Split bar"], model["Stripes"], !model["Row separator"], model["Magnifier glass", "Right"],
        model["Match type", "Loose"], model["Category suggestions", 3], !model["Brand suggestions"], model["Search suggestions", 10], model["Products left"], model["Two-column"], model["Cut outs"], !model["Prices"], !model["Text box"], model["Product cards", 5], !model["Term counts"])
val rand1Assumptions = collectionOf(*rand1Lits.map { (it as Value).toLiteral(model.index) }.toIntArray())
val rand2Assumptions = collectionOf(*rand2Lits.map { (it as Value).toLiteral(model.index) }.toIntArray())
 */
