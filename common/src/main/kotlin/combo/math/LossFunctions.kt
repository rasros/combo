@file:JvmName("LossFunctions")

package combo.math

import kotlin.jvm.JvmName
import kotlin.math.abs

/**
 * In general the loss function is expressed through the link function when using generalized linear models. Here are
 * some extra loss functions to support additional use cases.
 */

interface Loss {
    fun loss(diff: Double): Double
    fun dloss(diff: Double): Double
    fun asTransform() = object : Transform {
        override fun inverse(value: Double) = dloss(value)
        override fun apply(value: Double) = throw UnsupportedOperationException()
        override fun backtransform(stat: VarianceStatistic) = throw UnsupportedOperationException()
    }
}

/**
 * For use with l2 regularized regression
 */
object SquaredLoss : Loss {
    override fun loss(diff: Double) = 0.5 * diff * diff
    override fun dloss(diff: Double) = diff
}

/**
 * For use with l1 regularized regression
 */
object AbsoluteLoss : Loss {
    override fun loss(diff: Double) = abs(diff)
    override fun dloss(diff: Double) = diff / abs(diff)
}

/**
 * This will optimize Huber loss for linear regression. The inverse transformation is the derivative of the huber loss
 * function divided by value. In effect, this caps the gradient step to the delta parameter.
 */
class HuberLoss(val delta: Double) : Loss {
    override fun loss(diff: Double) = if (diff <= delta) 0.5 * diff * diff else delta * (abs(diff) - delta / 2.0)
    override fun dloss(diff: Double) = if (diff <= delta) diff else (diff * delta) / abs(diff)
}
