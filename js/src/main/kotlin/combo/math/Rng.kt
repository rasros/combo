package combo.math

import combo.util.require
import kotlin.math.*

actual class Rng actual constructor(actual val seed: Long) {

    private val rng = require("seedrandom")(seed)

    private var nextGaussian: Double = 0.0
    private var haveNextGaussian = false

    actual fun gaussian(): Double {
        return if (haveNextGaussian) {
            haveNextGaussian = false
            nextGaussian
        } else {
            val u1 = double()
            val u2 = double()
            val r = sqrt(-2.0 * ln(u1))
            val theta = 2 * PI * u2
            haveNextGaussian = true
            nextGaussian = r * cos(theta)
            r * sin(theta)
        }
    }

    actual fun double(): Double = rng()
    actual fun int(bound: Int) = double().toRawBits().toInt().absoluteValue % bound
    //actual fun long(bound: Long) = double().toRawBits().absoluteValue % bound
    actual fun boolean() = double().toRawBits() and 1L == 1L

}