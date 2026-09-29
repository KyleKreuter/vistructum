package de.kylekreuter.vistructum.ui.web.view;

import de.kylekreuter.vistructum.api.Activity;

import java.util.List;

public record ActivityView(List<ItemView> items) {

    public static ActivityView of(List<Activity> activity) {
        return new ActivityView(activity.stream().map(entry -> new ItemView(entry.at().toString(), entry.actor(),
                entry.kind().name(), entry.findingId())).toList());
    }

    public record ItemView(String at, String actor, String kind, long findingId) {
    }
}
