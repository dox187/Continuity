package me.pepperbell.continuity.client.resource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public class BakedModelManagerReloadExtension {
	/**
	 * PHASE 7: Global context for PreparableModelLoadingPlugin.
	 * 
	 * <p>
	 * PreparableModelLoadingPlugin.initialize() runs on a Worker thread, but atlas creation happens
	 * on the Render thread. ThreadLocal doesn't work across threads, so we need a global volatile
	 * variable that can be accessed from any thread.
	 * 
	 * <p>
	 * This is set by PreparableModelLoadingPlugin.initialize() and read by
	 * SpriteLoaderMixin.continuity$modifySupplier() on the Render thread.
	 */
	private static volatile SpriteLoaderLoadContextImpl globalContextForInitialLoad = null;

	private final CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingResultFuture;
	private final AtomicBoolean wrapEmissiveModels = new AtomicBoolean();
	private final SpriteLoaderLoadContextImpl spriteLoaderLoadContext;
	private volatile List<QuadProcessors.ProcessorHolder> processorHolders;

	public BakedModelManagerReloadExtension(ResourceManager resourceManager,
			Executor prepareExecutor) {
		ctmLoadingResultFuture = CompletableFuture.supplyAsync(
				() -> CtmPropertiesLoader.loadAllWithState(resourceManager), prepareExecutor);
		spriteLoaderLoadContext = new SpriteLoaderLoadContextImpl(
				ctmLoadingResultFuture
						.thenApply(CtmPropertiesLoader.LoadingResult::getTextureDependencies),
				wrapEmissiveModels);
		EmissiveSuffixLoader.load(resourceManager);
		ModelWrappingHandler.resetInstance();
	}

	/**
	 * Gets the CompletableFuture for CTM properties loading. Used by CtmInitializationCoordinator
	 * to synchronize property loading completion.
	 * 
	 * @return CompletableFuture that completes when CTM properties are loaded
	 */
	public CompletableFuture<CtmPropertiesLoader.LoadingResult> getCtmLoadingFuture() {
		return ctmLoadingResultFuture;
	}

	public void setContext() {
		SpriteLoaderLoadContext.THREAD_LOCAL.set(spriteLoaderLoadContext);
		// PHASE 7: Also set global context for cross-thread access
		globalContextForInitialLoad = spriteLoaderLoadContext;
	}

	public void clearContext() {
		SpriteLoaderLoadContext.THREAD_LOCAL.set(null);
		// PHASE 7: Also clear global context
		globalContextForInitialLoad = null;
	}

	/**
	 * PHASE 7: Gets the global context for initial load.
	 * 
	 * <p>
	 * This is used by SpriteLoaderMixin when ThreadLocal is not set (e.g., during initial load when
	 * PreparableModelLoadingPlugin runs on a worker thread).
	 */
	@Nullable
	public static SpriteLoaderLoadContextImpl getGlobalContext() {
		return globalContextForInitialLoad;
	}

	/**
	 * Prepares quad processors using sprite map from StitchResult (Minecraft 1.21.10).
	 * 
	 * @param sprites Map of sprite identifiers to sprite instances from StitchResult
	 * @param missingSprite The missing sprite to use as fallback
	 */
	public void beforeBake(Map<Identifier, Sprite> sprites, Sprite missingSprite) {
		CtmPropertiesLoader.LoadingResult result = ctmLoadingResultFuture.join();

		List<QuadProcessors.ProcessorHolder> processorHolders =
				result.createProcessorHolders(spriteId -> {
					Sprite sprite = sprites.get(spriteId.getTextureId());
					if (sprite != null) {
						return sprite;
					}
					return missingSprite;
				});

		this.processorHolders = processorHolders;

		ModelWrappingHandler.setInstance(!processorHolders.isEmpty(), wrapEmissiveModels.get());
	}

	public void apply() {
		List<QuadProcessors.ProcessorHolder> processorHolders = this.processorHolders;
		if (processorHolders != null) {
			QuadProcessors.reload(processorHolders);
		}
	}

	private static class SpriteLoaderLoadContextImpl implements SpriteLoaderLoadContext {
		private final CompletableFuture<Map<Identifier, Set<Identifier>>> allExtraIdsFuture;
		private final Map<Identifier, CompletableFuture<Set<Identifier>>> extraIdsFutures =
				new Object2ObjectOpenHashMap<>();
		private final EmissiveControl blockAtlasEmissiveControl;

		public SpriteLoaderLoadContextImpl(
				CompletableFuture<Map<Identifier, Set<Identifier>>> allExtraIdsFuture,
				AtomicBoolean blockAtlasHasEmissivesHolder) {
			this.allExtraIdsFuture = allExtraIdsFuture;
			blockAtlasEmissiveControl = new EmissiveControlImpl(blockAtlasHasEmissivesHolder);
		}

		@Override
		public CompletableFuture<@Nullable Set<Identifier>> getExtraIdsFuture(Identifier atlasId) {
			return extraIdsFutures.computeIfAbsent(atlasId,
					id -> allExtraIdsFuture.thenApply(allExtraIds -> allExtraIds.get(id)));
		}

		@Override
		@Nullable
		public EmissiveControl getEmissiveControl(Identifier atlasId) {
			if (atlasId.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)) {
				return blockAtlasEmissiveControl;
			}
			return null;
		}

		private static class EmissiveControlImpl implements EmissiveControl {
			@Nullable
			private volatile Map<Identifier, Identifier> emissiveIdMap;
			private final AtomicBoolean hasEmissivesHolder;

			public EmissiveControlImpl(AtomicBoolean hasEmissivesHolder) {
				this.hasEmissivesHolder = hasEmissivesHolder;
			}

			@Override
			@Nullable
			public Map<Identifier, Identifier> getEmissiveIdMap() {
				return emissiveIdMap;
			}

			@Override
			public void setEmissiveIdMap(Map<Identifier, Identifier> emissiveIdMap) {
				this.emissiveIdMap = emissiveIdMap;
			}

			@Override
			public void markHasEmissives() {
				hasEmissivesHolder.set(true);
			}
		}
	}
}
