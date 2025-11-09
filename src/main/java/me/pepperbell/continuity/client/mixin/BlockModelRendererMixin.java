package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.pepperbell.continuity.client.util.CtmRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

/**
 * Mixin to BlockModelRenderer that sets up the CTM rendering context before rendering blocks. This
 * allows CtmBlockModelPart.getQuads() to access world context when Sodium bypasses the Fabric
 * Renderer API.
 * 
 * Uses intermediary mappings since BlockModelRenderer methods are called by Sodium which uses
 * intermediary names.
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {

    /**
     * Inject at the HEAD of renderSmooth() (intermediary: method_68573) to set up CTM context. Full
     * signature includes: world, parts list, state, pos, matrix stack, vertex consumer, smooth
     * shading, overlay
     */
    @Inject(method = "method_68573", at = @At("HEAD"), remap = false)
    private void continuity$beforeRenderSmooth(BlockRenderView world,
            @SuppressWarnings("unused") java.util.List<?> parts, BlockState state, BlockPos pos,
            @SuppressWarnings("unused") MatrixStack matrixStack,
            @SuppressWarnings("unused") VertexConsumer vertexConsumer,
            @SuppressWarnings("unused") boolean smoothShading,
            @SuppressWarnings("unused") int overlay, CallbackInfo ci) {
        CtmRenderContext.set(world, pos, state);
    }

    /**
     * Inject at the RETURN of renderSmooth() to clean up CTM context.
     */
    @Inject(method = "method_68573", at = @At("RETURN"), remap = false)
    private void continuity$afterRenderSmooth(CallbackInfo ci) {
        CtmRenderContext.clear();
    }

    /**
     * Inject at the HEAD of renderFlat() (intermediary: method_68572) to set up CTM context.
     */
    @Inject(method = "method_68572", at = @At("HEAD"), remap = false)
    private void continuity$beforeRenderFlat(BlockRenderView world,
            @SuppressWarnings("unused") java.util.List<?> parts, BlockState state, BlockPos pos,
            @SuppressWarnings("unused") MatrixStack matrixStack,
            @SuppressWarnings("unused") VertexConsumer vertexConsumer,
            @SuppressWarnings("unused") boolean smoothShading,
            @SuppressWarnings("unused") int overlay, CallbackInfo ci) {
        CtmRenderContext.set(world, pos, state);
    }

    /**
     * Inject at the RETURN of renderFlat() to clean up CTM context.
     */
    @Inject(method = "method_68572", at = @At("RETURN"), remap = false)
    private void continuity$afterRenderFlat(CallbackInfo ci) {
        CtmRenderContext.clear();
    }
}
