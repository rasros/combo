package combo.util

inline fun DoubleArray.mapArray(transform: (Double) -> Double) =
        DoubleArray(this.size) {
            transform(this[it])
        }

inline fun IntArray.mapArray(transform: (Int) -> Int) =
        IntArray(this.size) {
            transform(this[it])
        }

inline fun DoubleArray.mapArrayIndexed(transform: (Int, Double) -> Double) =
        DoubleArray(this.size) {
            transform(it, this[it])
        }

inline fun DoubleArray.transformArrayIndexed(transform: (Int, Double) -> Double) {
    for (i in indices)
        this[i] = transform(i, this[i])
}

inline fun IntArray.transformArrayIndexed(transform: (Int, Int) -> Int) {
    for (i in indices)
        this[i] = transform(i, this[i])
}

inline fun DoubleArray.transformArray(transform: (Double) -> Double) {
    for (i in indices)
        this[i] = transform(this[i])
}

inline fun IntArray.transformArray(transform: (Int) -> Int) {
    for (i in indices)
        this[i] = transform(this[i])
}

fun IntArray.remove(ix: Int) = copyOfRange(0, ix) + copyOfRange(ix + 1, size)
val EMPTY_INT_ARRAY = IntArray(0)

