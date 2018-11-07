package combo.sat

// Complete method: Can move most constraints out of walksat
//                  More useful for hillclimber
// Approximate method: Faster flipping

// Sorted list: Fast clear
// IndexSet:

fun MutableLabeling.flipChain(ix: Ix, implicationGraph: Array<Literals>,
                              flipped: IntArray, startIx: Ix = 0): Int {
    var i = 0
    var j = startIx
    val literal = !asLiteral(ix)
    while (i < implicationGraph[literal].size && j < flipped.size) {
        val l = implicationGraph[literal][i]
        if (asLiteral(l.asIx()) != l)
            flipped[j++] = asLiteral(l.asIx())
        i++
    }
    flip(ix)
    setAll(implicationGraph[literal])
    if (j < flipped.size) flipped[j] = -1
    else {
        do {
            j = flipChain(!flipped[j], implicationGraph, flipped, j)
        } while (j < flipped.size)
    }
    return j
}

fun MutableLabeling.flipChain(ix: Ix, flipped: IntArray) {
    flip(ix)
    for (i in 0 until flipped.size) {
        if (i < 0) break
        flip(flipped[i])
    }
}
