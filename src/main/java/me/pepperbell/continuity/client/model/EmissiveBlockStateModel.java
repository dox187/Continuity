package me.pepperbell.continuity.client.model;

import java.util.List;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.random.Random;

/**
 * BlockStateModel wrapper that adds emissive texture support.
 * Replaces EmissiveBakedModel for Minecraft 1.21.10+
 * 
 * When enabled, wraps each BlockModelPart to detect and add emissive texture variants
 * for quads that have corresponding emissive sprites.
 * 
 * @since 1.21.10 migration
 */
public class EmissiveBlockStateModel extends WrappedBlockStateModel {

	public EmissiveBlockStateModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void addParts(Random random, List<BlockModelPart> parts) {
		// Get parts from wrapped model
		wrapped.addParts(random, parts);

		// Only process if emissive textures are enabled
		if (!ContinuityConfig.INSTANCE.emissiveTextures.get()) {
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getEmissiveTexturesState().isEnabled()) {
			return;
		}

		// Wrap each part to add emissive processing
		// We need to extract BlockState somehow - this is a limitation we'll need to solve
		// For now, we'll need to store it or get it from context
		for (int i = 0; i < parts.size(); i++) {
			BlockModelPart part = parts.get(i);
			// TODO: Need BlockState here - might need to be passed via ThreadLocal or context
			// parts.set(i, new EmissiveBlockModelPart(part, state));
		}
	}

	/**
	 * Version that takes BlockState for proper emissive material determination.
	 * This should be called from the model loading/wrapping system.
	 */
	public void addParts(Random random, List<BlockModelPart> parts, BlockState state) {
		// Get parts from wrapped model  
		wrapped.addParts(random, parts);

		// Only process if emissive textures are enabled
		if (!ContinuityConfig.INSTANCE.emissiveTextures.get()) {
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getEmissiveTexturesState().isEnabled()) {
			return;
		}

		// Wrap each part to add emissive processing
		for (int i = 0; i < parts.size(); i++) {
			parts.set(i, new EmissiveBlockModelPart(parts.get(i), state));
		}
	}

	// Note: This model still needs to integrate with Fabric Rendering API
	// The old emitBlockQuads/emitItemQuads methods need to be replaced with
	// whatever the new Fabric API provides for BlockStateModel
}
