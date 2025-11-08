package me.pepperbell.continuity.client.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.resource.AtlasLoaderInitContext;
import me.pepperbell.continuity.client.resource.AtlasLoaderLoadContext;
import me.pepperbell.continuity.client.resource.CtmPropertiesLoader;
import me.pepperbell.continuity.client.resource.EmissiveSpriteRegistry;
import me.pepperbell.continuity.client.resource.EmissiveSuffixLoader;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteOpener;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.atlas.SingleAtlasSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;



@Mixin(AtlasLoader.class)
abstract class AtlasLoaderMixin {
	private static Map<Identifier, Set<Identifier>> cachedTextureDependencies = null;
	@SuppressWarnings("unused") // Used to track when to reload CTM properties
	private static ResourceManager lastResourceManager = null;
	private static final Object CACHE_LOCK = new Object();
	private static boolean hasLoggedInitialLoad = false;

	// Track which atlas we're currently processing via thread-local storage
	private static final ThreadLocal<Identifier> CURRENT_ATLAS_ID = new ThreadLocal<>();
	
	// Store atlas ID per instance using a map since we can't store it in instance field before super()
	private static final Map<Object, Identifier> INSTANCE_ATLAS_MAP = new java.util.concurrent.ConcurrentHashMap<>();	/**
	 * Log which atlas is being created and set current atlas ID
	 */
	@Inject(method = "of(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/texture/atlas/AtlasLoader;",
			at = @At("HEAD"))
	private static void continuity$logAtlasCreation(ResourceManager resourceManager, Identifier id,
			CallbackInfoReturnable<AtlasLoader> cir) {
		ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
				+ "AtlasLoaderMixin: Creating AtlasLoader for atlas: {}", id);
		CURRENT_ATLAS_ID.set(id);
	}

	/**
	 * Clear atlas ID after atlas creation is complete
	 */
	@Inject(method = "of(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/texture/atlas/AtlasLoader;",
			at = @At("RETURN"))
	private static void continuity$cleanupAtlasId(ResourceManager resourceManager, Identifier id,
			CallbackInfoReturnable<AtlasLoader> cir) {
		CURRENT_ATLAS_ID.remove();
	}

	/**
	 * Inject CTM texture IDs into the atlas sources before they're processed. This ensures CTM
	 * textures are added to the atlas and can be found during rendering.
	 * 
	 * NOTE: Uses cachedTextureDependencies directly because AtlasLoaderInitContext is set later (in
	 * loadSources HEAD), after this method runs in the constructor.
	 */
	@ModifyVariable(method = "<init>(Ljava/util/List;)V", at = @At(value = "LOAD", ordinal = 0),
			argsOnly = true, ordinal = 0)
	private List<AtlasSource> continuity$modifySources(List<AtlasSource> sources,
			List<AtlasSource> originalSources) {
		// Get current atlas ID from thread-local storage
		Identifier currentAtlasId = CURRENT_ATLAS_ID.get();

		// Log atlas construction attempt
		ContinuityClient.LOGGER.debug(
				ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: Atlas constructor called for {} with {} sources",
				currentAtlasId, sources.size());

		// Get CTM texture dependencies from cache
		if (cachedTextureDependencies == null || cachedTextureDependencies.isEmpty()) {
			ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
					+ "AtlasLoaderMixin: No cached texture dependencies available for atlas {}",
					currentAtlasId);
			return sources;
		}

		// Check if this atlas has any texture dependencies
		Set<Identifier> extraIds = cachedTextureDependencies.get(currentAtlasId);
		if (extraIds == null || extraIds.isEmpty()) {
			ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
					+ "AtlasLoaderMixin: No extra textures for atlas ({})", currentAtlasId);
			return sources;
		}

		// Log texture injection
		ContinuityClient.LOGGER.info(
				ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: Injecting {} CTM texture(s) into atlas ({})",
				extraIds.size(), currentAtlasId);

		List<AtlasSource> extraSources = new ObjectArrayList<>();
		for (Identifier extraId : extraIds) {
			extraSources.add(new SingleAtlasSource(extraId, Optional.empty()));
			ContinuityClient.LOGGER.debug(
					ContinuityClient.LOG_PREFIX
							+ "AtlasLoaderMixin: Adding CTM texture {} to atlas {}",
					extraId, currentAtlasId);
		}

		if (sources instanceof ArrayList) {
			sources.addAll(0, extraSources);
		} else {
			List<AtlasSource> mutableSources = new ArrayList<>(extraSources);
			mutableSources.addAll(sources);
			return mutableSources;
		}

		return sources;
	}

	/**
	 * Capture the atlas ID from ThreadLocal during constructor execution
	 */
	@Inject(method = "<init>(Ljava/util/List;)V", at = @At("HEAD"))
	private static void continuity$captureAtlasId(List<AtlasSource> sources, CallbackInfo ci) {
		Identifier atlasId = CURRENT_ATLAS_ID.get();
		// Store atlas ID using the target object (will be available after this method)
		// We'll map it in the TAIL injection when the instance is fully constructed
		ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
				+ "AtlasLoaderMixin: Capturing atlas ID in constructor HEAD: {}", atlasId);
	}

	/**
	 * Cleanup context after constructor - note the context may already be cleared by
	 * afterLoadSources if loadSources was called during init.
	 */
	@Inject(method = "<init>(Ljava/util/List;)V", at = @At("TAIL"))
	private void continuity$cleanupContextAfterInit(CallbackInfo ci) {
		// Store the atlas ID for this instance now that it's fully constructed
		Identifier atlasId = CURRENT_ATLAS_ID.get();
		if (atlasId != null) {
			INSTANCE_ATLAS_MAP.put(this, atlasId);
			ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
					+ "AtlasLoaderMixin: Stored atlas ID {} for instance {}", atlasId, this.getClass().getSimpleName());
		}
		
		// Extra safety cleanup (context should already be cleared by afterLoadSources)
		// This is here just to ensure cleanup happens even if the other injection fails
		AtlasLoaderInitContext.THREAD_LOCAL.set(null);
	}

	/**
	 * Hook into the atlas source loading phase to cache CTM texture dependencies. This is called
	 * early enough that we can cache the data for use in the constructor.
	 */
	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At("HEAD"))
	private void continuity$beforeLoadSources(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir) {
		// Log which atlas we're currently processing
		ContinuityClient.LOGGER.info(
				ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: Processing atlas loading - ResourceManager: {}",
				resourceManager.getClass().getSimpleName());
		// Cache texture dependencies if resource manager changed (with thread safety)
		synchronized (CACHE_LOCK) {
			if (resourceManager != lastResourceManager) {
				try {
					if (!hasLoggedInitialLoad) {
						ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
								+ "Loading CTM properties for atlas preparation...");
						hasLoggedInitialLoad = true;
					}

					// *** IMPORTANT: Load emissive suffix BEFORE processing emissive sprites ***
					// This ensures EmissiveSuffixLoader.emissiveSuffix is set before we use it
					EmissiveSuffixLoader.load(resourceManager);

					// Load CTM properties to get texture dependencies
					CtmPropertiesLoader.LoadingResult result =
							CtmPropertiesLoader.loadAll(resourceManager);
					cachedTextureDependencies = result.getTextureDependencies();
					lastResourceManager = resourceManager;

					// Log all atlas IDs that have texture dependencies
					if (cachedTextureDependencies != null && !cachedTextureDependencies.isEmpty()) {
						ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
								+ "AtlasLoaderMixin: Found texture dependencies for {} atlas(es): {}",
								cachedTextureDependencies.size(),
								cachedTextureDependencies.keySet());

						// Log details for each atlas
						cachedTextureDependencies.forEach((atlasId, textures) -> {
							ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
									+ "AtlasLoaderMixin: Atlas {} has {} texture dependencies",
									atlasId, textures.size());
						});
					} else {
						ContinuityClient.LOGGER.warn(ContinuityClient.LOG_PREFIX
								+ "AtlasLoaderMixin: No texture dependencies found in CTM properties");
					}
				} catch (Exception e) {
					ContinuityClient.LOGGER.error(ContinuityClient.LOG_PREFIX
							+ "Failed to load CTM properties for atlas preparation", e);
					cachedTextureDependencies = Map.of();
					hasLoggedInitialLoad = false; // Reset on error
				}
			}

			// *** IMPORTANT: Set thread-local context for EVERY atlas load attempt ***
			// This needs to happen every time, not just on first load
			// The context is cleared after the <init> constructor completes
			Identifier currentAtlasId = CURRENT_ATLAS_ID.get();
			ContinuityClient.LOGGER.debug(
					ContinuityClient.LOG_PREFIX
							+ "AtlasLoaderMixin: Checking context setup for atlas: {}",
					currentAtlasId);

			if (cachedTextureDependencies != null && currentAtlasId != null) {
				Set<Identifier> extraIds = cachedTextureDependencies.get(currentAtlasId);
				if (extraIds != null && !extraIds.isEmpty()) {
					ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
							+ "AtlasLoaderMixin: Setting AtlasLoaderInitContext with {} CTM textures into {} atlas",
							extraIds.size(), currentAtlasId);
					AtlasLoaderInitContext initContext = new AtlasLoaderInitContext() {
						@Override
						public Set<Identifier> getExtraIds() {
							return extraIds;
						}
					};
					AtlasLoaderInitContext.THREAD_LOCAL.set(initContext);
				} else {
					ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
							+ "AtlasLoaderMixin: No CTM textures for atlas ({}), context not set",
							currentAtlasId);
					AtlasLoaderInitContext.THREAD_LOCAL.set(null);
				}
			} else {
				ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: cachedTextureDependencies or currentAtlasId is null, context not set");
			}
		}
	}

	/**
	 * Clean up the init context after the loadSources method completes.
	 */
	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At("RETURN"))
	private void continuity$afterLoadSourcesCleanup(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir) {
		// Clear the context after loadSources is done, since the <init> may call it
		// The context should remain set during the <init> constructor execution
		// and be cleared after the constructor completes
	}

	/**
	 * Clean up the init context after sources are loaded.
	 */
	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At("RETURN"))
	private void continuity$afterLoadSources(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir) {
		AtlasLoaderInitContext.THREAD_LOCAL.set(null);
	}

	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At(value = "INVOKE",
					target = "Lcom/google/common/collect/ImmutableList;builder()Lcom/google/common/collect/ImmutableList$Builder;",
					remap = false),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void continuity$afterLoadSources(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir,
			Map<Identifier, AtlasSource.SpriteRegion> suppliers) {

		Identifier currentAtlasId = INSTANCE_ATLAS_MAP.get(this);
		if (currentAtlasId == null) {
			// Fallback to ThreadLocal if instance mapping failed
			currentAtlasId = CURRENT_ATLAS_ID.get();
		}
		
		ContinuityClient.LOGGER.info(
				ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: Processing {} sprite suppliers for atlas {}",
				suppliers.size(), currentAtlasId);

		// Log some sample sprite IDs for debugging
		if (!suppliers.isEmpty()) {
			int logCount = Math.min(5, suppliers.size());
			ContinuityClient.LOGGER.debug(
					ContinuityClient.LOG_PREFIX
							+ "AtlasLoaderMixin: Sample sprite IDs for atlas {} (first {}): {}",
					currentAtlasId, logCount, suppliers.keySet().stream().limit(logCount).toList());
		}

		String emissiveSuffix = EmissiveSuffixLoader.getEmissiveSuffix();
		ContinuityClient.LOGGER.info(
				ContinuityClient.LOG_PREFIX + "AtlasLoaderMixin: emissiveSuffix={} for atlas {}",
				emissiveSuffix, currentAtlasId);

		// Only process emissive sprites for certain atlases (e.g., blocks, particles)
		// Emissive sprites are primarily used with block and particle textures
		boolean shouldProcessEmissive =
				currentAtlasId != null && ("blocks".equals(currentAtlasId.getPath())
						|| "particles".equals(currentAtlasId.getPath()));

		if (emissiveSuffix != null && shouldProcessEmissive) {
			ContinuityClient.LOGGER.debug(
					ContinuityClient.LOG_PREFIX
							+ "AtlasLoaderMixin: Processing emissive sprites for atlas {}",
					currentAtlasId);

			Map<Identifier, AtlasSource.SpriteRegion> emissiveSuppliers =
					new Object2ObjectOpenHashMap<>();
			Map<Identifier, Identifier> emissiveIdMap = new Object2ObjectOpenHashMap<>();

			// Enhanced bidirectional emissive detection
			suppliers.forEach((id, supplier) -> {
				String path = id.getPath();

				// Case 1: Base texture -> Look for emissive variant (base_texture ->
				// base_texture_e)
				if (!path.endsWith(emissiveSuffix)) {
					Identifier emissiveId = id.withPath(path + emissiveSuffix);

					// Check if emissive variant exists in suppliers (already loaded)
					if (suppliers.containsKey(emissiveId)) {
						emissiveIdMap.put(id, emissiveId);
						ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
								+ "AtlasLoaderMixin: Found existing emissive variant {} for base {}",
								emissiveId, id);
					} else {
						// Try to find emissive variant as a resource file
						Identifier emissiveLocation =
								emissiveId.withPath("textures/" + emissiveId.getPath() + ".png");
						Optional<Resource> optionalResource =
								resourceManager.getResource(emissiveLocation);
						if (optionalResource.isPresent()) {
							Resource resource = optionalResource.get();
							emissiveSuppliers.put(emissiveId,
									opener -> opener.loadSprite(emissiveId, resource));
							emissiveIdMap.put(id, emissiveId);
							ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
									+ "AtlasLoaderMixin: Loading emissive resource {} for base {}",
									emissiveLocation, id);
						}
					}
				}
				// Case 2: Emissive texture -> Look for base texture (base_texture_e ->
				// base_texture)
				else {
					// Remove the emissive suffix to get the base texture ID
					String basePath = path.substring(0, path.length() - emissiveSuffix.length());
					Identifier baseId = id.withPath(basePath);

					// Check if base texture exists in suppliers
					if (suppliers.containsKey(baseId)) {
						emissiveIdMap.put(baseId, id);
						ContinuityClient.LOGGER.debug(
								ContinuityClient.LOG_PREFIX
										+ "AtlasLoaderMixin: Found base texture {} for emissive {}",
								baseId, id);
					} else {
						// Try to find base texture as a resource file and load it
						Identifier baseLocation =
								baseId.withPath("textures/" + baseId.getPath() + ".png");
						Optional<Resource> optionalResource =
								resourceManager.getResource(baseLocation);
						if (optionalResource.isPresent()) {
							Resource resource = optionalResource.get();
							// Add the base texture to suppliers
							suppliers.put(baseId, opener -> opener.loadSprite(baseId, resource));
							emissiveIdMap.put(baseId, id);
							ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
									+ "AtlasLoaderMixin: Loading base resource {} for emissive {}",
									baseLocation, id);
						} else {
							ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
									+ "AtlasLoaderMixin: No base texture found for emissive {}, treating as standalone",
									id);
						}
					}
				}
			});

			// Add any new emissive suppliers we discovered
			suppliers.putAll(emissiveSuppliers);

			if (!emissiveIdMap.isEmpty()) {
				// Store in global registry for EmissiveTextureManager and EmissiveBlockModelPart
				// access
				EmissiveSpriteRegistry.setEmissiveMapping(emissiveIdMap);
				ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: Registered {} emissive sprite mappings for atlas {} in global registry",
						emissiveIdMap.size(), currentAtlasId);

				// Also try to set in contexts for backwards compatibility
				AtlasLoaderLoadContext atlasContext = AtlasLoaderLoadContext.THREAD_LOCAL.get();
				if (atlasContext != null) {
					atlasContext.setEmissiveIdMap(emissiveIdMap);
				}

				// Also set in SpriteLoaderLoadContext for compatibility
				me.pepperbell.continuity.client.resource.SpriteLoaderLoadContext spriteContext =
						me.pepperbell.continuity.client.resource.SpriteLoaderLoadContext.THREAD_LOCAL
								.get();
				if (spriteContext != null) {
					me.pepperbell.continuity.client.resource.SpriteLoaderLoadContext.EmissiveControl emissiveControl =
							spriteContext.getEmissiveControl(currentAtlasId);
					if (emissiveControl != null) {
						emissiveControl.setEmissiveIdMap(emissiveIdMap);
						emissiveControl.markHasEmissives();
					}
				}
			} else {
				ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: No emissive sprites found for atlas {} with suffix '{}'",
						currentAtlasId, emissiveSuffix);
			}
		} else if (emissiveSuffix != null) {
			ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
					+ "AtlasLoaderMixin: Skipping emissive processing for atlas {} (not in allowed list)",
					currentAtlasId);
		}
		
		// Clean up instance mapping to prevent memory leaks
		INSTANCE_ATLAS_MAP.remove(this);
	}
}
