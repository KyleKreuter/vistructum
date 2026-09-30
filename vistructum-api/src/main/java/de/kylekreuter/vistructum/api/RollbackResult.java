package de.kylekreuter.vistructum.api;

/**
 * Outcome of a rollback through the block log.
 *
 * @param restored number of blocks set back to the state before the players of the finding changed them
 * @param skipped number of blocks left unchanged because someone else changed them after the players of the finding
 * @see BlockLog#rollback(long, String)
 */
public record RollbackResult(int restored, int skipped) {

    /**
     * Validates the counts.
     *
     * @throws IllegalArgumentException if a count is negative
     */
    public RollbackResult {
        if (restored < 0 || skipped < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
