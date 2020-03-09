package combo.sat

/**
 * A validation exception is thrown only if there is a logical contradiction in the model or the model could not be
 * solved due to timeouts or iterations reached. In general, this does not indicate an incorrect usage of the library.
 */
sealed class ValidationException(message: String, cause: Throwable? = null, val literal: Int? = null)
    : RuntimeException(message, cause) {
    override fun toString(): String {
        return super.toString() + if (literal != null) " For literal: $literal." else ""
    }
}

class TimeoutException(timeout: Long) : ValidationException("Timeout of ${timeout}ms reached.")

class IterationsReachedException(msg: String) : ValidationException(msg) {
    constructor(itr: Int) : this("Max iterations of $itr reached.")
}

class NumericalInstabilityException(msg: String) : ValidationException(msg)

class UnsatisfiableException(override val message: String = "The model is unsatisfiable because " +
        "there is a contradiction in the specification.",
                             override val cause: Throwable? = null,
                             literal: Int? = null) : ValidationException(message, cause, literal)
