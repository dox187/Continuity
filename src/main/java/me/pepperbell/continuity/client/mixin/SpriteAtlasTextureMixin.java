package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.mixinterface.SpriteExtension;
import me.pepperbell.continuity.client.resource.EmissiveSpriteRegistry;
import me.pepperbell.continuity.client.resource.ModelWrappingCoordinator;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;

/**
 * Mixin to link emissive sprites to base sprites after the atlas is loaded. This ensures that
 * EmissiveBlockModelPart can find emissive sprites via SpriteExtension.
 */
@Mixin(SpriteAtlasTexture.class)
abstract class SpriteAtlasTextureMixin {

    @Shadow
    private Identifier id;

    /**
     * Hook into the atlas sprite loading completion. This is called after all sprites have been
     * loaded and registered in the atlas, making it safe to look them up.
     */
    @Inject(method = "upload", at = @At("TAIL"))
    private void continuity$linkEmissiveSprites(CallbackInfo ci) {
        // Only process blocks atlas - emissive textures are only in the blocks atlas
        // In 1.21.10, atlas IDs are like "minecraft:blocks"
        if (!id.equals(Identifier.of("minecraft", "blocks"))) {
            return;
        }

        // Only process if there are emissive sprites registered
        if (!EmissiveSpriteRegistry.hasEmissives()) {
            ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
                    + "SpriteAtlasTextureMixin: No emissive sprites in registry");
            return;
        }

        try {
            // Get all emissive mappings (ID to ID)
            var emissiveMapping = EmissiveSpriteRegistry.getEmissiveMapping();

            if (emissiveMapping.isEmpty()) {
                ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
                        + "SpriteAtlasTextureMixin: Empty emissive mapping");
                return;
            }

            // Get the current atlas sprite registry
            var atlas = (SpriteAtlasTexture) (Object) this;

            int linkedCount = 0;
            int failedCount = 0;

            ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
                    + "SpriteAtlasTextureMixin: Attempting to link {} emissive sprite mappings",
                    emissiveMapping.size());

            for (var entry : emissiveMapping.entrySet()) {
                Identifier baseId = entry.getKey();
                Identifier emissiveId = entry.getValue();

                try {
                    // Try to get both sprites from the atlas using reflection
                    // This is a bit hacky but necessary to access the sprite registry
                    Sprite baseSprite = getSprite(atlas, baseId);
                    Sprite emissiveSprite = getSprite(atlas, emissiveId);

                    if (baseSprite != null && emissiveSprite != null) {
                        // Link the emissive sprite to the base sprite
                        ((SpriteExtension) baseSprite).continuity$setEmissiveSprite(emissiveSprite);
                        linkedCount++;
                    } else {
                        if (baseSprite == null) {
                            ContinuityClient.LOGGER.debug(
                                    ContinuityClient.LOG_PREFIX
                                            + "Base sprite not found for emissive mapping: {}",
                                    baseId);
                        }
                        if (emissiveSprite == null) {
                            ContinuityClient.LOGGER.debug(
                                    ContinuityClient.LOG_PREFIX
                                            + "Emissive sprite not found for mapping: {} -> {}",
                                    baseId, emissiveId);
                        }
                        failedCount++;
                    }
                } catch (Exception e) {
                    ContinuityClient.LOGGER.debug(
                            ContinuityClient.LOG_PREFIX
                                    + "Failed to link emissive sprite for {}: {}",
                            baseId, e.getMessage());
                    failedCount++;
                }
            }

            ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
                    + "SpriteAtlasTextureMixin: Linked {} emissive sprites, {} failed mappings",
                    linkedCount, failedCount);

            // Notify the coordinator that blocks atlas loading is complete
            // This will trigger model wrapping if BlockModels are ready
            ModelWrappingCoordinator.onAtlasLoadingComplete();
        } catch (Exception e) {
            ContinuityClient.LOGGER
                    .error(ContinuityClient.LOG_PREFIX + "Error linking emissive sprites", e);
        }
    }

    /**
     * Helper to get a sprite from the atlas by ID. Uses reflection since Sprite registration is
     * internal to SpriteAtlasTexture.
     */
    private static Sprite getSprite(SpriteAtlasTexture atlas, Identifier spriteId) {
        try {
            // Try to access the sprite storage - this is implementation-dependent
            // For now, we'll try using getDynamicSpriteIfPresent if available
            // This is a workaround until we find a better way to access sprites
            return atlas.getSprite(spriteId);
        } catch (Exception e) {
            return null;
        }
    }
}
