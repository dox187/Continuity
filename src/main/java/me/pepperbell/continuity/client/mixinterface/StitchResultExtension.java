package me.pepperbell.continuity.client.mixinterface;

import java.util.Map;

import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

/**
 * Mixin accessor interface for SpriteLoader.StitchResult.
 * 
 * Provides access to the sprites map from the StitchResult record. In Minecraft 1.21.10,
 * StitchResult is a record with a 'sprites' component.
 */
public interface StitchResultExtension {
    /**
     * Gets the sprite map from the StitchResult.
     * 
     * @return Map of sprite identifiers to sprite instances
     */
    Map<Identifier, Sprite> continuity$getSprites();
}
