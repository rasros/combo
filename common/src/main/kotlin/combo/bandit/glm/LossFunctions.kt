@file:JvmName("LossFunctions")

package combo.bandit.glm

import combo.math.IdentityTransform
import combo.math.Transform
import kotlin.jvm.JvmName
import kotlin.math.abs
import kotlin.math.sign

class HuberLoss(val delta: Float) : Transform {
    override fun apply(value: Float) =
            if (value <= delta) value
            else (value * delta) / abs(value)
}

/**
 * For use with l2 regularized regression
 */
typealias MSELoss = IdentityTransform

/**
 * For use with l1 regularized regression
 */
object AbsoluteLoss : Transform {
    override fun apply(value: Float) = value.sign
}

