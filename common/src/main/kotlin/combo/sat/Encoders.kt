package combo.sat

abstract class VariableEncoder(val binaryIx: Int, val vectorIx: Int) {
    abstract fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean)
    abstract val binarySize: Int
    abstract val vectorSize: Int
}

class BooleanEncoder(binaryIx: Int, vectorIx: Int) : VariableEncoder(binaryIx, vectorIx) {
    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        if (instance[binaryIx]) vector[vectorIx] = 1.0f
        else if (!oneHot) vector[vectorIx] = -1.0f
    }

    override val binarySize: Int get() = 1
    override val vectorSize: Int get() = 1
}

class BitsEncoder(binaryIx: Int, vectorIx: Int, override val binarySize: Int) : VariableEncoder(binaryIx, vectorIx) {
    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        for (i in 0 until binarySize) {
            if (instance[binaryIx + i]) vector[vectorIx + i] = 1.0f
            else if (!oneHot) vector[vectorIx + i] = -1.0f
        }
    }

    override val vectorSize: Int get() = binarySize
}

class CountEncoder(binaryIx: Int, vectorIx: Int, override val binarySize: Int, val min: Int, val max: Int) : VariableEncoder(binaryIx, vectorIx) {

    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        val v = instance.getBits(binaryIx, binarySize)
        vector[vectorIx] = if (normalize) 2 * (v - min) / (max - min).toFloat() - 1.0f
        else v.toFloat()
    }

    override val vectorSize: Int get() = 1
}

class IntEncoder(binaryIx: Int, vectorIx: Int, override val binarySize: Int, val min: Int, val max: Int) : VariableEncoder(binaryIx, vectorIx) {

    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        val v = instance.getSignedInt(binaryIx, binarySize)
        vector[vectorIx] = if (normalize) 2 * (v - min) / (max - min).toFloat() - 1.0f
        else v.toFloat()
    }

    override val vectorSize: Int get() = 1
}

class FloatEncoder(binaryIx: Int, vectorIx: Int, val min: Float, val max: Float) : VariableEncoder(binaryIx, vectorIx) {

    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        val v = instance.getSignedInt(binaryIx, binarySize)
        vector[vectorIx] = if (normalize) 2 * (v - min) / (max - min) - 1.0f
        else v.toFloat()
    }

    override val vectorSize: Int get() = 1
    override val binarySize: Int get() = 32
}

class OrdinalEncoder(binaryIx: Int, vectorIx: Int, override val binarySize: Int) : VariableEncoder(binaryIx, vectorIx) {
    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        val bp = instance.getLast(binaryIx, binaryIx + binarySize)
        if (!oneHot) {
            for (i in bp until binarySize)
                vector[vectorIx + i] = -1.0f
        }
        for (i in 0 until bp)
            vector[vectorIx + i] = 1.0f
    }

    override val vectorSize: Int get() = binarySize
}

class CategoricalEncoder(binaryIx: Int, vectorIx: Int, override val binarySize: Int) : VariableEncoder(binaryIx, vectorIx) {
    override fun encode(vector: FloatArray, instance: Instance, oneHot: Boolean, normalize: Boolean) {
        if (!oneHot) {
            for (i in 0 until binarySize)
                vector[vectorIx + i] = -1.0f
        }
        vector[instance.getFirst(binaryIx, binaryIx + binarySize)] = 1.0f
    }

    override val vectorSize: Int get() = binarySize

}
