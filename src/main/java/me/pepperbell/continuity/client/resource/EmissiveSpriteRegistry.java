package me.pepperbell.continuity.client.resource;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.Identifier;

/**
 * Global registry for emissive sprite mappings. Populated during atlas loading by AtlasLoaderMixin.
 * Accessed by EmissiveTextureManager and EmissiveBlockModelPart.
 */
public class EmissiveSpriteRegistry {
    private static final Map<Identifier, Identifier> EMISSIVE_MAPPING = new ConcurrentHashMap<>();
    private static volatile boolean hasEmissives = false;

    /**
     * Set the complete emissive sprite mapping (called during atlas loading).
     */
    public static void setEmissiveMapping(Map<Identifier, Identifier> mapping) {
        EMISSIVE_MAPPING.clear();
        if (mapping != null && !mapping.isEmpty()) {
            EMISSIVE_MAPPING.putAll(mapping);
            hasEmissives = true;
            me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(
                    me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
                            + "EmissiveSpriteRegistry.setEmissiveMapping() called with {} entries",
                    mapping.size());
        } else {
            hasEmissives = false;
            me.pepperbell.continuity.client.ContinuityClient.LOGGER
                    .debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
                            + "EmissiveSpriteRegistry.setEmissiveMapping() called with null or empty mapping");
        }
    }

    /**
     * Get the emissive variant ID for a given base sprite ID. Returns null if no emissive variant
     * exists.
     */
    public static Identifier getEmissiveVariant(Identifier baseId) {
        return EMISSIVE_MAPPING.get(baseId);
    }

    /**
     * Get all emissive sprite mappings.
     */
    public static Map<Identifier, Identifier> getEmissiveMapping() {
        return Collections.unmodifiableMap(EMISSIVE_MAPPING);
    }

    /**
     * Check if there are any emissive sprites registered.
     */
    public static boolean hasEmissives() {
        return hasEmissives;
    }

    /**
     * Clear all emissive sprite mappings (usually called before reloading).
     */
    public static void clear() {
        EMISSIVE_MAPPING.clear();
        hasEmissives = false;
    }
}
