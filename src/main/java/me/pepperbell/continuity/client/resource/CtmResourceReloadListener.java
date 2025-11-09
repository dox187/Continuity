package me.pepperbell.continuity.client.resource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import me.pepperbell.continuity.client.ContinuityClient;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * Resource reload listener that initializes CTM properties loading. This replaces the functionality
 * of the deleted BakedModelManagerReloadHandler.
 * 
 * <p>
 * This listener creates a {@link BakedModelManagerReloadExtension} instance, which orchestrates the
 * entire CTM initialization pipeline:
 * <ul>
 * <li>Loads CTM properties from resource packs</li>
 * <li>Sets up SpriteLoaderLoadContext thread-local</li>
 * <li>Creates quad processors</li>
 * <li>Registers processors with ModelWrappingHandler</li>
 * </ul>
 * 
 * <p>
 * Without this listener, CTM properties are never loaded and connected textures cannot function.
 */
public class CtmResourceReloadListener implements SimpleSynchronousResourceReloadListener {
    public static final Identifier ID = ContinuityClient.asId("ctm_properties");
    public static final List<Identifier> DEPENDENCIES = List.of(ResourceReloadListenerKeys.MODELS);
    private static final CtmResourceReloadListener INSTANCE = new CtmResourceReloadListener();

    /**
     * Registers this listener with Fabric's ResourceManagerHelper. Must be called during mod
     * initialization.
     */
    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(INSTANCE);
        ContinuityClient.LOGGER.info("[Continuity] CtmResourceReloadListener registered");
    }

    @Override
    public void reload(ResourceManager manager) {
        ContinuityClient.LOGGER.info(
                "[Continuity] CtmResourceReloadListener.reload() - starting CTM initialization");

        // Create executor for async property loading
        // Using direct executor for now to simplify - properties load synchronously
        Executor prepareExecutor = Runnable::run;

        // Create the orchestrator - this triggers CTM properties loading
        BakedModelManagerReloadExtension extension =
                new BakedModelManagerReloadExtension(manager, prepareExecutor);

        ContinuityClient.LOGGER.info("[Continuity] BakedModelManagerReloadExtension created");

        // Set up thread-local context for SpriteLoaderMixin
        extension.setContext();

        ContinuityClient.LOGGER.info("[Continuity] SpriteLoaderLoadContext thread-local set");

        // Note: beforeBake() and apply() will be called by SpriteAtlasTextureMixin
        // during atlas preparation, which happens after this reload completes.
        // The extension must be stored somewhere accessible to the mixin.

        // Store extension for later use by mixins
        BakedModelManagerReloadExtensionHolder.set(extension);

        ContinuityClient.LOGGER.info(
                "[Continuity] CtmResourceReloadListener.reload() - CTM initialization complete");
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public List<Identifier> getFabricDependencies() {
        return DEPENDENCIES;
    }

    /**
     * Holder for BakedModelManagerReloadExtension instance. Allows mixins to access the extension
     * created during resource reload.
     */
    public static class BakedModelManagerReloadExtensionHolder {
        private static volatile BakedModelManagerReloadExtension extension;

        public static void set(BakedModelManagerReloadExtension ext) {
            extension = ext;
        }

        public static BakedModelManagerReloadExtension get() {
            return extension;
        }

        public static void clear() {
            extension = null;
        }
    }
}
