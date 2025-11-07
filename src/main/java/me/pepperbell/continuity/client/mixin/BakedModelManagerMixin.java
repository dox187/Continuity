package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import me.pepperbell.continuity.client.mixinterface.AtlasManagerAccess;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.texture.AtlasManager;

/**
 * Mixin to expose the private AtlasManager field from BakedModelManager.
 * In 1.21.10, BakedModelManager has field_61870 (AtlasManager) but no public getter.
 * We need access to it for sprite lookups.
 */
@Mixin(value = BakedModelManager.class, priority = 900)
abstract class BakedModelManagerMixin implements AtlasManagerAccess {
	@Shadow
	@Final
	private AtlasManager field_61870; // AtlasManager atlasManager

	@Override
	public AtlasManager continuity$getAtlasManager() {
		return field_61870;
	}
}
