package combo.model

import combo.sat.Labeling
import combo.sat.Problem
import combo.sat.Tautology
import combo.sat.toLiteral
import combo.util.IntSet
import combo.util.Tree
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

class Model private constructor(internal val featureMetas: Map<Feature<*>, FeatureMeta<*>>,
                                val problem: Problem, val root: FeatureTree) {

    val features = featureMetas.keys.toList().toTypedArray()

    companion object {
        @JvmStatic
        @JvmOverloads
        fun builder(feature: Feature<*> = flag("${"$"}root")) = Model.Builder(feature)

        @JvmStatic
        fun builder(name: String) = builder(flag(name))
    }

    fun toAssignment(labeling: Labeling) = Assignment(labeling, featureMetas)

    fun variableIndex(): Array<Feature<*>> {
        val list = ArrayList<Feature<*>>()
        for (f in featureMetas)
            for (j in f.value.indexEntry.indices) {
                if (j >= 0) list.add(f.key)
            }
        require(list.size == problem.nbrVariables)
        return list.toTypedArray()
    }

    class Builder private constructor(
            override val value: Feature<*>,
            override val children: MutableList<Builder>,
            private val declarations: MutableSet<Feature<*>>? = null,
            private val constraints: MutableList<ConstraintBuilder>? = null) : Tree<Feature<*>, Builder> {
        // TODO optimizations in building procedure
        // TODO ideas: don't add disjunctions to root

        @JvmOverloads
        constructor(feature: Feature<*> = flag("root")) : this(feature, ArrayList(), HashSet(), ArrayList()) {
            addDeclaration(feature)
        }

        fun build(): Model {
            val root = makeTree()
            val features = this.asSequence().map { it.value }.toList()

            val index = ReferenceIndex(features.toTypedArray())
            val fullSentences = ((constraints ?: emptyList<ConstraintBuilder>())
                    .asSequence().flatMap { it.toConstraints(index).asSequence() })
                    .plus(features.asSequence().flatMap { it.toConstraints(index).asSequence() })
                    .filterNot { it is Tautology }
                    .toList().toTypedArray()

            val unitLiterals = IntSet()
            val rootFeature = root.value
            unitLiterals.add(rootFeature.toLiteral(index.indexOf(rootFeature)))
            val problem = Problem(fullSentences, index.nbrVariables).let {
                val reducedSentences = it.unitPropagation(unitLiterals)
                Problem(reducedSentences, it.nbrVariables)
            }

            val remappedIds = IntArray(problem.nbrVariables)

            val nbrVariables = let {
                var variableId = 0
                for (i in remappedIds.indices)
                    when {
                        unitLiterals.contains(i.toLiteral(true)) -> remappedIds[i] = UNIT_TRUE
                        unitLiterals.contains(i.toLiteral(false)) -> remappedIds[i] = UNIT_FALSE
                        else -> remappedIds[i] = variableId++
                    }
                variableId
            }


            val featureMetas: Array<FeatureMeta<*>> = Array(features.size) {
                @Suppress("UNCHECKED_CAST")
                val feature: Feature<Any?> = features[it] as Feature<Any?>
                val originalId = index.indexOf(feature)
                val indexEntry: IndexEntry<Any?> = feature.createIndexEntry(
                        remappedIds.sliceArray(originalId until (originalId + feature.nbrLiterals)))
                FeatureMeta(feature, indexEntry)
            }

            val remappedSentences = problem.constraints.asSequence().map { it.remap(remappedIds) }.toList().toTypedArray()
            val featureMetaMap: Map<Feature<*>, FeatureMeta<*>> = LinkedHashMap<Feature<*>, FeatureMeta<*>>().apply {
                for (f in featureMetas)
                    this[f.feature] = f
            }

            return Model(featureMetaMap,
                    Problem(remappedSentences, nbrVariables), root)
        }

        fun constrained(by: ConstraintBuilder): Builder {
            for (ref in by.variables) if (!declarations!!.contains(ref.rootFeature))
                throw IllegalArgumentException("Use of undeclared reference $ref in constraint: $by")
            constraints!!.add(by)
            return this
        }

        fun optional(childFeature: Feature<*>) = optional(Builder(childFeature))
        fun optional(childBuilder: Builder): Builder {
            childBuilder.declarations?.forEach { this.addDeclaration(it) }
            childBuilder.constraints?.forEach { this.constrained(it) }
            this.children.add(Builder(childBuilder.value, childBuilder.children, this.declarations, this.constraints))
            constrained(childBuilder.value implies value)
            return this
        }

        fun mandatory(childFeature: Feature<*>) = mandatory(Builder(childFeature))
        fun mandatory(childBuilder: Builder): Builder {
            childBuilder.declarations?.forEach { this.addDeclaration(it) }
            childBuilder.constraints?.forEach { this.constrained(it) }
            this.children.add(Builder(childBuilder.value, childBuilder.children, this.declarations, this.constraints))
            constrained(childBuilder.value equivalent value)
            return this
        }

        private fun makeTree(): FeatureTree = FeatureTree(value, children.asSequence().map { it.makeTree() }.toList())
        private fun addDeclaration(feature: Feature<*>) = if (!declarations!!.add(feature)) throw IllegalArgumentException("Duplicated feature.") else Unit
    }
}
