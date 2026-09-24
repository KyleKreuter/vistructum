package de.kylekreuter.vistructum.api;

/**
 * Assessment of a finding by a reviewer.
 */
public enum Verdict {

    /**
     * The finding shows a prohibited symbol.
     */
    CONFIRMED,

    /**
     * The finding does not show a prohibited symbol.
     */
    FALSE_ALARM
}
