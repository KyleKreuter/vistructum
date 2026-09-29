package de.kylekreuter.vistructum.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sign-in and public evidence links of the web application.
 *
 * <p>All tokens are random secrets of 256 bits, encoded as URL-safe Base64 without padding. The server stores only
 * the SHA-256 hashes of login and session tokens. Share tokens are stored as they are, because a public link stays
 * the same for the whole lifetime of its finding. A login token is valid for {@value #LOGIN_MINUTES} minutes and can be redeemed once; a
 * session lasts {@value #SESSION_HOURS} hours. Checking permissions is the task of the caller: a login token
 * should be issued only to a player allowed to review.
 *
 * <p>All returned futures complete on the server main thread as specified by {@link Vistructum}.
 *
 * @see Vistructum#web()
 */
public interface WebAccess {

    /**
     * Minutes for which a login token can be redeemed.
     */
    int LOGIN_MINUTES = 5;

    /**
     * Hours for which a session is accepted.
     */
    int SESSION_HOURS = 12;

    /**
     * Issues a one-time login token for a player.
     *
     * @param player identifier of the player
     * @param playerName account name of the player, stored as the reviewer name of the session
     * @return a future completing with the login token
     * @throws NullPointerException if an argument is {@code null}
     */
    CompletableFuture<String> issueLogin(UUID player, String playerName);

    /**
     * Redeems a login token and creates a session.
     *
     * @param loginToken token from {@link #issueLogin(UUID, String)}
     * @return a future completing with the session, or with an empty {@link Optional} if the token is unknown,
     *         expired or already redeemed
     * @throws NullPointerException if {@code loginToken} is {@code null}
     */
    CompletableFuture<Optional<IssuedSession>> redeemLogin(String loginToken);

    /**
     * Looks up a session.
     *
     * @param sessionToken token from {@link IssuedSession#token()}
     * @return a future completing with the session, or with an empty {@link Optional} if the token is unknown,
     *         expired or ended
     * @throws NullPointerException if {@code sessionToken} is {@code null}
     */
    CompletableFuture<Optional<WebSession>> session(String sessionToken);

    /**
     * Ends a session so that its token is no longer accepted.
     *
     * @param sessionToken token from {@link IssuedSession#token()}
     * @return a future completing when the session has been removed; an unknown token is ignored
     * @throws NullPointerException if {@code sessionToken} is {@code null}
     */
    CompletableFuture<Void> endSession(String sessionToken);

    /**
     * Activates the public link to the evidence of a confirmed finding.
     *
     * <p>A finding has exactly one share token, issued on the first activation and kept for good. Activating an
     * inactive link again brings back the same token; activating an active link changes nothing. A later change of
     * the verdict does not deactivate the link. Every activation is added to the activity log.
     *
     * @param findingId identifier of the finding
     * @param actor name of the sharing party as it is to be stored and displayed
     * @return a future completing with the share token, or with an empty {@link Optional} if no finding has this
     *         identifier, or the link is inactive and the finding is not confirmed or has no evidence
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Optional<String>> share(long findingId, String actor);

    /**
     * Deactivates the public link to the evidence of a finding.
     *
     * <p>The token is kept, so activating the link again restores the same address. The action is added to the
     * activity log.
     *
     * @param findingId identifier of the finding
     * @param actor name of the deactivating party as it is to be stored and displayed
     * @return a future completing with {@code true} if an active link was deactivated, or {@code false} if none was
     *         active
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Boolean> unshare(long findingId, String actor);

    /**
     * Resolves a share token to the finding it grants access to.
     *
     * @param shareToken token from {@link #share(long, String)}
     * @return a future completing with the finding identifier, or with an empty {@link Optional} if the token is
     *         unknown or its link is inactive
     * @throws NullPointerException if {@code shareToken} is {@code null}
     */
    CompletableFuture<Optional<Long>> sharedFinding(String shareToken);

    /**
     * Looks up the active public link to the evidence of a finding.
     *
     * @param findingId identifier of the finding
     * @return a future completing with the active link, or with an empty {@link Optional} if the link of the finding
     *         is inactive or was never activated
     */
    CompletableFuture<Optional<EvidenceShare>> shared(long findingId);
}
