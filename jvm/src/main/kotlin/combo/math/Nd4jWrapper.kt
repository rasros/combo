package combo.math

import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j

class Nd4jVector(val array: INDArray) : Vector {

    override val size: Int get() = array.rows()
    override val sparse: Boolean get() = false
    override fun get(i: Int): Float = array.getFloat(i.toLong())

    override fun set(i: Int, x: Float) {
        array.putScalar(i.toLong(), x)
    }

    override fun dot(v: VectorView): Float {
        return if (v is Nd4jVector) Nd4j.getBlasWrapper().dot(array, v.array).toFloat()
        else v dot this
    }

    override fun assign(v: VectorView) {
        if (v is Nd4jVector) Nd4j.copy(v.array, this.array)
        else super.assign(v)
    }

    override fun norm2() = array.norm2Number().toFloat()
    override fun sum() = array.sumNumber().toFloat()

    override fun plus(v: VectorView) = if (v is Nd4jVector) Nd4jVector(array.add(v.array))
    else super.plus(v)

    override fun minus(v: VectorView) = if (v is Nd4jVector) Nd4jVector(array.sub(v.array))
    else super.minus(v)

    override fun times(v: VectorView) = if (v is Nd4jVector) Nd4jVector(array.mul(v.array))
    else super.times(v)

    override fun div(v: VectorView) = if (v is Nd4jVector) Nd4jVector(array.div(v.array))
    else super.div(v)

    override fun plus(f: Float) = Nd4jVector(array.add(f))
    override fun minus(f: Float) = Nd4jVector(array.sub(f))
    override fun times(f: Float) = Nd4jVector(array.mul(f))
    override fun div(f: Float) = Nd4jVector(array.div(f))

    override fun add(v: VectorView) {
        if (v is Nd4jVector) array.addi(v.array)
        else super.add(v)
    }

    override fun subtract(v: VectorView) {
        if (v is Nd4jVector) array.subi(v.array)
        else super.minus(v)
    }

    override fun multiply(v: VectorView) {
        if (v is Nd4jVector) array.muli(v.array)
        else super.multiply(v)
    }

    override fun divide(v: VectorView) {
        if (v is Nd4jVector) array.divi(v.array)
        else super.divide(v)
    }

    override fun add(f: Float) {
        array.addi(f)
    }

    override fun subtract(f: Float) {
        array.subi(f)
    }

    override fun multiply(f: Float) {
        array.muli(f)
    }

    override fun divide(f: Float) {
        array.divi(f)
    }

    override fun toFloatArray(): FloatArray = array.toFloatVector()

    override fun copy(): Vector {
        val c = Nd4j.zeros(size, 1, 'c')
        Nd4j.copy(array, c)
        return Nd4jVector(c)
    }

    override fun vectorCopy() = copy()
    override fun asVector() = this
}

class Nd4jMatrix(val mat: INDArray) : Matrix {
    override fun set(row: Int, values: VectorView) {
        val vec: INDArray = if (values is Nd4jVector) values.array
        else Nd4j.create(values.toFloatArray())
        mat.putRow(row.toLong(), vec)
    }

    override val rows: Int get() = mat.rows()
    override val cols: Int get() = mat.columns()

    override fun set(i: Int, j: Int, x: Float) {
        mat.put(i, j, x)
    }

    override fun get(i: Int, j: Int) = mat.getFloat(i.toLong(), j.toLong())

    override fun get(row: Int) = Nd4jVector(mat.getRow(row.toLong()))

    override fun copy() = Nd4jVectorFactory.matrix(mat.toFloatMatrix())

    override fun times(v: VectorView) =
            if (v is Nd4jVector) Nd4jVector(mat.mmul(v.array))
            else Nd4jVectorFactory.vector(FloatArray(rows) {
                v dot this@Nd4jMatrix[it]
            })

    override fun transpose() {
        mat.transposei()
    }

    override val T: Nd4jMatrix get() = Nd4jMatrix(mat.transpose())

    override fun toArray(): Array<FloatArray> = mat.toFloatMatrix()
}

object Nd4jVectorFactory : VectorFactory {

    init {
        Nd4j.setDefaultDataTypes(DataType.FLOAT, DataType.FLOAT)
    }

    override fun zeroMatrix(rows: Int, columns: Int) = Nd4jMatrix(Nd4j.zeros(rows, columns))
    override fun zeroVector(size: Int) = Nd4jVector(Nd4j.zeros(size, 1, 'c'))

    override fun matrix(values: Array<FloatArray>) = Nd4jMatrix(
            if (values.isEmpty()) Nd4j.empty()
            else Nd4j.create(values))

    override fun vector(values: FloatArray) =
            if (values.isEmpty()) zeroVector(0)
            else Nd4jVector(Nd4j.create(values).reshape(intArrayOf(values.size, 1)))

}

fun VectorView.toNd4j() = if (this is Nd4jVector) this else Nd4jVectorFactory.zeroVector(size).also {
    for (i in this) it[i] = this[i]
}
