package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.resource.CustomBlockLayers;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;

@Mixin(RenderLayers.class)
abstract class RenderLayersMixin {
	@Inject(
		method = "getBlockLayer",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void onGetBlockLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
		if (ContinuityConfig.INSTANCE.customBlockLayers.get()) {
			if (!CustomBlockLayers.isEmpty()) {
				RenderLayer customLayer = CustomBlockLayers.getLayer(state);
				if (customLayer != null) {
					// Convert RenderLayer to BlockRenderLayer
					// Note: This is a simplified mapping - may need refinement
					if (customLayer == RenderLayer.getSolid()) {
						cir.setReturnValue(BlockRenderLayer.SOLID);
					} else if (customLayer == RenderLayer.getCutout()) {
						cir.setReturnValue(BlockRenderLayer.CUTOUT);
					} else if (customLayer == RenderLayer.getCutoutMipped()) {
						cir.setReturnValue(BlockRenderLayer.CUTOUT_MIPPED);
					}
					// TRANSLUCENT would go here when we find the proper layer
				}
			}
		}
	}
}
