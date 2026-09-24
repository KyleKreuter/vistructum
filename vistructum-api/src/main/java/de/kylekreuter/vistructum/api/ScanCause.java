package de.kylekreuter.vistructum.api;

/**
 * Trigger of a {@link ScanJob}.
 */
public enum ScanCause {

    /**
     * The scan was requested through {@link Scans#request(String)}.
     */
    MANUAL,

    /**
     * The scan was queued by the daily schedule configured in the core plugin.
     */
    DAILY
}
