package me.pepperbell.continuity.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.pepperbell.continuity.client.ContinuityClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
	
	// Also hook the static method to see if it's used (for items/entities)
	@Inject(
		method = "render(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/render/model/BlockStateModel;FFFII)V", 
		at = @At("HEAD")
	)
	private static void continuity$beforeStaticRender(
		MatrixStack.Entry entry,
		VertexConsumer vertexConsumer,
		net.minecraft.client.render.model.BlockStateModel model,
		float red, float green, float blue,
		int light, int overlay,
		CallbackInfo ci
	) {
		ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX + 
			"STATIC render() called! Model: {} (no world context)", model.getClass().getSimpleName());
	}
}
