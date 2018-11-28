@file:JvmName("Exceptions")

package combo.sat

import kotlin.jvm.JvmName

sealed class ValidationException(message: String, cause: Throwable? = null, val literal: Literal? = null)
    : RuntimeException(message, cause) {
    override fun toString(): String {
        return super.toString() + if (literal != null) " For literal: ${literal}." else ""
    }
}

class TimeoutException(timeout: Long) : ValidationException("Timeout of ${timeout}ms reached.")

class IterationsReachedException(itr: Int) : ValidationException("Max iterations of $itr reached.")

class UnsatisfiableException(override val message: String = "The model is unsatisfiable because " +
        "there is a contradiction in the specification.",
                             override val cause: Throwable? = null,
                             literal: Literal? = null) : ValidationException(message, cause, literal)
