package combo.model

import combo.sat.Labeling

// TODO tree structure, implementing Tree
class Assignment internal constructor(val labeling: Labeling, val featureMeta: Map<Feature<*>, FeatureMeta<*>>) : Iterable<Assignment.FeatureAssignment<*>> {

    override fun iterator(): Iterator<FeatureAssignment<*>> = object : Iterator<FeatureAssignment<*>> {
        val itr: Iterator<Map.Entry<Feature<*>, FeatureMeta<*>>> = featureMeta.iterator()
        override fun hasNext(): Boolean = itr.hasNext()
        override fun next(): FeatureAssignment<*> {
            val f: Feature<*> = itr.next().key
            @Suppress("UNCHECKED_CAST")
            return FeatureAssignment(f as Feature<Any>, get(f))
        }
    }

    val map: Map<Feature<*>, Any?> by lazy {
        LinkedHashMap<Feature<*>, Any?>().apply {
            for ((f, v) in this@Assignment) this[f] = v
        }
    }

    operator fun get(feature: Flag<Boolean>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val meta: FeatureMeta<Boolean> = (featureMeta[feature] as? FeatureMeta<Boolean>) ?: return false
        return meta.indexEntry.valueOf(labeling) ?: false
    }

    operator fun <V> get(feature: Feature<V>): V? {
        @Suppress("UNCHECKED_CAST")
        val meta: FeatureMeta<V> = (featureMeta[feature] as? FeatureMeta<V>) ?: return null
        return meta.indexEntry.valueOf(labeling)
    }

    fun <V> getOrThrow(feature: Feature<V>): V = get(feature)
            ?: throw NoSuchElementException("Feature $feature not found.")

    fun <V> getOrDefault(feature: Feature<V>, default: V): V = get(feature)
            ?: default

    fun containsKey(feature: Feature<*>): Boolean = featureMeta.containsKey(feature)
    fun containsValue(value: Any): Boolean {
        for (a in this) if (a.value == value) return true
        return false
    }

    val size get() = featureMeta.size
    val keys get() = map.keys
    val values get() = map.values
    val entries get() = map.entries

    data class FeatureAssignment<V>(val feature: Feature<V>, val value: V?)
}


