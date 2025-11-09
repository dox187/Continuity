package me.pepperbell.continuity.client.resource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resource.ResourceManager;

/**
 * Central coordinator for CTM initialization synchronization.
 * 
 * Coordinates between: 1. CtmResourceReloadListener.reload() - Creates extension, starts async
 * property loading 2. SpriteAtlasTextureMixin.onUpload() - Uses extension for quad processor
 * registration
 * 
 * Solves race condition: Atlas upload can happen BEFORE reload listener completes. Solution: Reload
 * listener signals when properties are ready, atlas upload blocks until ready.
 * 
 * State Machine: IDLE → LOADING_PROPERTIES → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE
 */
public class CtmInitializationCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger("Continuity/Coordinator");
    private static final CtmInitializationCoordinator INSTANCE = new CtmInitializationCoordinator();

    /**
     * Initialization state machine.
     * 
     * IDLE: No initialization started. Extension null, no properties loaded. LOADING_PROPERTIES:
     * reload() in progress. Extension created, properties loading async. PROPERTIES_LOADED: Async
     * property loading complete. Extension ready for use. ATLAS_PROCESSING: SpriteAtlasTextureMixin
     * processing. Quad processors being registered. COMPLETE: All initialization done. CTM ready
     * for rendering.
     */
    public enum State {
        IDLE, LOADING_PROPERTIES, PROPERTIES_LOADED, ATLAS_PROCESSING, COMPLETE
    }

    private State state = State.IDLE;
    private BakedModelManagerReloadExtension extension;

    /**
     * Signal that properties loading is complete. Allows atlas upload mixin to proceed.
     */
    private CompletableFuture<Void> propertiesReady = new CompletableFuture<>();

    private static final long TIMEOUT_SECONDS = 5L;
    private static final String THREAD_NAME = "[Continuity/Coordinator]";

    private CtmInitializationCoordinator() {
        // Singleton
    }

    public static CtmInitializationCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Set extension directly during eager initialization.
     * 
     * Called from ContinuityClient.onInitializeClient() after loading CTM properties synchronously.
     * This allows the extension to be available immediately when SpriteAtlasTexture.upload() fires.
     * 
     * @param ext The extension with pre-loaded properties
     */
    public void setExtensionEarly(BakedModelManagerReloadExtension ext) {
        synchronized (this) {
            this.extension = ext;
            log("Extension set early for initial load");
        }
    }

    /**
     * Set state to PROPERTIES_LOADED after eager initialization.
     * 
     * This signals that properties are ready without waiting for async loading. Used by eager
     * initialization to indicate readiness.
     * 
     * @param newState The state to set (typically PROPERTIES_LOADED)
     */
    public void setStateEarly(State newState) {
        if (newState == State.PROPERTIES_LOADED) {
            synchronized (this) {
                setState(newState);
                // Signal that properties are ready for upload()
                propertiesReady.complete(null);
                log("State set to PROPERTIES_LOADED for early initialization");
            }
        } else {
            log("WARNING: setStateEarly() called with non-PROPERTIES_LOADED state: " + newState);
        }
    }

    /**
     * Called by CtmResourceReloadListener.reload() to start the reload sequence.
     * 
     * @param manager ResourceManager for property loading
     */
    public void startReload(ResourceManager manager) {
        synchronized (this) {
            // Reset future for new reload cycle
            propertiesReady = new CompletableFuture<>();
            setState(State.LOADING_PROPERTIES);
            log("Starting CTM properties reload from resource reload listener");
        }

        // Create the orchestrator - this triggers async property loading
        extension = new BakedModelManagerReloadExtension(manager, Runnable::run);

        // Set thread-local context for SpriteLoaderMixin
        extension.setContext();
        log("Extension created, context set");

        // Get the async properties loading future
        // When this completes, properties are ready for quad processor creation
        CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingFuture =
                extension.getCtmLoadingFuture();

        // When properties loading completes, signal that we're ready
        ctmLoadingFuture.thenRun(() -> {
            synchronized (this) {
                setState(State.PROPERTIES_LOADED);
                propertiesReady.complete(null); // ← SIGNAL: Atlas upload can proceed
                log("Properties loading complete, atlas upload can proceed");
            }
        }).exceptionally(ex -> {
            // Handle property loading errors
            log("WARNING: Property loading failed: " + ex.getMessage());
            propertiesReady.completeExceptionally(ex);
            return null;
        });
    }

    /**
     * Called by SpriteAtlasTextureMixin.onUpload() to get the extension.
     * 
     * BLOCKS until properties are ready (with timeout). Ensures BakedModelManagerReloadExtension is
     * valid before use.
     * 
     * @return BakedModelManagerReloadExtension instance, or null if reload hasn't started yet
     * @throws RuntimeException if timeout occurs or properties loading failed
     */
    public BakedModelManagerReloadExtension getExtensionWhenReady() {
        synchronized (this) {
            // IDLE state: not initialized yet
            if (state == State.IDLE) {
                // Coordinator not yet initialized - happens on initial game load before reload
                // listener fires (unless eager load succeeded)
                log("DEBUG: getExtensionWhenReady() called before reload started (initial load)");
                return null;
            }

            // Already processing or complete, return immediately
            if (state.ordinal() >= State.ATLAS_PROCESSING.ordinal()) {
                setState(State.ATLAS_PROCESSING);
                log("DEBUG: Returning extension, current state: " + state);
                return extension;
            }

            // PROPERTIES_LOADED: extension is ready immediately
            if (state == State.PROPERTIES_LOADED && extension != null) {
                setState(State.ATLAS_PROCESSING);
                log("DEBUG: Returning extension, current state: " + state);
                return extension;
            }
        }

        // LOADING_PROPERTIES: async loading in progress, block until ready
        // This is the critical synchronization point
        try {
            log("Waiting for properties loading (timeout: " + TIMEOUT_SECONDS + "s)...");

            // Block until either:
            // - propertiesReady completes normally (properties loaded)
            // - propertiesReady fails (exception occurred)
            // - TIMEOUT_SECONDS passes (deadlock/timeout error)
            propertiesReady.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log("Properties ready, proceeding with quad processor registration");

        } catch (java.util.concurrent.TimeoutException ex) {
            log("ERROR: Timeout waiting for properties! CTM will not work.");
            throw new RuntimeException(
                    "CTM properties loading timeout after " + TIMEOUT_SECONDS + "s", ex);

        } catch (java.util.concurrent.ExecutionException ex) {
            log("ERROR: Property loading failed with exception: " + ex.getCause());
            throw new RuntimeException("CTM property loading failed: " + ex.getCause().getMessage(),
                    ex.getCause());

        } catch (InterruptedException ex) {
            log("ERROR: Thread interrupted while waiting for properties");
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during CTM initialization", ex);
        }

        synchronized (this) {
            setState(State.ATLAS_PROCESSING);
            return extension;
        }
    }

    /**
     * Called after atlas processing completes to mark initialization as done.
     */
    public void markComplete() {
        synchronized (this) {
            setState(State.COMPLETE);
            log("CTM initialization COMPLETE");
        }
    }

    /**
     * Resets coordinator state for resource reload. Called at the start of each new reload cycle.
     */
    public void reset() {
        synchronized (this) {
            setState(State.IDLE);
            extension = null;
            // Create new CompletableFuture for next reload
            // Complete any pending futures to prevent deadlocks
            if (!propertiesReady.isDone()) {
                propertiesReady.complete(null);
            }
            propertiesReady = new CompletableFuture<>();
            log("Coordinator reset to IDLE state");
        }
    }

    // ===== STATE MANAGEMENT =====

    private synchronized void setState(State newState) {
        if (state != newState) {
            log("State transition: " + state + " → " + newState);
            state = newState;
        }
    }

    private void log(String message) {
        LOGGER.info(THREAD_NAME + " " + message);
    }

    // ===== GETTERS (for monitoring/debugging) =====

    public synchronized State getCurrentState() {
        return state;
    }

    public synchronized BakedModelManagerReloadExtension getExtension() {
        return extension;
    }
}
