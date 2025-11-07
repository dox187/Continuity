package me.pepperbell.continuity.client.model;

import java.util.List;

import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.random.Random;

/**
 * Base wrapper for BlockStateModel - replaces the old WrapperBakedModel.
 * Delegates to a wrapped BlockStateModel and allows subclasses to modify parts.
 * 
 * @since 1.21.10 migration
 */
public abstract class WrappedBlockStateModel implements BlockStateModel {
	protected final BlockStateModel wrapped;

	public WrappedBlockStateModel(BlockStateModel wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public void addParts(Random random, List<BlockModelPart> parts) {
		// Get parts from wrapped model
		wrapped.addParts(random, parts);
		// Subclasses can override to modify the parts list
	}

	@Override
	public Sprite particleSprite() {
		return wrapped.particleSprite();
	}

	/**
	 * Gets the wrapped model.
	 */
	public BlockStateModel getWrappedModel() {
		return wrapped;
	}
}
