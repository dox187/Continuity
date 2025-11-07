package me.pepperbell.continuity.client.resource;

import java.util.Map;

import me.pepperbell.continuity.client.mixin.BlockModelsAccessor;
import me.pepperbell.continuity.client.model.CtmBlockStateModel;
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
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
				"Not wrapping models - wrapCtm={}, wrapEmissive={}", wrapCtm, wrapEmissive);
			return;
		}
		
		// Access the internal models map through the accessor
		Map<BlockState, BlockStateModel> models = ((BlockModelsAccessor) blockModels).getModels();
		
		me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
			"Starting model wrapping: {} models in map, wrapCtm={}, wrapEmissive={}", 
			models.size(), wrapCtm, wrapEmissive);
		
		int wrappedCount = 0;
		int skippedCount = 0;
		
		for (Map.Entry<BlockState, BlockStateModel> entry : models.entrySet()) {
			BlockState state = entry.getKey();
			BlockStateModel model = entry.getValue();
			BlockStateModel wrappedModel = model;
			
			// Skip if already wrapped
			if (model instanceof CtmBlockStateModel) {
				skippedCount++;
				continue;
			}
			
			// Wrap with CTM support if enabled
			if (wrapCtm) {
				wrappedModel = new CtmBlockStateModel(wrappedModel, state);
				wrappedCount++;
			}
			
			// TODO: Wrap with emissive support if enabled
			// if (wrapEmissive) {
			//     wrappedModel = new EmissiveBlockStateModel(wrappedModel, state);
			// }
			
			entry.setValue(wrappedModel);
		}
		
		me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
			"Wrapped {} block state models with CTM support ({} skipped, {} total)", 
			wrappedCount, skippedCount, models.size());
	}
}
