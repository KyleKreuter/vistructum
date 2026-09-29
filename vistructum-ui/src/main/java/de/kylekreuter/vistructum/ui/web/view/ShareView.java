package de.kylekreuter.vistructum.ui.web.view;

import java.time.Instant;

public record ShareView(String url, String sharedSince) {

    public static ShareView of(String url, Instant sharedSince) {
        return new ShareView(url, sharedSince.toString());
    }
}
