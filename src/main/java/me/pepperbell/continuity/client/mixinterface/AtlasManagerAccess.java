package me.pepperbell.continuity.client.mixinterface;

import net.minecraft.client.texture.AtlasManager;

/**
 * Access interface for getting the AtlasManager from BakedModelManager.
 * In 1.21.10, BakedModelManager has a private field_61870 (AtlasManager)
 * with no public getter, so we need a mixin to expose it.
 */
public interface AtlasManagerAccess {
    /**
     * Gets the AtlasManager that manages all sprite atlases.
     * @return the AtlasManager instance
     */
    AtlasManager continuity$getAtlasManager();
}
