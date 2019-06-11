package combo.bandit

enum class ParallelMode {

    /**
     * Updates are non-blocking through a linked list with compare-and-swap. The update queue can grow indefinitely.
     * Processing updates cannot block and batch min-size cannot be guaranteed.
     */
    NON_BLOCKING,

    /**
     * Updates are synchronized with a read/write lock. The update queue can grow indefinitely. Processing updates can
     * block if requested and batch min-size is guaranteed.
     */
    BLOCKING_SUPPORTED,

    /**
     * For cases where the updates must not lag too far behind. This is intended to be used in simulations.
     * When there is more than maximum batch size unprocessed input events (update/updateAll calls) then the choose
     * method will block until the data has been processed.
     */
    BOUNDED_QUEUE,
}