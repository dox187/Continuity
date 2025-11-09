package me.pepperbell.continuity.client.resource;

import me.pepperbell.continuity.client.ContinuityClient;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

/**
 * Resource reload listener that initializes CTM properties loading. This replaces the functionality
 * of the deleted BakedModelManagerReloadHandler.
 * 
 * <p>
 * This listener uses {@link CtmInitializationCoordinator} to orchestrate the entire CTM
 * initialization pipeline:
 * <ul>
 * <li>Resets coordinator state for new reload cycle</li>
 * <li>Creates {@link BakedModelManagerReloadExtension} instance</li>
 * <li>Loads CTM properties from resource packs (async)</li>
 * <li>Sets up SpriteLoaderLoadContext thread-local</li>
 * <li>Signals when properties are ready for quad processor creation</li>
 * </ul>
 * 
 * <p>
 * The coordinator pattern solves a race condition where sprite atlas upload can happen BEFORE
 * resource reload listener completes. The coordinator blocks atlas upload until properties are
 * loaded, ensuring quad processors are registered correctly.
 * 
 * <p>
 * Uses new Fabric Resource API (fabric-resource-loader-v1) via ResourceLoader and
 * SynchronousResourceReloader (Minecraft vanilla interface, not deprecated).
 * 
 * <p>
 * Without this listener, CTM properties are never loaded and connected textures cannot function.
 */
public class CtmResourceReloadListener implements SynchronousResourceReloader {
    public static final Identifier ID = ContinuityClient.asId("ctm_properties");
    private static final CtmResourceReloadListener INSTANCE = new CtmResourceReloadListener();

    /**
     * Registers this listener with Fabric's new ResourceLoader API. Must be called during mod
     * initialization.
     */
    public static void init() {
        ContinuityClient.LOGGER
                .info("[Continuity] Registering CTM resource reloader with new API...");

        ResourceLoader resourceLoader = ResourceLoader.get(ResourceType.CLIENT_RESOURCES);

        // Register the reloader
        resourceLoader.registerReloader(ID, INSTANCE);

        // Set ordering: Run AFTER models are loaded (so models are available when CTM processes)
        resourceLoader.addReloaderOrdering(ResourceReloaderKeys.Client.MODELS, ID);

        ContinuityClient.LOGGER
                .info("[Continuity] CtmResourceReloadListener registered with ResourceLoader");
    }

    @Override
    public void reload(ResourceManager manager) {
        ContinuityClient.LOGGER.info(
                "[Continuity] CtmResourceReloadListener.reload() - starting CTM initialization");

        // Get coordinator instance
        CtmInitializationCoordinator coordinator = CtmInitializationCoordinator.getInstance();

        // Reset coordinator for new reload cycle
        coordinator.reset();

        // Start new initialization sequence (creates extension, starts async property loading)
        coordinator.startReload(manager);

        ContinuityClient.LOGGER.info(
                "[Continuity] CtmResourceReloadListener.reload() - CTM initialization started");
    }
}
