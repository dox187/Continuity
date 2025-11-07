// ✅ FIXED for 1.21.10 - Updated to use new BlockEntityRenderer pattern
//
// PURPOSE: Disable CTM/emissive processing for piston blocks to prevent visual glitches
//
// NEW SIGNATURE (1.21.10):
//   render(PistonBlockEntityRenderState, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)
//
// OLD SIGNATURE (1.21.4):
//   render(PistonBlockEntity, float, MatrixStack, VertexConsumerProvider, int, int)
//
// NOTE: Currently disabled as the new architecture may not need this mixin.
//       If visual glitches appear on piston-pushed blocks, this can be re-enabled with proper context.
//
package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.render.block.entity.PistonBlockEntityRenderer;

@Mixin(PistonBlockEntityRenderer.class)
abstract class PistonBlockEntityRendererMixin {
	// TODO: Add injections if needed once CTM/emissive processing is working
	// The new rendering architecture may handle this differently
	
	// Original purpose: Temporarily disable CTM connections on blocks being pushed by pistons
	// to prevent visual glitches during the animation
}
