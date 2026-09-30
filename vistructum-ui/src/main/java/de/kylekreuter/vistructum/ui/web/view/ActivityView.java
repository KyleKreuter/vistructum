package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.Activity;

import java.util.List;

public record ActivityView(List<ItemView> items, long total, long page, long pageSize) {

    public ActivityView {
        items = List.copyOf(items);
    }

    public static ActivityView of(List<Activity> activity, long total, long page, long pageSize) {
        return new ActivityView(activity.stream().map(entry -> new ItemView(entry.at().toString(), entry.actor(),
                entry.kind().name(), entry.findingId())).toList(), total, page, pageSize);
    }

    public record ItemView(String at, String actor, String kind, long findingId) {
    }
}
