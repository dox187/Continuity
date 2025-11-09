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
    private ResourceManager lastResourceManager;

    /**
     * Signal that properties loading is complete. Allows atlas upload mixin to proceed.
     */
    private CompletableFuture<Void> propertiesReady = new CompletableFuture<>();

    private static final long TIMEOUT_SECONDS = 5L;

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
                propertiesReady.complete(null);
            }
        } else {
            LOGGER.warn("[Continuity/Coordinator] setStateEarly() called with invalid state: {}",
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
            propertiesReady = new CompletableFuture<>();
            setState(State.LOADING_PROPERTIES);
            lastResourceManager = manager;
        }

        extension = new BakedModelManagerReloadExtension(manager, Runnable::run);
        extension.setContext();

        // Get the async properties loading future
        // When this completes, properties are ready for quad processor creation
        CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingFuture =
                extension.getCtmLoadingFuture();

        ctmLoadingFuture.thenRun(() -> {
            synchronized (this) {
                setState(State.PROPERTIES_LOADED);
                propertiesReady.complete(null);
            }
        }).exceptionally(ex -> {
            LOGGER.warn("[Continuity/Coordinator] Property loading failed", ex);
            propertiesReady.completeExceptionally(ex);
            return null;
        });
    }

    public void startReloadEarly(ResourceManager manager) {
        synchronized (this) {
            if (state != State.IDLE) {
                return;
            }

            propertiesReady = new CompletableFuture<>();
            setState(State.LOADING_PROPERTIES);
            lastResourceManager = manager;
        }

        extension = new BakedModelManagerReloadExtension(manager, Runnable::run);
        extension.setContext();

        CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingFuture =
                extension.getCtmLoadingFuture();

        ctmLoadingFuture.thenRun(() -> {
            synchronized (this) {
                setState(State.PROPERTIES_LOADED);
                propertiesReady.complete(null);
            }
        }).exceptionally(ex -> {
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
            if (state == State.IDLE) {
                return null;
            }

            if (state.ordinal() >= State.ATLAS_PROCESSING.ordinal()) {
                setState(State.ATLAS_PROCESSING);
                return extension;
            }

            if (state == State.PROPERTIES_LOADED && extension != null) {
                setState(State.ATLAS_PROCESSING);
                return extension;
            }
        }

        try {
            propertiesReady.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        } catch (java.util.concurrent.TimeoutException ex) {
            LOGGER.error(
                    "[Continuity/Coordinator] Timeout waiting for properties! CTM will not work.");
            throw new RuntimeException(
                    "CTM properties loading timeout after " + TIMEOUT_SECONDS + "s", ex);

        } catch (java.util.concurrent.ExecutionException ex) {
            LOGGER.error("[Continuity/Coordinator] Property loading failed", ex.getCause());
            throw new RuntimeException("CTM property loading failed: " + ex.getCause().getMessage(),
                    ex.getCause());

        } catch (InterruptedException ex) {
            LOGGER.error(
                    "[Continuity/Coordinator] Thread interrupted while waiting for properties");
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
        }
    }

    /**
     * Resets coordinator state for resource reload. Called at the start of each new reload cycle.
     */
    public void reset() {
        synchronized (this) {
            setState(State.IDLE);
            extension = null;
            if (!propertiesReady.isDone()) {
                propertiesReady.complete(null);
            }
            propertiesReady = new CompletableFuture<>();
        }
    }

    // ===== STATE MANAGEMENT =====

    private synchronized void setState(State newState) {
        state = newState;
    }

    /**
     * Set ResourceManager for initial load synchronous property loading.
     * 
     * Called from AtlasLoaderMixin BEFORE atlas creation to ensure ResourceManager is available
     * when SpriteAtlasTextureMixin.onUpload() needs to load properties synchronously.
     * 
     * This is safe to call multiple times (will store the most recent ResourceManager).
     * 
     * @param resourceManager The resource manager from AtlasLoader.loadSources()
     */
    public void setResourceManagerForInitialLoad(ResourceManager resourceManager) {
        synchronized (this) {
            lastResourceManager = resourceManager;
        }
    }

    /**
     * Synchronously load CTM properties for initial load scenario.
     * 
     * Called from SpriteAtlasTextureMixin when extension is null during initial atlas upload. This
     * bypasses the async reload listener and loads properties synchronously.
     * 
     * The lastResourceManager is stored when startReload() is called. If reload listener never
     * fired yet (IDLE state), we use that ResourceManager here.
     * 
     * WARNING: This is a blocking call that may take 100-500ms. Should only be used during initial
     * load when we have no choice.
     * 
     * @return true if synchronous loading succeeded, false if no ResourceManager available
     */
    public boolean loadPropertiesSynchronously() {
        synchronized (this) {
            if (state != State.IDLE) {
                return false;
            }

            if (lastResourceManager == null) {
                LOGGER.warn(
                        "[Continuity/Coordinator] No ResourceManager available for synchronous loading");
                return false;
            }

            setState(State.LOADING_PROPERTIES);
            extension = new BakedModelManagerReloadExtension(lastResourceManager, Runnable::run);
        }

        extension.setContext();

        try {
            extension.beforeBake(java.util.Collections.emptyMap(), null);
            extension.apply();

            synchronized (this) {
                setState(State.PROPERTIES_LOADED);
                propertiesReady.complete(null);
            }
            return true;

        } catch (Exception ex) {
            LOGGER.error("[Continuity/Coordinator] Synchronous property loading failed", ex);
            synchronized (this) {
                propertiesReady.completeExceptionally(ex);
            }
            return false;
        }
    }

    // ===== GETTERS (for monitoring/debugging) =====

    public synchronized State getCurrentState() {
        return state;
    }

    public synchronized BakedModelManagerReloadExtension getExtension() {
        return extension;
    }
}
