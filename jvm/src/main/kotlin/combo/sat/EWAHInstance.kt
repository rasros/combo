package combo.sat

import com.googlecode.javaewah.EWAHCompressedBitmap
import combo.util.transformArray

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

    override fun get(ix: Ix) = bitField.get(ix)

    override fun copy() = EWAHInstance(size, bitField.clone())

    override fun set(ix: Ix, value: Boolean) {
        if (value) bitField.set(ix) else bitField.clear(ix)
    }

    override fun truthIterator(): IntIterator {
        return object : IntIterator() {
            val itr = bitField.intIterator()
            override fun hasNext() = itr.hasNext()
            override fun nextInt() = itr.next() shl 1
        }
    }

    override fun toLiterals(trueValuesOnly: Boolean): IntArray {
        return if (trueValuesOnly) bitField.toArray().apply { transformArray { it shl 1 } }
        else super.toLiterals(trueValuesOnly)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Instance || other.size != size) return false
        return if (other is EWAHInstance) bitField == other.bitField
        else deepEquals(other)
    }

    override fun hashCode() = size * 31 + bitField.hashCode()
}