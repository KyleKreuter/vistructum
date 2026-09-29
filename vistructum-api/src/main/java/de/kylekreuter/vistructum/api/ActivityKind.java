package de.kylekreuter.vistructum.api;

/**
 * Kind of an entry in the activity log.
 */
public enum ActivityKind {

    /**
     * A finding was confirmed.
     */
    CONFIRMED,

    /**
     * A finding was rejected as a false alarm.
     */
    FALSE_ALARM,

    /**
     * The evidence of a finding was made available through a public link.
     */
    SHARED,

    /**
     * The public link to the evidence of a finding was revoked.
     */
    UNSHARED
}
