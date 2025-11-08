package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.pepperbell.continuity.client.resource.ModelWrappingHandler;
import me.pepperbell.continuity.client.util.SpriteCalculator;
import net.minecraft.client.render.block.BlockModels;

@Mixin(BlockModels.class)
abstract class BlockModelsMixin {
	@Inject(method = "setModels(Ljava/util/Map;)V", at = @At("HEAD"))
	private void continuity$onHeadSetModels(CallbackInfo ci) {
		SpriteCalculator.clearCache();

		// *** FIX: Load CTM properties synchronously BEFORE model wrapping ***
		// The CTMResourceReloadListener runs too late (after models are set)
		// So we load CTM data here to ensure it's ready for wrapping
		// COMMENTED OUT - Debug level frequency
		// me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
		// me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX +
		// "setModels() HEAD - preparing CTM system");

		// Trigger CTM loading if not already done
		// TODO: This is a workaround - ideally CTMResourceReloadListener would run earlier
		try {
			me.pepperbell.continuity.client.resource.CTMResourceReloadListener.INSTANCE.reload(
					net.minecraft.client.MinecraftClient.getInstance().getResourceManager());
		} catch (Exception e) {
			// COMMENTED OUT - Error level high frequency
			// me.pepperbell.continuity.client.ContinuityClient.LOGGER.error(
			// me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX +
			// "Failed to reload CTM during setModels", e);
		}
	}

	@Inject(method = "setModels(Ljava/util/Map;)V", at = @At("TAIL"))
	private void continuity$onTailSetModels(CallbackInfo ci) {
		ModelWrappingHandler.wrapModels((BlockModels) (Object) this);
	}
}
