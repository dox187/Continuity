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
import me.pepperbell.continuity.client.resource.EmissiveIdMapStorage;
import me.pepperbell.continuity.client.resource.EmissiveSuffixLoader;
import net.minecraft.client.texture.SpriteAtlasTexture;
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

	private static Map<Identifier, Set<Identifier>> cachedTextureDependencies = null;
	private static ResourceManager lastResourceManager = null;
	private static final Object CACHE_LOCK = new Object();

	private static final ThreadLocal<List<AtlasSource>> MODIFIED_SOURCES = new ThreadLocal<>();

	@Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
			at = @At("HEAD"))
	private void continuity$beforeLoadSources(ResourceManager resourceManager,
			CallbackInfoReturnable<List<Function<SpriteOpener, SpriteContents>>> cir) {
		synchronized (CACHE_LOCK) {
			if (resourceManager != lastResourceManager) {
				try {
					EmissiveSuffixLoader.load(resourceManager);
					CtmPropertiesLoader.LoadingResult result =
							CtmPropertiesLoader.loadAll(resourceManager);
					cachedTextureDependencies = result.getTextureDependencies();
					lastResourceManager = resourceManager;

					if (cachedTextureDependencies != null) {
						int totalTextures = 0;
						for (Set<Identifier> textures : cachedTextureDependencies.values()) {
							totalTextures += textures.size();
						}
						LOGGER.info("[Continuity] Loaded {} CTM textures", totalTextures);
					}
				} catch (Exception e) {
					LOGGER.error("[Continuity] Failed to load CTM properties", e);
					cachedTextureDependencies = Map.of();
				}
			}
		}
	}

	@Inject(method = "<init>(Ljava/util/List;)V", at = @At("HEAD"))
	private static void continuity$beforeInit(List<AtlasSource> sources,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (cachedTextureDependencies == null || cachedTextureDependencies.isEmpty()) {
			MODIFIED_SOURCES.remove();
			return;
		}

		Set<Identifier> allExtraIds = new java.util.HashSet<>();
		cachedTextureDependencies.values().forEach(allExtraIds::addAll);

		if (allExtraIds.isEmpty()) {
			MODIFIED_SOURCES.remove();
			return;
		}

		List<AtlasSource> extraSources = new ObjectArrayList<>();
		for (Identifier extraId : allExtraIds) {
			extraSources.add(new SingleAtlasSource(extraId, Optional.empty()));
		}

		List<AtlasSource> modifiedSources = new ArrayList<>(sources.size() + extraSources.size());
		modifiedSources.addAll(extraSources);
		modifiedSources.addAll(sources);

		MODIFIED_SOURCES.set(modifiedSources);
	}

	@ModifyVariable(method = "<init>(Ljava/util/List;)V", at = @At(value = "HEAD"), argsOnly = true)
	private static List<AtlasSource> continuity$modifySources(List<AtlasSource> sources) {
		List<AtlasSource> modified = MODIFIED_SOURCES.get();
		if (modified != null) {
			MODIFIED_SOURCES.remove();
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
		AtlasLoaderLoadContext context = AtlasLoaderLoadContext.THREAD_LOCAL.get();

		if (context == null) {
			me.pepperbell.continuity.client.resource.CtmInitializationCoordinator coordinator =
					me.pepperbell.continuity.client.resource.CtmInitializationCoordinator
							.getInstance();

			me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension ext =
					coordinator.getExtension();

			if (ext == null) {
				coordinator.startReloadEarly(resourceManager);
				ext = coordinator.getExtension();
			}

			if (ext != null) {
				ext.setContext();
				context = AtlasLoaderLoadContext.THREAD_LOCAL.get();
			}
		}

		String emissiveSuffix = EmissiveSuffixLoader.getEmissiveSuffix();

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
				@SuppressWarnings("deprecation")
				Identifier atlasId = SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
				EmissiveIdMapStorage.put(atlasId, emissiveIdMap);
				LOGGER.info("[Continuity] Loaded {} emissive textures", emissiveIdMap.size());

				if (context != null) {
					context.setEmissiveIdMap(emissiveIdMap);
				}
			}
		}
	}
}
