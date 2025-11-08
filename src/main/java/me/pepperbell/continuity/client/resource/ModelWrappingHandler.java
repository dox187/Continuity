package me.pepperbell.continuity.client.resource;

import java.util.Map;

import me.pepperbell.continuity.client.mixin.BlockModelsAccessor;
import me.pepperbell.continuity.client.model.CtmBlockStateModel;
import me.pepperbell.continuity.client.model.EmissiveBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.model.BlockStateModel;

/**
 * Handles wrapping BlockStateModel instances with CTM support.
 */
public class ModelWrappingHandler {
	private static boolean wrapCtm = false;
	private static boolean wrapEmissive = false;

	public static void init() {
		// Reserved for future initialization
	}

	public static void setInstance(boolean wrapCtm, boolean wrapEmissive) {
		ModelWrappingHandler.wrapCtm = wrapCtm;
		ModelWrappingHandler.wrapEmissive = wrapEmissive;
	}

	public static void resetInstance() {
		wrapCtm = false;
		wrapEmissive = false;
	}

	/**
	 * Wraps block state models with CTM and/or emissive support.
	 */
	public static void wrapModels(BlockModels blockModels) {
		if (!wrapCtm && !wrapEmissive) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(
					me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
							+ "Not wrapping models - wrapCtm={}, wrapEmissive={}",
					wrapCtm, wrapEmissive);
			return;
		}

		// Access the internal models map through the accessor
		Map<BlockState, BlockStateModel> models = ((BlockModelsAccessor) blockModels).getModels();

		me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(
				me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
						+ "Starting model wrapping: {} models in map, wrapCtm={}, wrapEmissive={}",
				models.size(), wrapCtm, wrapEmissive);

		int ctmWrappedCount = 0;
		int emissiveWrappedCount = 0;
		int skippedCtmCount = 0;
		int skippedEmissiveCount = 0;

		for (Map.Entry<BlockState, BlockStateModel> entry : models.entrySet()) {
			BlockState state = entry.getKey();
			BlockStateModel model = entry.getValue();
			BlockStateModel wrappedModel = model;

			// Skip CTM wrapping if already wrapped
			if (!(model instanceof CtmBlockStateModel) && wrapCtm) {
				try {
					wrappedModel = new CtmBlockStateModel(wrappedModel, state);
					ctmWrappedCount++;
				} catch (Exception e) {
					me.pepperbell.continuity.client.ContinuityClient.LOGGER
							.error(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
									+ "Failed to wrap CTM model for block {}", state, e);
					skippedCtmCount++;
				}
			} else if (model instanceof CtmBlockStateModel && wrapCtm) {
				skippedCtmCount++;
			}

			// Wrap with emissive support if enabled
			if (wrapEmissive && !(model instanceof EmissiveBlockStateModel)) {
				try {
					wrappedModel = new EmissiveBlockStateModel(wrappedModel, state);
					emissiveWrappedCount++;
					me.pepperbell.continuity.client.ContinuityClient.LOGGER
							.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
									+ "Wrapped block {} with emissive support", state);
				} catch (Exception e) {
					me.pepperbell.continuity.client.ContinuityClient.LOGGER
							.error(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
									+ "Failed to wrap emissive model for block {}", state, e);
					skippedEmissiveCount++;
				}
			} else if (model instanceof EmissiveBlockStateModel && wrapEmissive) {
				skippedEmissiveCount++;
			}

			entry.setValue(wrappedModel);
		}

		me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(
				me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX
						+ "Model wrapping complete: CTM wrapped={}, skipped={}; Emissive wrapped={}, skipped={}; Total models={}",
				ctmWrappedCount, skippedCtmCount, emissiveWrappedCount, skippedEmissiveCount,
				models.size());
	}
}
