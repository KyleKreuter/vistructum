package de.kylekreuter.vistructum.api;

/**
 * Review state criterion of a {@link FindingQuery}.
 */
public enum ReviewState {

    /**
     * Matches every finding regardless of its verdict.
     */
    ANY,

    /**
     * Matches findings without a verdict, see {@link Finding#open()}.
     */
    OPEN,

    /**
     * Matches findings with a verdict.
     */
    REVIEWED
}
