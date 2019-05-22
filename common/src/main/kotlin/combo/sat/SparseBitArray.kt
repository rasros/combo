package combo.sat

import combo.util.IntHashMap
import combo.util.key
import combo.util.value

object SparseBitArrayBuilder : InstanceBuilder {
    override fun create(size: Int) = SparseBitArray(size)
}

class SparseBitArray(override val size: Int, val map: IntHashMap = IntHashMap(1, -1)) : MutableInstance {

    override operator fun get(ix: Int) = (map[ix shr 5] ushr ix and 0x1F) and 1 == 1

    override fun copy() = SparseBitArray(size, map.copy())

    override fun set(ix: Int, value: Boolean) {
        val i = ix shr 5
        val rem = ix and 0x1F
        setWord(i, if (value) map[i] or (1 shl rem)
        else map[i] and (1 shl rem).inv())
    }

    override fun getWord(wordIx: Int) = map[wordIx]

    override fun setWord(wordIx: Int, value: Int) {
        if (value == 0) map.remove(wordIx)
        else map[wordIx] = value
    }

    override fun clear() = map.clear()

    override fun iterator(): IntIterator {
        return object : IntIterator() {
            var base = map.entryIterator()
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

    override fun wordIterator() = map.entryIterator()

    override fun equals(other: Any?): Boolean {
        return if (other is SparseBitArray) {
            if (size != other.size) false
            else {
                val itr1 = map.entryIterator()
                val itr2 = other.map.entryIterator()
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
        val itr = map.entryIterator()
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