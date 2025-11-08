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
	private static boolean hasLoggedInjection = false;

	/**
	 * Inject CTM texture IDs into the atlas sources before they're processed. This ensures CTM
	 * textures are added to the atlas and can be found during rendering.
	 * 
	 * IMPORTANT: This loads CTM properties directly because there's no reliable hook that runs
	 * before this constructor on the same thread with proper timing.
	 */
	/**
	 * Hook into the AtlasLoader constructor ENTRY to prepare the context BEFORE sources are
	 * modified. This ensures the context is available when modifySources is called.
	 */
	@Inject(method = "<init>(Ljava/util/List;)V", at = @At("HEAD"))
	private void continuity$setupContextBeforeInit(CallbackInfo ci) {
		// Load CTM suffix and properties if not already loaded
		try {
			// Get resource manager from thread-local context if available
			// Otherwise we'll load from cache
			synchronized (CACHE_LOCK) {
				if (cachedTextureDependencies != null) {
					Identifier blocksAtlasId = Identifier.of("minecraft", "blocks");
					Set<Identifier> extraIds = cachedTextureDependencies.get(blocksAtlasId);
					if (extraIds != null && !extraIds.isEmpty()) {
						ContinuityClient.LOGGER.debug(ContinuityClient.LOG_PREFIX
								+ "Setting up AtlasLoaderInitContext with {} CTM textures for blocks atlas",
								extraIds.size());
						AtlasLoaderInitContext initContext = new AtlasLoaderInitContext() {
							@Override
							public Set<Identifier> getExtraIds() {
								return extraIds;
							}
						};
						AtlasLoaderInitContext.THREAD_LOCAL.set(initContext);
					}
				}
			}
		} catch (Exception e) {
			ContinuityClient.LOGGER.debug(
					ContinuityClient.LOG_PREFIX + "Error setting up AtlasLoaderInitContext: {}",
					e.getMessage());
		}
	}

	@ModifyVariable(method = "<init>(Ljava/util/List;)V", at = @At(value = "LOAD", ordinal = 0),
			argsOnly = true, ordinal = 0)
	private List<AtlasSource> continuity$modifySources(List<AtlasSource> sources,
			List<AtlasSource> originalSources) {
		// Only inject if we have a context set (which means this is the blocks atlas)
		AtlasLoaderInitContext context = AtlasLoaderInitContext.THREAD_LOCAL.get();
		if (context == null) {
			// No context means this atlas doesn't need CTM textures (e.g., item, entity atlases)
			return sources;
		}

		Set<Identifier> extraIds = context.getExtraIds();
		if (extraIds == null || extraIds.isEmpty()) {
			return sources;
		}

		synchronized (CACHE_LOCK) {
			if (!hasLoggedInjection) {
				ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
						+ "Injecting {} CTM texture(s) into blocks atlas", extraIds.size());
				hasLoggedInjection = true;
			}
		}

		List<AtlasSource> extraSources = new ObjectArrayList<>();
		for (Identifier extraId : extraIds) {
			extraSources.add(new SingleAtlasSource(extraId, Optional.empty()));
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
	 * Clean up the init context after the constructor completes.
	 */
	@Inject(method = "<init>(Ljava/util/List;)V", at = @At("TAIL"))
	private void continuity$cleanupContextAfterInit(CallbackInfo ci) {
		// Clear context after init is complete
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

					// Reset injection flag for new resource reload
					hasLoggedInjection = false;

					// Log once after cache is populated
					Identifier blocksAtlasId = Identifier.of("minecraft", "blocks");
					Set<Identifier> extraIds = cachedTextureDependencies.get(blocksAtlasId);

					if (extraIds != null && !extraIds.isEmpty()) {
						ContinuityClient.LOGGER.info(
								ContinuityClient.LOG_PREFIX
										+ "Loaded {} CTM texture dependencies for atlas injection",
								extraIds.size());
					} else {
						ContinuityClient.LOGGER.warn(ContinuityClient.LOG_PREFIX
								+ "No CTM texture dependencies found for blocks atlas");
					}
				} catch (Exception e) {
					ContinuityClient.LOGGER.error(ContinuityClient.LOG_PREFIX
							+ "Failed to load CTM properties for atlas preparation", e);
					cachedTextureDependencies = Map.of();
					hasLoggedInitialLoad = false; // Reset on error
				}
			}

			// Set thread-local context for this atlas (happens on every call, but only for blocks
			// atlas)
			Identifier blocksAtlasId = Identifier.of("minecraft", "blocks");
			if (cachedTextureDependencies != null) {
				Set<Identifier> extraIds = cachedTextureDependencies.get(blocksAtlasId);
				if (extraIds != null && !extraIds.isEmpty()) {
					AtlasLoaderInitContext initContext = new AtlasLoaderInitContext() {
						@Override
						public Set<Identifier> getExtraIds() {
							return extraIds;
						}
					};
					AtlasLoaderInitContext.THREAD_LOCAL.set(initContext);
				}
			}
		}
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
		String emissiveSuffix = EmissiveSuffixLoader.getEmissiveSuffix();
		ContinuityClient.LOGGER.info(
				ContinuityClient.LOG_PREFIX + "AtlasLoaderMixin: emissiveSuffix={}",
				emissiveSuffix);
		if (emissiveSuffix != null) {
			Map<Identifier, AtlasSource.SpriteRegion> emissiveSuppliers =
					new Object2ObjectOpenHashMap<>();
			Map<Identifier, Identifier> emissiveIdMap = new Object2ObjectOpenHashMap<>();
			suppliers.forEach((id, supplier) -> {
				if (!id.getPath().endsWith(emissiveSuffix)) {
					Identifier emissiveId = id.withPath(id.getPath() + emissiveSuffix);
					if (!suppliers.containsKey(emissiveId)) {
						Identifier emissiveLocation =
								emissiveId.withPath("textures/" + emissiveId.getPath() + ".png");
						Optional<Resource> optionalResource =
								resourceManager.getResource(emissiveLocation);
						if (optionalResource.isPresent()) {
							Resource resource = optionalResource.get();
							emissiveSuppliers.put(emissiveId,
									opener -> opener.loadSprite(emissiveId, resource));
							emissiveIdMap.put(id, emissiveId);
						}
					} else {
						emissiveIdMap.put(id, emissiveId);
					}
				}
			});
			suppliers.putAll(emissiveSuppliers);
			if (!emissiveIdMap.isEmpty()) {
				// Store in global registry for EmissiveTextureManager and EmissiveBlockModelPart
				// access
				EmissiveSpriteRegistry.setEmissiveMapping(emissiveIdMap);
				ContinuityClient.LOGGER.info(ContinuityClient.LOG_PREFIX
						+ "AtlasLoaderMixin: Registered {} emissive sprite mappings in global registry",
						emissiveIdMap.size());

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
							spriteContext.getEmissiveControl(Identifier.of("minecraft", "blocks"));
					if (emissiveControl != null) {
						emissiveControl.setEmissiveIdMap(emissiveIdMap);
						emissiveControl.markHasEmissives();
					}
				}
			} else {
				ContinuityClient.LOGGER.debug(
						ContinuityClient.LOG_PREFIX
								+ "AtlasLoaderMixin: No emissive sprites found with suffix '{}'",
						emissiveSuffix);
			}
		}
	}
}
