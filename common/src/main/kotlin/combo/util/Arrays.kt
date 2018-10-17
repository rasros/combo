package combo.util

inline fun DoubleArray.applyTransform(transform: (Double) -> Double): DoubleArray {
    for (i in indices)
        this[i] = transform(this@applyTransform[i])
    return this
}

inline fun IntArray.applyTransform(transform: (Int) -> Int): IntArray {
    for (i in indices)
        this[i] = transform(this@applyTransform[i])
    return this
}

fun IntArray.remove(ix: Int) = copyOfRange(0, ix - 1) + copyOfRange(ix, size)
