package combo.model

import combo.sat.Labeling
import combo.sat.Problem
import combo.sat.Tautology
import combo.sat.asLiteral
import combo.util.IndexSet
import combo.util.Tree
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

class Model private constructor(val featureMetas: Map<Feature<*>, FeatureMeta<*>>,
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

    class Builder private constructor(
            override val value: Feature<*>,
            override val children: MutableList<Builder>,
            private val declarations: MutableSet<Feature<*>>? = null,
            private val sentences: MutableList<SentenceBuilder>? = null) : Tree<Feature<*>, Builder> {

        @JvmOverloads
        constructor(feature: Feature<*> = flag("root")) : this(feature, ArrayList(), HashSet(), ArrayList()) {
            addDeclaration(feature)
        }

        fun build(): Model {
            val root = makeTree()
            val features = this.asSequence().map { it.value }.toList()

            val index = ReferenceIndex(features.toTypedArray())
            val fullSentences = ((sentences ?: emptyList<SentenceBuilder>())
                    .asSequence().flatMap { it.toSentences(index).asSequence() })
                    .plus(features.asSequence().flatMap { it.toSentences(index).asSequence() })
                    .filterNot { it is Tautology }
                    .toList().toTypedArray()

            val unitLiterals = IndexSet()
            val rootFeature = root.value
            unitLiterals.add(rootFeature.toLiteral(index.indexOf(rootFeature)))
            val problem = Problem(fullSentences, index.nbrVariables, Problem.Tree(-1)).unitPropagation(unitLiterals)

            val remappedIds = IntArray(problem.nbrVariables)

            val nbrVariables = let {
                var variableId = 0
                for (i in remappedIds.indices)
                    when {
                        unitLiterals.contains(i.asLiteral(true)) -> remappedIds[i] = UNIT_TRUE
                        unitLiterals.contains(i.asLiteral(false)) -> remappedIds[i] = UNIT_FALSE
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

            val remappedSentences = problem.sentences.asSequence().map { it.remap(remappedIds) }.toList().toTypedArray()
            val featureMetaMap: Map<Feature<*>, FeatureMeta<*>> = LinkedHashMap<Feature<*>, FeatureMeta<*>>().apply {
                for (f in featureMetas)
                    this[f.feature] = f
            }

            fun FeatureTree.buildTree(treeTarget: Problem.Tree, children: MutableList<Problem.Tree>) {
                val ixe = featureMetaMap[this.value]!!.indexEntry
                val ids = ixe.indices
                val newChildren: MutableList<Problem.Tree>
                val newParent: Problem.Tree
                if (ixe.isRootUnit()) {
                    newChildren = ArrayList()
                    newParent = Problem.Tree(ids[0], newChildren)
                    children.add(newParent)
                } else {
                    newChildren = children
                    newParent = treeTarget
                }
                for (i in 1 until ids.size) {
                    if (ids[i] >= 0)
                        newChildren.add(Problem.Tree(ids[i]))
                }
                this.children.forEach {
                    it.buildTree(newParent, newChildren)
                }
            }

            val problemTree = let {
                val list = ArrayList<Problem.Tree>()
                Problem.Tree(-1, list).also { pt -> root.buildTree(pt, list) }
            }

            return Model(featureMetaMap,
                    Problem(remappedSentences, nbrVariables, problemTree), root)
        }

        fun constrained(by: SentenceBuilder): Builder {
            for (ref in by.references) if (!declarations!!.contains(ref.rootFeature))
                throw ValidationException("Use of undeclared reference in operator: " + by.toString())
            sentences!!.add(by)
            return this
        }

        fun optional(childFeature: Feature<*>) = optional(Builder(childFeature))
        fun optional(childBuilder: Builder): Builder {
            childBuilder.declarations?.forEach { this.addDeclaration(it) }
            childBuilder.sentences?.forEach { this.constrained(it) }
            this.children.add(Builder(childBuilder.value, childBuilder.children, this.declarations, this.sentences))
            constrained(childBuilder.value implies value)
            return this
        }

        fun mandatory(childFeature: Feature<*>) = mandatory(Builder(childFeature))
        fun mandatory(childBuilder: Builder): Builder {
            childBuilder.declarations?.forEach { this.addDeclaration(it) }
            childBuilder.sentences?.forEach { this.constrained(it) }
            this.children.add(Builder(childBuilder.value, childBuilder.children, this.declarations, this.sentences))
            constrained(childBuilder.value equivalent value)
            return this
        }

        private fun makeTree(): FeatureTree = FeatureTree(value, children.asSequence().map { it.makeTree() }.toList())
        private fun addDeclaration(feature: Feature<*>) = if (!declarations!!.add(feature)) throw ValidationException("Duplicated feature.") else Unit
    }
}
