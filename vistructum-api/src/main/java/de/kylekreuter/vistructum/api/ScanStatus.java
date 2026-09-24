package de.kylekreuter.vistructum.api;

/**
 * Processing state of a {@link ScanJob}.
 *
 * <p>{@link #QUEUED} and {@link #RUNNING} are active states. {@link #DONE}, {@link #CANCELLED} and
 * {@link #FAILED} are final; a scan in a final state never changes again.
 */
public enum ScanStatus {

    /**
     * The scan waits for its turn or collects the chunks of its world; no tiles are planned yet.
     */
    QUEUED,

    /**
     * The tiles are planned and are being processed.
     */
    RUNNING,

    /**
     * Every planned tile was processed. Individual tiles may have failed, see {@link ScanJob#failures()}.
     */
    DONE,

    /**
     * The scan was cancelled through {@link Scans#cancelAll()}.
     */
    CANCELLED,

    /**
     * The scan could not be carried out because its world was not loaded when processing began.
     */
    FAILED
}
