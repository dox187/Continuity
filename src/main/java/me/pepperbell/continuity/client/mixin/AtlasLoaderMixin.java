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
import me.pepperbell.continuity.client.resource.AtlasLoaderInitContext;
import me.pepperbell.continuity.client.resource.AtlasLoaderLoadContext;
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

	@ModifyVariable(method = "<init>(Ljava/util/List;)V", at = @At(value = "LOAD", ordinal = 0),
			argsOnly = true, ordinal = 0)
	private List<AtlasSource> continuity$modifySources(List<AtlasSource> sources) {
		AtlasLoaderInitContext context = AtlasLoaderInitContext.THREAD_LOCAL.get();

		// PHASE 7: Fallback to global context if ThreadLocal is not set
		if (context == null) {
			context = AtlasLoaderInitContext.GLOBAL_CONTEXT.get();
		}

		if (context != null) {
			Set<Identifier> extraIds = context.getExtraIds();
			if (extraIds != null && !extraIds.isEmpty()) {
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
			}
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
