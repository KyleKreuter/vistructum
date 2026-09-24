package de.kylekreuter.vistructum.api;

import java.time.Instant;
import java.util.Objects;

public record Review(Verdict verdict, String reviewer, Instant reviewedAt) {

    public Review {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reviewer, "reviewer");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
    }
}
