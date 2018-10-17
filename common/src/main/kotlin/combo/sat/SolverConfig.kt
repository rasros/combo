package combo.sat

import combo.math.RngSequence
import combo.util.nanos

data class SolverConfig(
        val labelingBuilder: LabelingBuilder<*> = BitFieldLabelingBuilder(),
        val randomSeed: Long = nanos(),
        val maximize: Boolean = true,
        val debugMode: Boolean = false) {

    private val rngSequence = RngSequence(randomSeed)

    fun nextRng() = rngSequence.next()
}