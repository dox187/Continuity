package me.pepperbell.continuity.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import me.pepperbell.continuity.client.mixinterface.StitchResultExtension;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.util.Identifier;

/**
 * Mixin for SpriteLoader.StitchResult to provide access to sprites map.
 * 
 * In Minecraft 1.21.10, StitchResult is a record with a 'sprites' component. This mixin implements
 * StitchResultExtension to expose the sprites map.
 */
@Mixin(SpriteLoader.StitchResult.class)
abstract class StitchResultMixin implements StitchResultExtension {
    @Shadow
    @Final
    private Map<Identifier, Sprite> sprites;

    @Override
    public Map<Identifier, Sprite> continuity$getSprites() {
        return this.sprites;
    }
}
