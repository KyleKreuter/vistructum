package de.kylekreuter.vistructum.ui.web.view;

import java.util.List;

public record FindingPageView(List<FindingView> items, long total, long page, long pageSize) {

    public FindingPageView {
        items = List.copyOf(items);
    }
}
