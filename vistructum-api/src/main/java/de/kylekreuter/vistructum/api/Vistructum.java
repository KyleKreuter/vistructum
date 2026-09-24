package de.kylekreuter.vistructum.api;

import org.bukkit.Bukkit;

import java.util.concurrent.CompletableFuture;

/**
 * Entry point of the Vistructum API.
 *
 * <p>The Vistructum core plugin registers exactly one implementation of this interface with the Bukkit
 * {@link org.bukkit.plugin.ServicesManager} while it is enabled. The facade is divided into the areas
 * {@link #findings()} and {@link #scans()}, complemented by the aggregated {@link #status()}.
 *
 * <h2>Threading contract</h2>
 * <ul>
 *   <li>All methods may be called from any thread.</li>
 *   <li>Every {@link CompletableFuture} returned by this API, including those returned by {@link Findings},
 *       {@link Scans} and {@link Page}, completes on the server main thread. Dependent stages that are attached
 *       without an executor therefore run on the main thread and may call the Bukkit API directly.</li>
 *   <li>Storage access and sidecar requests never run on the main thread.</li>
 *   <li>Blocking on a returned future from the main thread, for instance through {@link CompletableFuture#join()}
 *       or {@link CompletableFuture#get()}, deadlocks the server, because completion is scheduled for a later
 *       tick of that same thread.</li>
 *   <li>Once the core plugin is disabled, pending futures complete on the thread that finishes the underlying
 *       work instead of the main thread.</li>
 * </ul>
 *
 * <h2>Failure contract</h2>
 * Unless stated otherwise, a returned future completes exceptionally when the underlying storage or sidecar
 * operation fails. The returned future is completed with the original cause, not with a
 * {@link java.util.concurrent.CompletionException} wrapping it.
 *
 * <h2>Lifecycle</h2>
 * The instance is bound to the enabled state of the core plugin. Consumers declare {@code depend: [vistructum]}
 * in their {@code plugin.yml} and obtain the instance through {@link #get()} during or after their own
 * {@code onEnable}. A reference must not be retained across a disable of the core plugin.
 *
 * @see Findings
 * @see Scans
 */
public interface Vistructum {

    /**
     * Returns the instance registered by the enabled core plugin.
     *
     * @return the registered API instance, never {@code null}
     * @throws IllegalStateException if the core plugin is not enabled and no instance is registered
     */
    static Vistructum get() {
        Vistructum vistructum = Bukkit.getServicesManager().load(Vistructum.class);
        if (vistructum == null) {
            throw new IllegalStateException("Vistructum is not enabled; add depend: [vistructum] to your plugin.yml");
        }
        return vistructum;
    }

    /**
     * Returns the area for reading, querying and reviewing findings.
     *
     * @return the findings area, never {@code null}; the same instance is returned on every call
     */
    Findings findings();

    /**
     * Returns the area for requesting, observing and cancelling world scans.
     *
     * @return the scans area, never {@code null}; the same instance is returned on every call
     */
    Scans scans();

    /**
     * Collects an aggregated snapshot of the operational state.
     *
     * <p>The storage-derived values are read independently of one another and do not form a single consistent
     * transaction. An unreachable or unhealthy sidecar does not fail the returned future; it is reported through
     * {@link VistructumStatus#sidecar()} instead.
     *
     * @return a future completing on the main thread with the current status
     */
    CompletableFuture<VistructumStatus> status();
}
