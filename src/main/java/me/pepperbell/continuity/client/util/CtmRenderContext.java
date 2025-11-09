package me.pepperbell.continuity.client.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;

/**
 * ThreadLocal storage for CTM rendering context.
 *
 * This allows BlockModelPart.getQuads() to access world context during rendering, even though
 * BlockStateModel.addParts() doesn't have access to it at bake time.
 *
 * The context is set by BlockModelRendererMixin at the start of render() and cleared at the end.
 */
public class CtmRenderContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    /**
     * Stores the rendering context for the current block being rendered.
     *
     * @param world The world containing the block
     * @param pos The position of the block
     * @param state The block state being rendered
     */
    public record Context(BlockRenderView world, BlockPos pos, BlockState state) {
    }

    /**
     * Set the current rendering context. Called by BlockModelRendererMixin at the start of
     * render().
     */
    public static void set(BlockRenderView world, BlockPos pos, BlockState state) {
        CURRENT.set(new Context(world, pos, state));
    }

    /**
     * Get the current rendering context, or null if not currently rendering. Called by
     * CtmBlockModelPart.getQuads() to access world context.
     *
     * @return The current context, or null if not in a rendering context
     */
    @Nullable
    public static Context get() {
        return CURRENT.get();
    }

    /**
     * Clear the current rendering context. Called by BlockModelRendererMixin at the end of
     * render().
     */
    public static void clear() {
        CURRENT.remove();
    }
}
