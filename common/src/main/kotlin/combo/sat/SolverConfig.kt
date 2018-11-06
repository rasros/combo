package combo.sat

import combo.math.RandomSequence
import combo.util.nanos

data class SolverConfig(
        val labelingBuilder: LabelingBuilder<*> = BitFieldLabelingBuilder(),
        val randomSeed: Long = nanos(),
        val maximize: Boolean = true,
        val debugMode: Boolean = false) {

    private val randomSequence = RandomSequence(randomSeed)

    fun nextRandom() = randomSequence.next()
}