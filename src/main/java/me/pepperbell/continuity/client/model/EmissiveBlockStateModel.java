package me.pepperbell.continuity.client.model;

import java.util.List;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.resource.EmissiveSpriteRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.random.Random;

/**
 * BlockStateModel wrapper that adds emissive texture support. Replaces EmissiveBakedModel for
 * Minecraft 1.21.10+
 * 
 * When enabled, wraps each BlockModelPart to detect and add emissive texture variants for quads
 * that have corresponding emissive sprites.
 * 
 * @since 1.21.10 migration
 */
public class EmissiveBlockStateModel extends WrappedBlockStateModel {
	private final BlockState blockState;
	private boolean partsProcessed = false;

	public EmissiveBlockStateModel(BlockStateModel wrapped, BlockState blockState) {
		super(wrapped);
		this.blockState = blockState;
	}

	@Override
	public void addParts(Random random, List<BlockModelPart> parts) {
		// Get parts from wrapped model
		wrapped.addParts(random, parts);

		// Mark that we're processing parts for this block state
		this.partsProcessed = true;

		// Only process if emissive textures are enabled
		if (!ContinuityConfig.INSTANCE.emissiveTextures.get()) {
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getEmissiveTexturesState().isEnabled()) {
			return;
		}

		// Only wrap if there are actually emissive sprites registered
		if (!EmissiveSpriteRegistry.hasEmissives()) {
			return;
		}

		// Wrap each part ONLY if there's actually an emissive sprite for this block
		for (int i = 0; i < parts.size(); i++) {
			BlockModelPart part = parts.get(i);
			EmissiveBlockModelPart wrappedPart = new EmissiveBlockModelPart(part, blockState);

			// Check if this part actually has emissive quads
			// Only replace if wrapping was successful (emissive sprites found)
			if (wrappedPart.hasEmissiveQuads()) {
				parts.set(i, wrappedPart);
			}
		}

	}

	/**
	 * Gets the BlockState this model is wrapping for.
	 */
	public BlockState getBlockState() {
		return blockState;
	}

	/**
	 * Returns true if parts have been processed by this emissive model.
	 */
	public boolean isPartsProcessed() {
		return partsProcessed;
	}
}
