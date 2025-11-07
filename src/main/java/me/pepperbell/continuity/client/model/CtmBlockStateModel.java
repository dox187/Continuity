package me.pepperbell.continuity.client.model;

import java.util.List;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.random.Random;

/**
 * BlockStateModel wrapper that adds Connected Textures Mod (CTM) support.
 * Replaces CtmBakedModel for Minecraft 1.21.10+
 * 
 * Processes block quads to apply connected texture patterns based on neighboring blocks.
 * 
 * @since 1.21.10 migration
 */
public class CtmBlockStateModel extends WrappedBlockStateModel {
	private final BlockState defaultState;

	public CtmBlockStateModel(BlockStateModel wrapped, BlockState defaultState) {
		super(wrapped);
		this.defaultState = defaultState;
	}

	@Override
	public void addParts(Random random, List<BlockModelPart> parts) {
		// Get parts from wrapped model
		wrapped.addParts(random, parts);
		
		int originalSize = parts.size();

		// Only process if CTM is enabled
		if (!ContinuityConfig.INSTANCE.connectedTextures.get()) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
				me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
				"CtmBlockStateModel.addParts() - CTM disabled in config");
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getConnectedTexturesState().isEnabled()) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
				me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
				"CtmBlockStateModel.addParts() - CTM feature state disabled");
			return;
		}

		// Wrap each part to add CTM processing
		for (int i = 0; i < parts.size(); i++) {
			parts.set(i, new CtmBlockModelPart(parts.get(i), defaultState));
		}
		
		me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
			me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
			"CtmBlockStateModel.addParts() - Wrapped {} parts for block {}", 
			originalSize, defaultState.getBlock());
	}

	public BlockState getDefaultState() {
		return defaultState;
	}

	// Note: CTM processing requires world context (neighboring blocks)
	// The old emitBlockQuads method received BlockRenderView, BlockState, BlockPos
	// We need to figure out how to get this context in the new system
	// Likely through ThreadLocal or Fabric API extensions
}
