/**
 * Public API of Vistructum, the detection of prohibited symbols built in Minecraft worlds.
 *
 * <p>This package is the only supported integration surface. Consumers compile against the
 * {@code vistructum-api} artifact with scope {@code provided}, declare {@code depend: [vistructum]} in their
 * {@code plugin.yml} and obtain the API through {@link de.kylekreuter.vistructum.api.Vistructum#get()}. The classes
 * are supplied at runtime by the core plugin.
 *
 * <p>The package comprises three groups of types:
 * <ul>
 *   <li>Service interfaces: {@link de.kylekreuter.vistructum.api.Vistructum},
 *       {@link de.kylekreuter.vistructum.api.Findings}, {@link de.kylekreuter.vistructum.api.Scans} and
 *       {@link de.kylekreuter.vistructum.api.Page}. Their asynchronous results complete on the server main
 *       thread.</li>
 *   <li>Events, all fired synchronously on the server main thread:
 *       {@link de.kylekreuter.vistructum.api.FindingCreateEvent},
 *       {@link de.kylekreuter.vistructum.api.FindingCreatedEvent},
 *       {@link de.kylekreuter.vistructum.api.FindingReviewedEvent},
 *       {@link de.kylekreuter.vistructum.api.ScanStartedEvent},
 *       {@link de.kylekreuter.vistructum.api.ScanProgressEvent} and
 *       {@link de.kylekreuter.vistructum.api.ScanFinishedEvent}.</li>
 *   <li>Immutable value types, such as {@link de.kylekreuter.vistructum.api.Finding},
 *       {@link de.kylekreuter.vistructum.api.ScanJob} and {@link de.kylekreuter.vistructum.api.FindingQuery}.</li>
 * </ul>
 *
 * <p>No type in this package accepts {@code null} unless its documentation states otherwise.
 */
package de.kylekreuter.vistructum.api;
