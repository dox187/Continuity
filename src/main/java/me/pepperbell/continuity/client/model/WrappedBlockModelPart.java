package me.pepperbell.continuity.client.model;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for BlockModelPart that allows modification of quads.
 * This enables CTM and emissive processing to continue working with the new model system.
 * 
 * @since 1.21.10 migration
 */
public abstract class WrappedBlockModelPart implements BlockModelPart {
	protected final BlockModelPart wrapped;

	public WrappedBlockModelPart(BlockModelPart wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public List<BakedQuad> getQuads(@Nullable Direction side) {
		List<BakedQuad> originalQuads = wrapped.getQuads(side);
		
		// Allow subclasses to process/modify quads
		return processQuads(originalQuads, side);
	}

	/**
	 * Process and potentially modify the quads from the wrapped part.
	 * Default implementation returns quads unchanged.
	 * 
	 * @param quads The original quads from the wrapped part
	 * @param side The face direction (null for non-culled quads)
	 * @return The processed quads (may be same list, modified list, or new list)
	 */
	protected List<BakedQuad> processQuads(List<BakedQuad> quads, @Nullable Direction side) {
		// Default: return unchanged
		// Subclasses override to modify quads (e.g., add emissive variants, apply CTM)
		return quads;
	}

	@Override
	public boolean useAmbientOcclusion() {
		return wrapped.useAmbientOcclusion();
	}

	@Override
	public Sprite particleSprite() {
		return wrapped.particleSprite();
	}

	/**
	 * Gets the wrapped BlockModelPart.
	 */
	public BlockModelPart getWrapped() {
		return wrapped;
	}
}
