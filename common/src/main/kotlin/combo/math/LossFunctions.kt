@file:JvmName("LossFunctions")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.abs

/**
 * In general the loss function is expressed through the link function when using generalized linear models. Here are
 * some extra loss functions to support additional use cases.
 */

interface Loss {
    fun loss(diff: Float): Float
    fun dloss(diff: Float): Float
}

/**
 * For use with l2 regularized regression
 */
object SquaredLoss : Loss {
    override fun loss(diff: Float) = 0.5f * diff * diff
    override fun dloss(diff: Float) = diff
}

/**
 * For use with l1 regularized regression
 */
object AbsoluteLoss : Loss {
    override fun loss(diff: Float) = abs(diff)
    override fun dloss(diff: Float) = diff / abs(diff)
}

/**
 * This will optimize Huber loss for linear regression. The inverse transformation is the derivative of the huber loss
 * function divided by value. In effect, this caps the gradient step to the delta parameter.
 */
class HuberLoss(val delta: Float) : Loss {
    override fun loss(diff: Float) = if (diff <= delta) 0.5f * diff * diff else delta * (abs(diff) - delta / 2.0f)
    override fun dloss(diff: Float) = if (diff <= delta) diff else (diff * delta) / abs(diff)
}
