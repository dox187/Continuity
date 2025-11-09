package me.pepperbell.continuity.client.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.resource.CustomBlockLayers;
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;

@Mixin(RenderLayers.class)
abstract class RenderLayersMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Continuity/RenderLayers");

	@Inject(method = "getBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/BlockRenderLayer;",
			at = @At("HEAD"), cancellable = true)
	private static void continuity$onHeadGetBlockLayer(BlockState state,
			CallbackInfoReturnable<BlockRenderLayer> cir) {
		if (!CustomBlockLayers.isEmpty() && ContinuityConfig.INSTANCE.customBlockLayers.get()) {
			BlockRenderLayer layer = CustomBlockLayers.getLayer(state);
			if (layer != null) {
				LOGGER.debug("[Continuity] RENDER LAYER SELECTION:");
				LOGGER.debug("  Block: {}", state.getBlock());
				LOGGER.debug("  Custom layer: {}", layer);
				cir.setReturnValue(layer);
			}
		}
	}

	@Inject(method = "getMovingBlockLayer(Lnet/minecraft/block/BlockState;)Lnet/minecraft/client/render/RenderLayer;",
			at = @At("HEAD"), cancellable = true)
	private static void continuity$onHeadGetMovingBlockLayer(BlockState state,
			CallbackInfoReturnable<RenderLayer> cir) {
		if (!CustomBlockLayers.isEmpty() && ContinuityConfig.INSTANCE.customBlockLayers.get()) {
			BlockRenderLayer layer = CustomBlockLayers.getLayer(state);
			if (layer != null) {
				cir.setReturnValue(RenderLayerHelper.getMovingBlockLayer(layer));
			}
		}
	}
}
