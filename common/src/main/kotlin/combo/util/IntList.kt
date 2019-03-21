package combo.util

import combo.math.IntPermutation
import kotlin.random.Random

class IntList private constructor(private var array: IntArray, size: Int) : MutableIntCollection {

    constructor(initialSize: Int = 4) : this(IntArray(initialSize), 0)
    constructor(array: IntArray) : this(array, array.size)

    override var size: Int = size
        private set

    override fun copy() = IntList(array.copyOf(), size)

    override fun clear() {
        size = 0
    }

    override operator fun contains(value: Int) = indexOf(value) >= 0

    operator fun get(index: Int) = array[index]

    fun indexOf(ix: Int): Int {
        for (i in 0 until size)
            if (array[i] == ix) return i
        return -1
    }

    override fun toArray() = array.copyOfRange(0, size)

    override fun map(transform: (Int) -> Int) =
            IntList(IntArray(size) { i ->
                transform(this.array[i])
            })

    override fun iterator() = object : IntIterator() {
        private var ptr = 0
        override fun hasNext() = ptr < size
        override fun nextInt(): Int {
            if (ptr >= size) throw NoSuchElementException()
            return array[ptr++]
        }
    }

    override fun permutation(rng: Random) = object : IntIterator() {
        private var ptr = 0
        private var perm = IntPermutation(size, rng)
        override fun hasNext() = ptr < size
        override fun nextInt(): Int {
            if (ptr >= size) throw NoSuchElementException()
            return array[perm.encode(ptr++)]
        }
    }

    override fun random(rng: Random) = array[rng.nextInt(size)]

    override fun add(value: Int): Boolean {
        if (array.size == size)
            array = array.copyOf(kotlin.math.max(array.size, 1) * 2)
        array[size++] = value
        return true
    }

    fun removeAt(ix: Int): Int {
        val v = array[ix]
        size--
        for (i in ix until size)
            array[i] = array[i + 1]
        array[size] = 0
        return v
    }

    override fun remove(value: Int): Boolean {
        val indexOf = indexOf(value)
        if (indexOf >= 0) {
            size--
            for (i in indexOf until size)
                array[i] = array[i + 1]
            array[size] = 0
            return true
        }
        return false
    }

    override fun toString() = "IntList($size)"
}