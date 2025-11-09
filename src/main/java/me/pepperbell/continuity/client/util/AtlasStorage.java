package me.pepperbell.continuity.client.util;

import net.minecraft.client.texture.SpriteAtlasTexture;

/**
 * Utility class to store static reference to the block atlas texture.
 * 
 * This class exists to circumvent a Mixin framework limitation: Mixins cannot contain public static
 * methods because they would pollute the target class's API.
 * 
 * Instead of storing the atlas reference in the mixin itself, we store it here and provide safe
 * public access. The mixin calls the package-private setter, and external code uses the public
 * getter.
 * 
 * Thread Safety: The blockAtlas field is volatile to ensure visibility across threads (atlas is set
 * during resource loading, read during rendering on different threads).
 * 
 * See PHASE4_RUNTIME_ERROR.md and MIXIN_RULES_AND_GOTCHAS.md for full rationale.
 */
public final class AtlasStorage {
    private static volatile SpriteAtlasTexture blockAtlas;

    private AtlasStorage() {
        // Utility class - no instantiation
    }

    /**
     * Gets the block atlas texture. Used by RenderUtil to create sprite finders.
     * 
     * @return The block atlas texture, or null if not yet initialized
     */
    public static SpriteAtlasTexture getBlockAtlas() {
        return blockAtlas;
    }

    /**
     * Sets the block atlas texture. Called by SpriteAtlasTextureMixin during atlas upload. Public
     * to allow access from mixin package.
     * 
     * @param atlas The block atlas texture to store
     */
    public static void setBlockAtlas(SpriteAtlasTexture atlas) {
        blockAtlas = atlas;
    }
}
