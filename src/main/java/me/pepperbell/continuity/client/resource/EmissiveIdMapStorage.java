package me.pepperbell.continuity.client.resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

/**
 * PHASE 8: Thread-safe storage for emissive ID mappings.
 * 
 * <p>
 * Stores mappings from base sprite IDs to their emissive variants (e.g., "block/stone" ->
 * "block/stone_e"). This storage is accessed from multiple worker threads during atlas loading, so
 * it uses ConcurrentHashMap for thread safety.
 * 
 * <p>
 * This is separate from AtlasLoaderMixin because mixins cannot have public static methods.
 */
public final class EmissiveIdMapStorage {
    private static final Map<Identifier, Map<Identifier, Identifier>> emissiveIdMaps =
            new ConcurrentHashMap<>();

    private EmissiveIdMapStorage() {
        // Utility class
    }

    /**
     * Stores an emissive ID map for a specific atlas.
     * 
     * @param atlasId The atlas identifier
     * @param emissiveIdMap The map of base sprite IDs to emissive sprite IDs
     */
    public static void put(Identifier atlasId, Map<Identifier, Identifier> emissiveIdMap) {
        emissiveIdMaps.put(atlasId, emissiveIdMap);
    }

    /**
     * Retrieves the emissive ID map for a specific atlas.
     * 
     * @param atlasId The atlas identifier
     * @return The emissive ID map, or null if not present
     */
    @Nullable
    public static Map<Identifier, Identifier> get(Identifier atlasId) {
        return emissiveIdMaps.get(atlasId);
    }

    /**
     * Clears all stored emissive ID maps. Called during resource reload.
     */
    public static void clear() {
        emissiveIdMaps.clear();
    }
}
