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
    UNSHARED,

    /**
     * The players of a finding were determined from the block log.
     *
     * @see BlockLog#attribute(long, String)
     */
    ATTRIBUTED,

    /**
     * The block changes of the players of a finding were reverted through the block log.
     *
     * @see BlockLog#rollback(long, String)
     */
    ROLLED_BACK
}
