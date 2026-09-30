package de.kylekreuter.vistructum.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Access to the block log of the server, which records which player placed or broke which block.
 *
 * <p>The block log is read from CoreProtect. Without CoreProtect, or with the integration turned off in the
 * configuration of the core plugin, {@link #available()} returns {@code false} and {@link #attribute(long, String)}
 * and {@link #rollback(long, String)} return futures that fail with an {@link IllegalStateException}. All returned futures complete on the server main thread as
 * specified by {@link Vistructum}.
 *
 * @see Vistructum#blockLog()
 */
public interface BlockLog {

    /**
     * Reports whether the block log can be read.
     *
     * @return {@code true} if CoreProtect is installed, enabled and connected
     */
    boolean available();

    /**
     * Determines the players of a finding from the block log and secures evidence from it.
     *
     * <p>The players whose placed blocks stood in the box of the finding when it was created become the players of
     * the finding. If the block log names no such player, the players of the finding stay unchanged. Evidence is
     * secured from the block log if none exists for the finding yet. Storing new players is written to the activity
     * log as {@link ActivityKind#ATTRIBUTED}.
     *
     * @param findingId identifier of the finding
     * @param actor name of the party that requests the action, as it is to be stored and displayed
     * @return a future completing with the finding as stored afterwards, or with an empty {@link Optional} if no
     *         finding has this identifier; the future fails with an {@link IllegalStateException} if the block log is
     *         not available
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Optional<Finding>> attribute(long findingId, String actor);

    /**
     * Reverts the block changes that the players of a confirmed finding made inside the box of the finding.
     *
     * <p>Only blocks inside the box that a player of the finding placed or broke are changed. Each of them is set back
     * to its state before the first logged change of a player of the finding. A block is skipped if its last logged
     * change inside the box is by someone else, or if it no longer matches the last logged change. Blocks outside the
     * box stay unchanged. Every restored block is written to the block log. The finding and its evidence stay
     * unchanged. A rollback that restores at least one block is written to the activity log as
     * {@link ActivityKind#ROLLED_BACK}.
     *
     * @param findingId identifier of the finding
     * @param actor name of the party that requests the action, as it is to be stored and displayed
     * @return a future completing with the numbers of restored and skipped blocks, or with an empty {@link Optional}
     *         if no finding has this identifier; the future fails with an {@link IllegalStateException} if the block
     *         log is not available, the finding is not confirmed or it has no players
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Optional<RollbackResult>> rollback(long findingId, String actor);

    /**
     * Returns the latest rollback of a finding.
     *
     * <p>The rollback is read from the activity log, so it is returned even while the block log is not available.
     *
     * @param findingId identifier of the finding
     * @return a future completing with the latest {@link ActivityKind#ROLLED_BACK} entry of the finding, or with an
     *         empty {@link Optional} if the finding was never rolled back or no finding has this identifier
     */
    CompletableFuture<Optional<Activity>> lastRollback(long findingId);
}
