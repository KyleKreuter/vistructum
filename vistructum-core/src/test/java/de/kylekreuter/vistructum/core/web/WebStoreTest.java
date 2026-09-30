package de.kylekreuter.vistructum.core.web;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.EvidenceShare;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.FindingCandidate;
import de.kylekreuter.vistructum.api.IssuedSession;
import de.kylekreuter.vistructum.api.Preview;
import de.kylekreuter.vistructum.api.Source;
import de.kylekreuter.vistructum.api.Verdict;
import de.kylekreuter.vistructum.api.WebSession;
import de.kylekreuter.vistructum.core.alert.DetectedCandidate;
import de.kylekreuter.vistructum.core.alert.FindingStore;
import de.kylekreuter.vistructum.core.alert.ModelInput;
import de.kylekreuter.vistructum.core.evidence.EvidenceSettings;
import de.kylekreuter.vistructum.core.evidence.EvidenceStore;
import de.kylekreuter.vistructum.core.evidence.VolumeSnapshot;
import de.kylekreuter.vistructum.core.store.Database;
import de.kylekreuter.vistructum.core.store.TestDatabase;
import de.kylekreuter.vistructum.inference.ModelKind;
import de.kylekreuter.vistructum.inference.SurfaceScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-28T20:00:00Z");
    private static final UUID STAFF = UUID.fromString("e3e04125-846a-3ff7-9f1f-aa61ff8eb6b7");

    @TempDir
    Path directory;
    private Database database;
    private FindingStore findings;
    private EvidenceStore evidence;
    private WebStore store;

    @BeforeEach
    void open() {
        database = TestDatabase.open(directory);
        findings = new FindingStore(database);
        evidence = new EvidenceStore(database);
        store = new WebStore(database, new Tokens());
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void tokensAreUrlSafeRandomAndStoredOnlyAsHashes() throws Exception {
        Tokens tokens = new Tokens();
        String token = tokens.next();

        assertEquals(Tokens.TOKEN_BYTES, Base64.getUrlDecoder().decode(token).length);
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(token, tokens.next());
        assertEquals(64, Tokens.hash(token).length());

        String login = store.issueLogin(STAFF, "Staff", NOW).get();
        assertEquals(0, count("SELECT count(*) FROM web_logins WHERE token_hash = '" + login + "'"));
        assertEquals(1, count("SELECT count(*) FROM web_logins WHERE token_hash = '" + Tokens.hash(login) + "'"));
    }

    @Test
    void loginIsRedeemedOnceIntoATwelveHourSession() throws Exception {
        String login = store.issueLogin(STAFF, "Staff", NOW).get();

        IssuedSession issued = store.redeemLogin(login, NOW.plusSeconds(60)).get().orElseThrow();
        assertEquals(new WebSession(STAFF, "Staff", NOW.plusSeconds(60).plus(Duration.ofHours(12))), issued.session());
        assertTrue(store.redeemLogin(login, NOW.plusSeconds(61)).get().isEmpty());

        assertEquals(Optional.of(issued.session()), store.session(issued.token(), NOW.plus(Duration.ofHours(12))).get());
        assertTrue(store.session(issued.token(), NOW.plusSeconds(60).plus(Duration.ofHours(12))).get().isEmpty());
        assertTrue(store.session("unknown", NOW).get().isEmpty());
    }

    @Test
    void loginExpiresAfterFiveMinutesAndIsConsumedAnyway() throws Exception {
        String login = store.issueLogin(STAFF, "Staff", NOW).get();

        assertTrue(store.redeemLogin(login, NOW.plus(Duration.ofMinutes(5))).get().isEmpty());
        assertEquals(0, count("SELECT count(*) FROM web_logins"));
    }

    @Test
    void endedSessionIsGone() throws Exception {
        IssuedSession issued = store.redeemLogin(store.issueLogin(STAFF, "Staff", NOW).get(), NOW).get().orElseThrow();

        store.endSession(issued.token()).get();

        assertTrue(store.session(issued.token(), NOW).get().isEmpty());
    }

    @Test
    void deleteExpiredRemovesLoginsAndSessionsPastTheirExpiry() throws Exception {
        store.issueLogin(STAFF, "Staff", NOW).get();
        IssuedSession issued = store.redeemLogin(store.issueLogin(STAFF, "Staff", NOW).get(), NOW).get().orElseThrow();

        assertEquals(1, store.deleteExpired(NOW.plus(Duration.ofMinutes(5))).get());
        assertTrue(store.session(issued.token(), NOW).get().isPresent());
        assertEquals(1, store.deleteExpired(NOW.plus(Duration.ofHours(12))).get());
        assertTrue(store.session(issued.token(), NOW).get().isEmpty());
    }

    @Test
    void onlyConfirmedFindingsWithEvidenceCanBeShared() throws Exception {
        Finding withoutEvidence = finding("a");
        Finding open = finding("b");
        secureEvidence(open);

        assertTrue(store.share(open.id(), "Staff", NOW).get().isEmpty());
        findings.review(withoutEvidence.id(), Verdict.CONFIRMED, "Staff", NOW).get();
        assertTrue(store.share(withoutEvidence.id(), "Staff", NOW).get().isEmpty());
        findings.review(open.id(), Verdict.FALSE_ALARM, "Staff", NOW).get();
        assertTrue(store.share(open.id(), "Staff", NOW).get().isEmpty());
        findings.review(open.id(), Verdict.CONFIRMED, "Staff", NOW).get();
        assertTrue(store.share(open.id(), "Staff", NOW).get().isPresent());
    }

    @Test
    void sharingAgainKeepsTheActiveLink() throws Exception {
        Finding finding = confirmedWithEvidence();

        String first = store.share(finding.id(), "Staff", NOW).get().orElseThrow();
        String second = store.share(finding.id(), "Admin", NOW.plusSeconds(10)).get().orElseThrow();

        assertEquals(first, second);
        assertEquals(Optional.of(new EvidenceShare(first, NOW)), store.shared(finding.id()).get());
        assertEquals(1, count("SELECT count(*) FROM finding_events"));
    }

    @Test
    void unshareDeactivatesTheLinkAndShareRestoresTheSameToken() throws Exception {
        Finding finding = confirmedWithEvidence();
        String token = store.share(finding.id(), "Staff", NOW).get().orElseThrow();

        assertTrue(store.unshare(finding.id(), "Staff", NOW.plusSeconds(5)).get());
        assertFalse(store.unshare(finding.id(), "Staff", NOW.plusSeconds(6)).get());
        assertTrue(store.sharedFinding(token).get().isEmpty());
        assertTrue(store.shared(finding.id()).get().isEmpty());

        assertEquals(Optional.of(token), store.share(finding.id(), "Admin", NOW.plusSeconds(20)).get());
        assertEquals(Optional.of(finding.id()), store.sharedFinding(token).get());
        assertEquals(Optional.of(new EvidenceShare(token, NOW.plusSeconds(20))), store.shared(finding.id()).get());
        assertEquals(3, count("SELECT count(*) FROM finding_events"));
    }

    @Test
    void linkKeepsWorkingWhenTheVerdictChanges() throws Exception {
        Finding finding = confirmedWithEvidence();
        String token = store.share(finding.id(), "Staff", NOW).get().orElseThrow();

        findings.review(finding.id(), Verdict.FALSE_ALARM, "Staff", NOW.plusSeconds(5)).get();

        assertEquals(Optional.of(finding.id()), store.sharedFinding(token).get());
        assertEquals(Optional.of(token), store.share(finding.id(), "Staff", NOW.plusSeconds(6)).get());
    }

    @Test
    void inactiveLinkOfAFindingNoLongerConfirmedCannotBeActivated() throws Exception {
        Finding finding = confirmedWithEvidence();
        store.share(finding.id(), "Staff", NOW).get();
        store.unshare(finding.id(), "Staff", NOW.plusSeconds(1)).get();

        findings.review(finding.id(), Verdict.FALSE_ALARM, "Staff", NOW.plusSeconds(5)).get();

        assertTrue(store.share(finding.id(), "Staff", NOW.plusSeconds(6)).get().isEmpty());
    }

    @Test
    void sharesCascadeWithTheFinding() throws Exception {
        Finding finding = confirmedWithEvidence();
        store.share(finding.id(), "Staff", NOW).get();
        store.unshare(finding.id(), "Staff", NOW).get();

        findings.deleteReviewedBefore(Verdict.CONFIRMED, NOW.plusSeconds(1)).get();

        assertEquals(0, count("SELECT count(*) FROM evidence_shares"));
        assertEquals(0, count("SELECT count(*) FROM finding_events"));
    }

    @Test
    void activeLinksKeepTheirFindingsPastTheRetentionPeriod() throws Exception {
        Finding finding = confirmedWithEvidence();
        store.share(finding.id(), "Staff", NOW).get();

        assertEquals(0, findings.deleteReviewedBefore(Verdict.CONFIRMED, NOW.plusSeconds(100)).get());

        store.unshare(finding.id(), "Staff", NOW.plusSeconds(200)).get();
        assertEquals(0, findings.deleteReviewedBefore(Verdict.CONFIRMED, NOW.plusSeconds(200)).get());
        assertEquals(1, findings.deleteReviewedBefore(Verdict.CONFIRMED, NOW.plusSeconds(201)).get());
    }

    private Finding confirmedWithEvidence() throws Exception {
        Finding finding = finding("world");
        secureEvidence(finding);
        findings.review(finding.id(), Verdict.CONFIRMED, "Staff", NOW).get();
        return finding;
    }

    private Finding finding(String world) throws Exception {
        return findings.insertUnlessDuplicate(new DetectedCandidate(new FindingCandidate(Source.MASK, world,
                new BlockBox(0, 60, 0, 2, 60, 2), 0.97, 2, Set.of(STAFF), "Achse Y", "bf-mask-2",
                new Preview(1, 1, new byte[]{40})), new ModelInput(ModelKind.MASK,
                SurfaceScene.maskOnly(1, 1, new byte[]{1}), 0, 0, 64, 64)), NOW, Duration.ofDays(14)).get()
                .orElseThrow();
    }

    private void secureEvidence(Finding finding) throws Exception {
        BlockBox region = new BlockBox(0, 60, 0, 0, 60, 0);
        assertTrue(evidence.secure(finding.id(), finding.world(), finding.box(),
                new VolumeSnapshot.Builder(region).set(0, "minecraft:air").build(),
                new EvidenceSettings(32, Duration.ofSeconds(30), 0), NOW).get());
    }

    private int count(String sql) throws Exception {
        return database.transaction(connection -> {
            try (PreparedStatement select = connection.prepareStatement(sql); ResultSet rows = select.executeQuery()) {
                return rows.getInt(1);
            }
        }).get();
    }
}
