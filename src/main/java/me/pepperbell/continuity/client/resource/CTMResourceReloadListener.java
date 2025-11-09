package me.pepperbell.continuity.client.resource;

import java.util.Collection;
import java.util.List;

import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.mixinterface.AtlasManagerAccess;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

/**
 * Resource reload listener that replaces BakedModelManagerMixin. This runs during resource reload
 * to load CTM properties and set up quad processors.
 */
public class CTMResourceReloadListener implements SimpleSynchronousResourceReloadListener {
	public static final Identifier ID = ContinuityClient.asId("ctm_reload");
	public static final CTMResourceReloadListener INSTANCE = new CTMResourceReloadListener();

	private CTMResourceReloadListener() {}

	@Override
	public Identifier getFabricId() {
		return ID;
	}

	@Override
	public Collection<Identifier> getFabricDependencies() {
		// Run after models and textures are loaded
		return List.of(ResourceReloadListenerKeys.MODELS);
	}

	@Override
	public void reload(ResourceManager manager) {
		// DON'T reset the coordinator here - it causes a race condition!
		// The coordinator manages its own state through setBlockModels() and
		// onAtlasLoadingComplete()
		// REMOVED: ModelWrappingCoordinator.reset();

		try {
			// Load CTM properties
			CtmPropertiesLoader.LoadingResult result =
					CtmPropertiesLoader.loadAllWithState(manager);

			// Access AtlasManager from MinecraftClient to get sprites
			AtlasManager atlasManager =
					((AtlasManagerAccess) MinecraftClient.getInstance().getBakedModelManager())
							.continuity$getAtlasManager();

			// Create processor holders with sprite lookup that uses the correct atlas for each
			// texture
			List<QuadProcessors.ProcessorHolder> processorHolders =
					result.createProcessorHolders(spriteId -> {
						// Get the correct atlas for this sprite using its atlas ID
						// The SpriteIdentifier already has the correct atlas ID from TextureUtil
						SpriteAtlasTexture atlas =
								atlasManager.getAtlasTexture(spriteId.getAtlasId());
						if (atlas == null) {
							ContinuityClient.LOGGER.warn(ContinuityClient.LOG_PREFIX
									+ "CTMResourceReloadListener: Atlas not found for {}, texture {}",
									spriteId.getAtlasId(), spriteId.getTextureId());
							// Throw to fail fast - atlas should exist at this point
							throw new RuntimeException("Atlas not found: " + spriteId.getAtlasId());
						}
						// Get sprite from the correct atlas using the texture ID
						return atlas.getSprite(spriteId.getTextureId());
					});

			// Register the processors
			QuadProcessors.reload(processorHolders);

			// *** FIX: Enable model wrapping if we have processors ***
			// This was previously done in BakedModelManagerReloadExtension.beforeBake()
			// but that class is never instantiated in the new system
			boolean wrapCtm = !processorHolders.isEmpty();

			// Check emissive textures config and feature state
			boolean wrapEmissive =
					me.pepperbell.continuity.client.config.ContinuityConfig.INSTANCE.emissiveTextures
							.get()
							&& me.pepperbell.continuity.api.client.ContinuityFeatureStates.get()
									.getEmissiveTexturesState().isEnabled();

			ModelWrappingHandler.setInstance(wrapCtm, wrapEmissive);

			ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
					+ "Loaded {} CTM properties, wrapping enabled: wrapCtm={}, wrapEmissive={}",
					processorHolders.size(), wrapCtm, wrapEmissive);
		} catch (Exception e) {
			ContinuityClient.LOGGER
					.error(ContinuityClient.LOG_PREFIX + "Failed to load CTM properties", e);
		}
	}
}
