package me.pepperbell.continuity.client.resource;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.pepperbell.continuity.client.ContinuityClient;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Handles the discovery and registration of emissive sprites.
 * 
 * In 1.21.10, emissive sprites are registered via AtlasLoaderMixin during atlas loading.
 * This manager caches the emissive sprite mapping for later use by renderers.
 */
public class EmissiveTextureManager implements SimpleSynchronousResourceReloadListener {

    private final Map<Identifier, Identifier> emissiveMapping = new ConcurrentHashMap<>();

    @Override
    public Identifier getFabricId() {
        return ContinuityClient.asId("emissive_texture_manager");
    }

    @Override
    public void reload(ResourceManager manager) {
        emissiveMapping.clear();
        
        // Get emissive mappings from the context if available
        SpriteLoaderLoadContext context = SpriteLoaderLoadContext.THREAD_LOCAL.get();
        if (context != null) {
            SpriteLoaderLoadContext.EmissiveControl emissiveControl = context.getEmissiveControl(Identifier.of("minecraft", "blocks"));
            if (emissiveControl != null) {
                Map<Identifier, Identifier> emissiveIdMap = emissiveControl.getEmissiveIdMap();
                if (emissiveIdMap != null) {
                    emissiveMapping.putAll(emissiveIdMap);
                    ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX + "Loaded {} emissive sprite mappings", emissiveIdMap.size());
                    return;
                }
            }
        }
        
        ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX + "No emissive sprites registered (normal if no resource packs use emissive textures)");
    }

    /**
     * Get the emissive sprite ID for a given base sprite ID.
     * Returns null if no emissive variant exists.
     */
    public Identifier getEmissiveVariant(Identifier baseId) {
        return emissiveMapping.get(baseId);
    }

    /**
     * Get all emissive sprite mappings.
     */
    public Map<Identifier, Identifier> getEmissiveMapping() {
        return Collections.unmodifiableMap(emissiveMapping);
    }
}
