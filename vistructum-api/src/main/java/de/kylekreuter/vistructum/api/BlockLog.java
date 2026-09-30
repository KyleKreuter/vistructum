package de.kylekreuter.vistructum.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Access to the block log of the server, which records which player placed or broke which block.
 *
 * <p>The block log is read from CoreProtect. Without CoreProtect, or with the integration turned off in the
 * configuration of the core plugin, {@link #available()} returns {@code false} and the other methods return futures
 * that fail with an {@link IllegalStateException}. All returned futures complete on the server main thread as
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
     * Reverts the block changes that the players of a confirmed finding made at the finding.
     *
     * <p>The rollback covers the players of the finding, block placements and removals only, and the time since the
     * first change a player of the finding logged inside the box of the finding. It reaches as far around the centre
     * of the box as half its largest extent plus one block, so changes of these players right next to the box are
     * reverted as well. The finding and its evidence stay unchanged. The rollback is written to the activity log as
     * {@link ActivityKind#ROLLED_BACK}.
     *
     * @param findingId identifier of the finding
     * @param actor name of the party that requests the action, as it is to be stored and displayed
     * @return a future completing with the number of reverted block changes, or with an empty {@link Optional} if no
     *         finding has this identifier; the future fails with an {@link IllegalStateException} if the block log is
     *         not available, the finding is not confirmed or it has no players
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Optional<Integer>> rollback(long findingId, String actor);
}
