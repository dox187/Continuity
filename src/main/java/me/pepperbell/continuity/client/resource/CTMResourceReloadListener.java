package me.pepperbell.continuity.client.resource;

import java.util.Collection;
import java.util.List;

import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.mixinterface.AtlasManagerAccess;
import me.pepperbell.continuity.client.model.QuadProcessors;
import me.pepperbell.continuity.client.resource.ModelWrappingCoordinator;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AtlasManager;
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
		// *** FIX: DO NOT reset here - it clears atlasLoadingComplete mid-cycle! ***
		// The resetModelsWrapped() is already called in BlockModelsMixin.onHeadSetModels()
		// We don't need a full reset here because:
		// 1. modelsWrapped is already reset by resetModelsWrapped()
		// 2. atlasLoadingComplete will be set by SpriteAtlasTextureMixin.upload()
		// 3. blockModels will be set by BlockModelsMixin.onTailSetModels()
		// A full reset here would clear atlasLoadingComplete before setBlockModels() runs!

		// REMOVED: ModelWrappingCoordinator.reset();

		try {
			// Load CTM properties
			CtmPropertiesLoader.LoadingResult result =
					CtmPropertiesLoader.loadAllWithState(manager);

			// Access AtlasManager from MinecraftClient to get sprites
			AtlasManager atlasManager =
					((AtlasManagerAccess) MinecraftClient.getInstance().getBakedModelManager())
							.continuity$getAtlasManager();

			// Get the block atlas texture
			// In 1.21.10, atlas IDs changed from "minecraft:textures/atlas/blocks.png" to
			// "minecraft:blocks"
			var blockAtlas = atlasManager.getAtlasTexture(Identifier.of("minecraft", "blocks"));

			// Create processor holders with sprite lookup
			List<QuadProcessors.ProcessorHolder> processorHolders =
					result.createProcessorHolders(spriteId -> {
						// Get sprite from the atlas texture directly using the texture ID
						// SpriteIdentifier contains both atlas ID and texture ID - we only need the
						// texture ID
						return blockAtlas.getSprite(spriteId.getTextureId());
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
