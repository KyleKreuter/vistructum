package de.kylekreuter.vistructum.core.history;

import de.kylekreuter.vistructum.api.Activity;
import de.kylekreuter.vistructum.api.ActivityKind;
import de.kylekreuter.vistructum.api.BlockLog;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.core.MainThread;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.evidence.EvidenceKeeper;
import de.kylekreuter.vistructum.core.evidence.EvidenceStore;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BlockLogService implements BlockLog {

    private final MainThread mainThread;
    private final FindingStore findings;
    private final EvidenceStore evidence;
    private final EvidenceKeeper keeper;
    private final Optional<BlockHistory> history;
    private final Clock clock;

    public BlockLogService(MainThread mainThread, FindingStore findings, EvidenceStore evidence, EvidenceKeeper keeper,
                           Optional<BlockHistory> history, Clock clock) {
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.findings = Objects.requireNonNull(findings, "findings");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.history = Objects.requireNonNull(history, "history");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean available() {
        return history.isPresent();
    }

    @Override
    public CompletableFuture<Optional<Finding>> attribute(long findingId, String actor) {
        Objects.requireNonNull(actor, "actor");
        return withFinding(findingId, (blocks, finding) -> attribute(blocks, finding, actor));
    }

    @Override
    public CompletableFuture<Optional<Integer>> rollback(long findingId, String actor) {
        Objects.requireNonNull(actor, "actor");
        return withFinding(findingId, (blocks, finding) -> rollback(blocks, finding, actor));
    }

    @Override
    public CompletableFuture<Optional<Activity>> lastRollback(long findingId) {
        return mainThread.handOff(findings.lastEvent(findingId, ActivityKind.ROLLED_BACK));
    }

    private <T> CompletableFuture<Optional<T>> withFinding(long findingId,
                                                          FindingAction<T> action) {
        if (history.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("the block log is not available"));
        }
        BlockHistory blocks = history.get();
        return mainThread.handOff(findings.find(findingId).thenCompose(found -> found
                .map(finding -> action.apply(blocks, finding).thenApply(Optional::of))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()))));
    }

    private CompletableFuture<Finding> attribute(BlockHistory blocks, Finding finding, String actor) {
        return blocks.lookup(finding.world(), finding.box(), finding.createdAt())
                .thenApply(entries -> Builders.of(entries, finding.box()))
                .thenCompose(players -> stored(finding, players, actor))
                .thenCompose(stored -> evidence.hasEvidence(stored.id())
                        .thenCompose(present -> present ? CompletableFuture.completedFuture(false)
                                : keeper.secureFromHistory(stored))
                        .thenApply(ignored -> stored));
    }

    private CompletableFuture<Finding> stored(Finding finding, Set<UUID> players, String actor) {
        if (players.isEmpty()) {
            return CompletableFuture.completedFuture(finding);
        }
        return findings.attribute(finding.id(), players, actor, clock.instant())
                .thenApply(updated -> updated.orElse(finding));
    }

    private CompletableFuture<Integer> rollback(BlockHistory blocks, Finding finding, String actor) {
        if (!finding.review().map(Review::verdict).filter(Verdict.CONFIRMED::equals).isPresent()) {
            return CompletableFuture.failedFuture(new IllegalStateException("finding #" + finding.id()
                    + " is not confirmed"));
        }
        if (finding.players().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("finding #" + finding.id()
                    + " has no players"));
        }
        Instant now = clock.instant();
        return blocks.lookup(finding.world(), finding.box(), now)
                .thenApply(entries -> RollbackPlan.of(entries, finding.box(), finding.players(), now))
                .thenCompose(plan -> plan
                        .map(steps -> blocks.rollback(finding.world(), finding.box(), steps.playerNames(),
                                        steps.since())
                                .thenCompose(reverted -> findings.record(finding.id(), ActivityKind.ROLLED_BACK,
                                        actor, clock.instant()).thenApply(ignored -> reverted)))
                        .orElseGet(() -> CompletableFuture.completedFuture(0)));
    }

    @FunctionalInterface
    private interface FindingAction<T> {

        CompletableFuture<T> apply(BlockHistory blocks, Finding finding);
    }
}
