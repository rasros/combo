package combo.demo.models.autocomplete

import combo.model.Model
import combo.model.Value
import combo.sat.constraints.Relation

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
        impose { linear(0, Relation.LE, intArrayOf(-1, -2, -4) + ix, vars) }
        impose { linear(0, Relation.GE, intArrayOf(0, -2, -3) + ix, vars) }
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

            impose { linear(5, Relation.GE, adjustedGE, adjusted) }
            impose { height.value("S") reifiedImplies linear(7, Relation.LE, adjustedLE, adjusted) }
            impose { height.value("M") reifiedImplies linear(7, Relation.GT, adjustedGE, adjusted) }
            impose { height.value("M") reifiedImplies linear(15, Relation.LE, adjustedLE, adjusted) }
            impose { height.value("L") reifiedImplies linear(15, Relation.GT, adjustedGE, adjusted) }
            impose { linear(24, Relation.LE, adjustedLE, adjusted) }
        }

        run {
            // Set height for two column layout, the columns must be balanced
            val adjustedIm = arrayOf(*imHeight.values, !col2)
            val adjustedImLE = intArrayOf(*imWeights, -100)
            val adjustedImGE = intArrayOf(*imWeights, 100)

            impose { height.value("S") reifiedImplies linear(7, Relation.LE, adjustedImLE, adjustedIm) }
            impose { height.value("M") reifiedImplies linear(7, Relation.GT, adjustedImGE, adjustedIm) }
            impose { height.value("M") reifiedImplies linear(15, Relation.LE, adjustedImLE, adjustedIm) }
            impose { height.value("L") reifiedImplies linear(15, Relation.GT, adjustedImGE, adjustedIm) }

            val adjusted = arrayOf(*options, !col2)
            val adjustedLE = intArrayOf(*optionWeights, -100)
            val adjustedGE = intArrayOf(*optionWeights, 100)

            impose { height.value("S") reifiedImplies linear(7, Relation.LE, adjustedLE, adjusted) }
            impose { height.value("M") reifiedImplies linear(7, Relation.GT, adjustedGE, adjusted) }
            impose { height.value("M") reifiedImplies linear(15, Relation.LE, adjustedLE, adjusted) }
            impose { height.value("L") reifiedImplies linear(15, Relation.GT, adjustedGE, adjusted) }
        }
    }
}