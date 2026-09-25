package de.kylekreuter.vistructum.ui.text;

public enum Message {

    COMMAND_NO_PERMISSION("command.no-permission"),
    COMMAND_PLAYER_ONLY("command.player-only"),
    COMMAND_FAILED("command.failed"),
    COMMAND_USAGE("command.usage"),
    COMMAND_ID_MISSING("command.id-missing"),
    COMMAND_COUNT_INVALID("command.count-invalid"),
    COMMAND_FINDING_MISSING("command.finding-missing"),
    COMMAND_WORLD_NOT_LOADED("command.world-not-loaded"),
    COMMAND_WORLD_UNKNOWN("command.world-unknown"),
    STATUS_INFERENCE("status.inference"),
    STATUS_INFERENCE_UNAVAILABLE("status.inference-unavailable"),
    STATUS_CHANGES("status.changes"),
    STATUS_OPEN("status.open"),
    STATUS_NO_SCAN("status.no-scan"),
    REVIEW_EMPTY("review.empty"),
    REVIEW_HEADER("review.header"),
    REVIEW_LINE("review.line"),
    REVIEW_MORE("review.more"),
    FINDING_DETAIL("finding.detail"),
    FINDING_UNREVIEWED("finding.unreviewed"),
    FINDING_CREATED("finding.created"),
    FINDING_CONFIRMED("finding.confirmed"),
    FINDING_FALSE_ALARM("finding.false-alarm"),
    SCAN_PLANNING("scan.planning"),
    SCAN_PROGRESS("scan.progress"),
    SCAN_QUEUED("scan.queued"),
    SCAN_ALREADY_RUNNING("scan.already-running"),
    SCAN_CANCELLED("scan.cancelled"),
    SCAN_DONE("scan.done"),
    SCAN_STOPPED("scan.stopped"),
    SCAN_FAILED("scan.failed"),
    PACK_PROMPT("pack.prompt"),
    SOURCE_MASK("source.mask"),
    SOURCE_FULLSCAN("source.fullscan"),
    AGE_MINUTES("age.minutes"),
    AGE_HOURS("age.hours"),
    AGE_DAYS("age.days"),
    MENU_FINDING("menu.finding"),
    MENU_BUILDER_UNKNOWN("menu.builder-unknown"),
    MENU_BUILDER_MORE("menu.builder-more"),
    MENU_FIELD_WORLD("menu.field-world"),
    MENU_FIELD_LOCATION("menu.field-location"),
    MENU_FIELD_DETECTED("menu.field-detected"),
    MENU_FIELD_PROBABILITY("menu.field-probability"),
    MENU_FIELD_SOURCE("menu.field-source"),
    MENU_FIELD_CONFIRMED("menu.field-confirmed"),
    MENU_FIELD_FALSE_ALARM("menu.field-false-alarm"),
    MENU_CONFIRM("menu.confirm"),
    MENU_FALSE_ALARM("menu.false-alarm"),
    MENU_TELEPORT("menu.teleport"),
    MENU_BACK("menu.back"),
    MENU_PREVIOUS("menu.previous"),
    MENU_NEXT("menu.next"),
    MENU_PAGE("menu.page"),
    MENU_FILTER_OPEN("menu.filter-open"),
    MENU_FILTER_OPEN_HINT("menu.filter-open-hint"),
    MENU_FILTER_CLOSED("menu.filter-closed"),
    MENU_FILTER_CLOSED_HINT("menu.filter-closed-hint"),
    MENU_CARD("menu.card"),
    MENU_CARD_WORLD("menu.card-world"),
    MENU_CARD_LOCATION("menu.card-location"),
    MENU_CARD_PROBABILITY("menu.card-probability"),
    MENU_CARD_AGE("menu.card-age"),
    MENU_CARD_CONFIRMED("menu.card-confirmed"),
    MENU_CARD_FALSE_ALARM("menu.card-false-alarm"),
    MENU_CARD_HINT("menu.card-hint");

    private final String path;

    Message(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
