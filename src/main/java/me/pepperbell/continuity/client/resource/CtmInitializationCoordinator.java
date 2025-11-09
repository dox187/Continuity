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
     * State machine for CTM initialization lifecycle.
     * 
     * IDLE → Load starts (either eager or from reload event) LOADING_PROPERTIES → Async loading in
     * progress (reload path) PROPERTIES_LOADED → Extension ready for use ATLAS_PROCESSING →
     * upload() method executing COMPLETE → Initialization finished
     * 
     * Initial load path: IDLE → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE Reload path: IDLE →
     * LOADING_PROPERTIES → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE → IDLE
     */
    public enum State {
        IDLE, LOADING_PROPERTIES, PROPERTIES_LOADED, ATLAS_PROCESSING, COMPLETE
    }

    private volatile State state = State.IDLE;
    private BakedModelManagerReloadExtension extension;

    /**
     * Signal that properties loading is complete. Allows atlas upload mixin to proceed.
     */
    private CompletableFuture<Void> propertiesReadyFuture = new CompletableFuture<>();

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
        this.extension = ext;
        LOGGER.debug("[Continuity] Extension set early for initial load");
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
                this.state = newState;
            }
            // Signal that properties are ready for upload()
            this.propertiesReadyFuture.complete(null);
            LOGGER.debug("[Continuity] State set to PROPERTIES_LOADED for early initialization");
        } else {
            LOGGER.warn("[Continuity] setStateEarly() called with non-PROPERTIES_LOADED state: {}",
                    newState);
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
            if (this.propertiesReadyFuture.isDone()) {
                this.propertiesReadyFuture = new CompletableFuture<>();
            }
            this.state = State.LOADING_PROPERTIES;
            LOGGER.info(
                    "[Continuity] Starting CTM properties reload from resource reload listener");
        }

        // Create the orchestrator - this triggers async property loading
        extension = new BakedModelManagerReloadExtension(manager, Runnable::run);

        // Set thread-local context for SpriteLoaderMixin
        extension.setContext();

        // Get the async properties loading future
        // When this completes, properties are ready for quad processor creation
        CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingFuture =
                extension.getCtmLoadingFuture();

        // When properties loading completes, signal that we're ready
        ctmLoadingFuture.thenRun(() -> {
            synchronized (this) {
                this.state = State.PROPERTIES_LOADED;
                this.propertiesReadyFuture.complete(null); // ← SIGNAL: Atlas upload can proceed
            }
        }).exceptionally(ex -> {
            // Handle property loading errors
            LOGGER.warn("[Continuity] Property loading failed", ex);
            this.propertiesReadyFuture.completeExceptionally(ex);
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
                return null;
            }

            // LOADING_PROPERTIES: async loading in progress, block until ready
            if (state == State.LOADING_PROPERTIES) {
                try {
                    // Wait up to 5 seconds for properties to finish loading
                    propertiesReadyFuture.get(5, TimeUnit.SECONDS);
                    LOGGER.debug("[Continuity] Properties loading completed");
                } catch (java.util.concurrent.TimeoutException e) {
                    LOGGER.warn("[Continuity] CTM properties loading timed out after 5 seconds");
                    return null;
                } catch (InterruptedException e) {
                    LOGGER.warn("[Continuity] Interrupted waiting for CTM properties");
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception e) {
                    LOGGER.error("[Continuity] Error waiting for CTM properties", e);
                    return null;
                }
            }

            // PROPERTIES_LOADED or later: extension is ready immediately
            if (extension != null) {
                LOGGER.debug("[Continuity] Returning extension, current state: {}", state);
                return extension;
            }

            // Should not reach here
            LOGGER.warn("[Continuity] Extension is null despite state being {}", state);
            return null;
        }
    }

    /**
     * Called after atlas processing completes to mark initialization as done.
     */
    public void markComplete() {
        synchronized (this) {
            this.state = State.COMPLETE;
            LOGGER.debug("[Continuity] CTM initialization COMPLETE");
        }
    }

    /**
     * Reset coordinator state between reload cycles.
     * 
     * Called after a reload completes to return to initial state. Allows subsequent reloads to
     * start fresh.
     */
    public void reset() {
        synchronized (this) {
            this.state = State.IDLE;
            this.extension = null;
            // Complete any pending futures
            if (!propertiesReadyFuture.isDone()) {
                propertiesReadyFuture.complete(null);
            }
            this.propertiesReadyFuture = new CompletableFuture<>();
            LOGGER.debug("[Continuity] Coordinator reset to IDLE state");
        }
    }

    /**
     * Get current state for debugging/testing.
     */
    public State getState() {
        synchronized (this) {
            return state;
        }
    }

    public synchronized BakedModelManagerReloadExtension getExtension() {
        return extension;
    }
}
