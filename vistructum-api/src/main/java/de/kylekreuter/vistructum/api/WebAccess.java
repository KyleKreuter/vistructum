package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sign-in and public evidence links of the web application.
 *
 * <p>All tokens are random secrets of 256 bits, encoded as URL-safe Base64 without padding. The server stores only
 * their SHA-256 hashes. A login token is valid for {@value #LOGIN_MINUTES} minutes and can be redeemed once; a
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
     * Makes the evidence of a confirmed finding available through a public link.
     *
     * <p>A finding has at most one public link. Sharing again revokes the previous link and issues a new token.
     * The action is added to the activity log.
     *
     * @param findingId identifier of the finding
     * @param actor name of the sharing party as it is to be stored and displayed
     * @return a future completing with the share token, or with an empty {@link Optional} if no finding has this
     *         identifier, the finding is not confirmed or it has no evidence
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Optional<String>> share(long findingId, String actor);

    /**
     * Revokes the public link to the evidence of a finding.
     *
     * @param findingId identifier of the finding
     * @param actor name of the revoking party as it is to be stored and displayed
     * @return a future completing with {@code true} if a link was revoked, or {@code false} if none was active
     * @throws NullPointerException if {@code actor} is {@code null}
     */
    CompletableFuture<Boolean> unshare(long findingId, String actor);

    /**
     * Resolves a share token to the finding it grants access to.
     *
     * @param shareToken token from {@link #share(long, String)}
     * @return a future completing with the finding identifier, or with an empty {@link Optional} if the token is
     *         unknown or revoked
     * @throws NullPointerException if {@code shareToken} is {@code null}
     */
    CompletableFuture<Optional<Long>> sharedFinding(String shareToken);

    /**
     * Reports since when the evidence of a finding is shared.
     *
     * @param findingId identifier of the finding
     * @return a future completing with the time the active link was created, or with an empty {@link Optional} if
     *         no link is active
     */
    CompletableFuture<Optional<Instant>> sharedSince(long findingId);
}
