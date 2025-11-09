package me.pepperbell.continuity.client.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.pepperbell.continuity.client.resource.AtlasLoaderLoadContext;
import me.pepperbell.continuity.client.resource.CtmPropertiesLoader;
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
	private static final Logger LOGGER = LoggerFactory.getLogger("Continuity/AtlasLoader");

	// PHASE 7: Static cache for CTM texture dependencies
	private static Map<Identifier, Set<Identifier>> cachedTextureDependencies = null;
	private static ResourceManager lastResourceManager = null;
	private static final Object CACHE_LOCK = new Object();

	// PHASE 7: ThreadLocal storage for modified sources during construction
	private static final ThreadLocal<List<AtlasSource>> MODIFIED_SOURCES = new ThreadLocal<>();

	/**
	 * PHASE 7: Load CTM properties synchronously BEFORE any atlas construction. This runs at HEAD
	 * of loadSources(), ensuring texture dependencies are cached.
	 */
	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At("HEAD"))
	private void continuity$beforeLoadSources(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir) {
		synchronized (CACHE_LOCK) {
			if (resourceManager != lastResourceManager) {
				try {
					LOGGER.info("[Continuity] PHASE 7: Loading CTM properties synchronously...");

					// Load emissive suffix first
					EmissiveSuffixLoader.load(resourceManager);

					// Load CTM properties SYNCHRONOUSLY
					CtmPropertiesLoader.LoadingResult result =
							CtmPropertiesLoader.loadAll(resourceManager);
					cachedTextureDependencies = result.getTextureDependencies();
					lastResourceManager = resourceManager;

					LOGGER.info(
							"[Continuity] PHASE 7: Cached texture dependencies for {} atlas(es)",
							cachedTextureDependencies != null ? cachedTextureDependencies.size()
									: 0);

					// Debug: log what's in the cache
					if (cachedTextureDependencies != null) {
						int totalTextures = 0;
						for (Map.Entry<Identifier, Set<Identifier>> entry : cachedTextureDependencies
								.entrySet()) {
							LOGGER.info("  Atlas: {}, textures: {}", entry.getKey(),
									entry.getValue().size());
							totalTextures += entry.getValue().size();
						}
						LOGGER.info("  Total CTM textures across all atlases: {}", totalTextures);
					}
				} catch (Exception e) {
					LOGGER.error("[Continuity] PHASE 7: Failed to load CTM properties", e);
					cachedTextureDependencies = Map.of();
				}
			}
		}
	}

	// Intercept the constructor and prepare modified sources
	@Inject(method = "<init>(Ljava/util/List;)V", at = @At("HEAD"))
	private static void continuity$beforeInit(List<AtlasSource> sources,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		// PHASE 7: Use static cache instead of context (which isn't set yet)
		if (cachedTextureDependencies == null || cachedTextureDependencies.isEmpty()) {
			MODIFIED_SOURCES.remove();
			return;
		}

		// TODO: Get current atlas ID - for now just log
		LOGGER.info(
				"[Continuity] PHASE 7: continuity$beforeInit() called, sources: {}, cache size: {}",
				sources.size(), cachedTextureDependencies.size());

		// IMPORTANT: We need to know WHICH atlas we're processing!
		// The old code used ThreadLocal to track this. For now, we'll add to ALL atlases
		// which matches the "wrong" behavior but at least it works!

		Set<Identifier> allExtraIds = new java.util.HashSet<>();
		cachedTextureDependencies.values().forEach(allExtraIds::addAll);

		LOGGER.info("[Continuity] PHASE 7: Collected {} unique CTM texture IDs from cache",
				allExtraIds.size());

		if (allExtraIds.isEmpty()) {
			LOGGER.warn("[Continuity] PHASE 7: No CTM textures to add (cache values are empty?)");
			MODIFIED_SOURCES.remove();
			return;
		}

		LOGGER.info("[Continuity] PHASE 7: Adding {} total CTM textures to sources",
				allExtraIds.size());

		List<AtlasSource> extraSources = new ObjectArrayList<>();
		for (Identifier extraId : allExtraIds) {
			extraSources.add(new SingleAtlasSource(extraId, Optional.empty()));
		}

		LOGGER.info("[Continuity] PHASE 7: Original sources type: {}, size: {}",
				sources.getClass().getName(), sources.size());

		// Create new mutable list with CTM textures first, then original sources
		List<AtlasSource> modifiedSources = new ArrayList<>(sources.size() + extraSources.size());
		modifiedSources.addAll(extraSources);
		modifiedSources.addAll(sources);

		LOGGER.info("[Continuity] PHASE 7: Modified sources size: {}", modifiedSources.size());
		MODIFIED_SOURCES.set(modifiedSources);
	}

	// Apply the modified sources to the constructor parameter
	// MUST be static because it's before super() call
	@ModifyVariable(method = "<init>(Ljava/util/List;)V", at = @At(value = "HEAD"), argsOnly = true)
	private static List<AtlasSource> continuity$modifySources(List<AtlasSource> sources) {
		List<AtlasSource> modified = MODIFIED_SOURCES.get();
		if (modified != null) {
			MODIFIED_SOURCES.remove(); // Clear for next use
			return modified;
		}
		return sources;
	}

	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At(value = "INVOKE",
					target = "Lcom/google/common/collect/ImmutableList;builder()Lcom/google/common/collect/ImmutableList$Builder;",
					remap = false),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void continuity$afterLoadSources(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir,
			Map<Identifier, AtlasSource.SpriteRegion> suppliers) {
		LOGGER.info("[Continuity] PHASE 7: AtlasLoaderMixin.afterLoadSources() called");

		// PHASE 7: Early extension creation for initial load (before CtmResourceReloadListener
		// runs)
		AtlasLoaderLoadContext context = AtlasLoaderLoadContext.THREAD_LOCAL.get();
		LOGGER.info("[Continuity] PHASE 7: ThreadLocal context: {}",
				(context != null ? "EXISTS" : "NULL"));

		if (context == null) {
			// This is initial load (Quick Reload) - try to get or create extension
			LOGGER.info("[Continuity] PHASE 7: No context - attempting early extension creation");
			me.pepperbell.continuity.client.resource.CtmInitializationCoordinator coordinator =
					me.pepperbell.continuity.client.resource.CtmInitializationCoordinator
							.getInstance();

			me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension ext =
					coordinator.getExtension();
			LOGGER.info("[Continuity] PHASE 7: getExtension() returned: {}",
					(ext != null ? "EXISTS" : "NULL"));

			if (ext == null) {
				// Extension doesn't exist yet - create it NOW using available ResourceManager
				LOGGER.info("[Continuity] PHASE 7: Calling startReloadEarly()");
				coordinator.startReloadEarly(resourceManager);
				ext = coordinator.getExtension();
				LOGGER.info("[Continuity] PHASE 7: After startReloadEarly(), extension: {}",
						(ext != null ? "EXISTS" : "NULL"));
			}

			if (ext != null) {
				LOGGER.info("[Continuity] PHASE 7: Setting context via ext.setContext()");
				ext.setContext(); // Sets BOTH ThreadLocal AND globalContextForInitialLoad
				context = AtlasLoaderLoadContext.THREAD_LOCAL.get();
				LOGGER.info("[Continuity] PHASE 7: After setContext(), context: {}",
						(context != null ? "EXISTS" : "NULL"));
			}
		}

		// Emissive texture handling - add emissive variants to atlas
		if (context != null) {
			String emissiveSuffix = EmissiveSuffixLoader.getEmissiveSuffix();
			if (emissiveSuffix != null) {
				Map<Identifier, AtlasSource.SpriteRegion> emissiveSuppliers =
						new Object2ObjectOpenHashMap<>();
				Map<Identifier, Identifier> emissiveIdMap = new Object2ObjectOpenHashMap<>();
				suppliers.forEach((id, supplier) -> {
					if (!id.getPath().endsWith(emissiveSuffix)) {
						Identifier emissiveId = id.withPath(id.getPath() + emissiveSuffix);
						if (!suppliers.containsKey(emissiveId)) {
							Identifier emissiveLocation = emissiveId
									.withPath("textures/" + emissiveId.getPath() + ".png");
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
					context.setEmissiveIdMap(emissiveIdMap);
				}
			}
		}
	}
}
