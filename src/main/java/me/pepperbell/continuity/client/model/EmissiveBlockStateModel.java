package me.pepperbell.continuity.client.model;

import java.util.List;

import me.pepperbell.continuity.client.config.ContinuityConfig;
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
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
					me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
							+ "EmissiveBlockStateModel: Emissive textures disabled for block {}",
					blockState);
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getEmissiveTexturesState().isEnabled()) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
					me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
							+ "EmissiveBlockStateModel: Emissive feature disabled for block {}",
					blockState);
			return;
		}

		// Wrap each part to add emissive processing
		int wrappedPartsCount = 0;
		for (int i = 0; i < parts.size(); i++) {
			BlockModelPart part = parts.get(i);
			EmissiveBlockModelPart wrappedPart = new EmissiveBlockModelPart(part, blockState);
			parts.set(i, wrappedPart);
			wrappedPartsCount++;
		}

		me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(
				me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
						+ "EmissiveBlockStateModel: Successfully wrapped {} parts for block {} with emissive support",
				wrappedPartsCount, blockState);
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
