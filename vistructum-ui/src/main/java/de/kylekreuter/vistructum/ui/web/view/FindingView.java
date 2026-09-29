package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.BlockBox;
import de.kylekreuter.vistructum.api.Finding;
import de.kylekreuter.vistructum.api.Review;
import de.kylekreuter.vistructum.api.Source;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record FindingView(long id, String source, String world, BoxView box, double score, long votes, String detail,
                          String modelVersion, String createdAt, List<PlayerView> players, ReviewView review,
                          boolean hasEvidence, boolean hasTerrain, String sharedSince, String shareUrl, Optional<String> teleport) {

    public static FindingView of(Finding finding, Map<UUID, Optional<String>> names, boolean hasEvidence,
                                 boolean hasTerrain, Optional<ShareView> share) {
        return new FindingView(finding.id(), source(finding.source()), finding.world(), BoxView.of(finding.box()),
                finding.score(), finding.votes(), finding.detail(), finding.modelVersion(),
                finding.createdAt().toString(), PlayerView.of(finding.players(), names),
                finding.review().map(ReviewView::of).orElse(null), hasEvidence, hasTerrain,
                share.map(ShareView::sharedSince).orElse(null), share.map(ShareView::url).orElse(null),
                Optional.empty());
    }

    public FindingView withTeleport(BlockBox box, Optional<String> dimension) {
        String tp = "tp @s " + box.centerX() + " " + Math.floorDiv(box.minY() + box.maxY(), 2) + " " + box.centerZ();
        String command = dimension.map(key -> "/execute in " + key + " run " + tp).orElse("/" + tp);
        return new FindingView(id, source, world, this.box, score, votes, detail, modelVersion, createdAt, players,
                review, hasEvidence, hasTerrain, sharedSince, shareUrl, Optional.of(command));
    }

    public static String source(Source source) {
        return source.modelKind();
    }

    public record BoxView(long minX, long minY, long minZ, long maxX, long maxY, long maxZ) {

        static BoxView of(BlockBox box) {
            return new BoxView(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        }
    }

    public record ReviewView(String verdict, String reviewer, String reviewedAt) {

        static ReviewView of(Review review) {
            return new ReviewView(review.verdict().name(), review.reviewer(), review.reviewedAt().toString());
        }
    }
}
