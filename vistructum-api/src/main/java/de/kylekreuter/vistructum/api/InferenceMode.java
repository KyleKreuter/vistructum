package de.kylekreuter.vistructum.api;

/**
 * Place where the core plugin runs model inference.
 */
public enum InferenceMode {

    /**
     * Inference runs inside the server process of the core plugin.
     */
    LOCAL,

    /**
     * Inference runs in a separate sidecar process that the core plugin reaches over HTTP.
     */
    REMOTE
}
