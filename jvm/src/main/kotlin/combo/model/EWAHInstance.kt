package combo.model

import com.googlecode.javaewah.EWAHCompressedBitmap
import combo.sat.Instance
import combo.sat.InstanceFactory
import combo.sat.MutableInstance
import combo.sat.deepEquals

/**
 * This uses a library for compressed bitmaps. It is very space efficient but quite slow. This requires
 * an optional dependency to EWAH like so in gradle: compile "com.googlecode.javaewah:JavaEWAH:1.1.6"
 */
object EWAHInstanceFactory : InstanceFactory {
    override fun create(size: Int) = EWAHInstance(size)
}

class EWAHInstance @JvmOverloads constructor(
        override val size: Int,
        val bitField: EWAHCompressedBitmap = EWAHCompressedBitmap()) : MutableInstance {

    override fun get(ix: Int) = bitField.get(ix)

    override fun copy() = EWAHInstance(size, bitField.clone())

    override fun set(ix: Int, value: Boolean) {
        if (value) bitField.set(ix) else bitField.clear(ix)
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            val itr = bitField.intIterator()
            override fun hasNext() = itr.hasNext()
            override fun nextInt() = itr.next() shl 1
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Instance || other.size != size) return false
        return if (other is EWAHInstance) bitField == other.bitField
        else deepEquals(other)
    }

    override fun hashCode() = size * 31 + bitField.hashCode()
    override val sparse: Boolean get() = true
}
