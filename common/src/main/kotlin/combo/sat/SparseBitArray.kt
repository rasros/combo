package combo.sat

import combo.util.IntIntHashMap
import combo.util.assert
import combo.util.key
import combo.util.value

object SparseBitArrayFactory : InstanceFactory {
    override fun create(size: Int) = SparseBitArray(size)
}

class SparseBitArray(override val size: Int, val map: IntIntHashMap = IntIntHashMap(1, -1)) : MutableInstance {

    override operator fun get(ix: Int) = (map[ix shr 5] ushr ix and 0x1F) and 1 == 1

    override fun copy() = SparseBitArray(size, map.copy())

    override fun set(ix: Int, value: Boolean) {
        val i = ix shr 5
        val rem = ix and 0x1F
        setOrRemove(i, if (value) map[i] or (1 shl rem)
        else map[i] and (1 shl rem).inv())
    }

    private fun setOrRemove(i: Int, value: Int) {
        if (value == 0) map.remove(i)
        else map[i] = value
    }

    override fun getBits(ix: Int, nbrBits: Int): Int {
        assert(nbrBits in 1..32 && ix + nbrBits <= size)
        val i1 = ix shr 5
        val i2 = (ix + nbrBits - 1) shr 5
        val rem = ix and 0x1F
        return if (i1 != i2) {
            val v1 = (map[i1] ushr Int.SIZE_BITS + rem)
            val v2 = map[i2] shl Int.SIZE_BITS - rem
            val mask = -1 ushr Int.SIZE_BITS - nbrBits
            (v1 or v2) and mask
        } else {
            val value = map[i1] ushr rem
            val mask = -1 ushr Int.SIZE_BITS - nbrBits
            value and mask
        }
    }

    override fun setBits(ix: Int, nbrBits: Int, value: Int) {
        assert(nbrBits >= 1 && nbrBits <= 32 && ix + nbrBits <= size)
        assert(nbrBits == 32 || value and (-1 shl nbrBits) == 0)
        val i1 = ix shr 5
        val i2 = (ix + nbrBits - 1) shr 5
        val rem = ix and 0x1F
        if (i1 != i2) {
            val mask = -1 shl rem

            var v1 = map[i1] and mask.inv()
            v1 = v1 or (value shl rem and mask)
            setOrRemove(i1, v1)

            var v2 = map[i2] and mask
            v2 = v2 or ((value ushr (32 - rem)) and mask.inv())
            setOrRemove(i2, v2)
        } else {
            val mask1 = (-1 ushr Int.SIZE_BITS - nbrBits - rem).inv()
            val mask2 = (-1 shl rem).inv()
            var v = map[i1] and (mask1 or mask2) // zero out old value
            v = v or (value shl rem) // set value
            setOrRemove(i1, v)
        }
    }

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            var base = map.iterator()
            var currentKey: Int = 0
            var currentValue: Int = 0
            var i = 0
            override fun hasNext() = base.hasNext() || currentValue != 0
            override fun nextInt(): Int {
                if (currentValue == 0) {
                    val l = base.nextLong()
                    currentKey = l.key()
                    currentValue = l.value()
                    i = 0
                }
                while (currentValue and 1 == 0) {
                    i++
                    currentValue = currentValue ushr 1
                }
                currentValue = currentValue ushr 1
                if (i >= 32) throw NoSuchElementException()
                return (currentKey shl Int.SIZE_BYTES + 1) + i++
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return if (other is SparseBitArray) {
            if (size != other.size) false
            else {
                val itr1 = map.iterator()
                val itr2 = other.map.iterator()
                while (itr1.hasNext() && itr2.hasNext())
                    if (itr1.nextLong() != itr2.nextLong()) return false
                if (itr1.hasNext() || itr2.hasNext()) return false
                return true
            }
        } else if (other is Instance) deepEquals(other)
        else false
    }

    override fun hashCode(): Int {
        var result = size
        val itr = map.iterator()
        while (itr.hasNext()) {
            val l = itr.nextLong()
            result = 31 * result + l.key()
            result = 31 * result + l.value()
        }
        return result
    }

    override fun toString() = deepToString()
    override val sparse: Boolean get() = true
}