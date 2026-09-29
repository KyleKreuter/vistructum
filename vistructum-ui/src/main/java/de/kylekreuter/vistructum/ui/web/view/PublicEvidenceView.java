package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.Evidence;
import de.kylekreuter.vistructum.api.Finding;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record PublicEvidenceView(PublicFindingView finding, EvidenceView evidence) {

    public static PublicEvidenceView of(Finding finding, Evidence evidence, Map<UUID, Optional<String>> names) {
        return new PublicEvidenceView(new PublicFindingView(finding.id(), finding.createdAt().toString(),
                finding.review().map(review -> review.verdict().name()).orElse(null),
                PlayerView.of(finding.players(), names)), EvidenceView.relative(evidence));
    }

    public record PublicFindingView(long id, String createdAt, String verdict, List<PlayerView> players) {
    }
}
