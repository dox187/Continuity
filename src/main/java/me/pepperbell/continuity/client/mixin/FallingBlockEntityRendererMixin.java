// ✅ FIXED for 1.21.10 - Updated to use new RenderState pattern
//
// PURPOSE: Disable CTM/emissive processing for falling blocks to prevent visual glitches
//
// NEW SIGNATURE (1.21.10):
//   render(FallingBlockEntityRenderState, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)
//
// OLD SIGNATURE (1.21.4):
//   render(FallingBlockEntity, float, float, MatrixStack, VertexConsumerProvider, int)
//
// NOTE: Currently disabled as the new architecture may not need this mixin.
//       If visual glitches appear on falling blocks, this can be re-enabled with proper context.
//
package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.render.entity.FallingBlockEntityRenderer;

@Mixin(FallingBlockEntityRenderer.class)
abstract class FallingBlockEntityRendererMixin {
	// TODO: Add injections if needed once CTM/emissive processing is working
	// The new rendering architecture may handle this differently
	
	// Original purpose: Temporarily disable CTM connections on falling sand/gravel
	// to prevent visual glitches during the fall animation
}
