package me.pepperbell.continuity.client.resource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.pepperbell.continuity.client.mixinterface.AtlasManagerAccess;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public class BakedModelManagerReloadExtension implements BakedModelManagerBakeContext {
	private final CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingResultFuture;
	private final AtomicBoolean wrapEmissiveModels = new AtomicBoolean();
	private final SpriteLoaderLoadContextImpl spriteLoaderLoadContext;
	private volatile List<QuadProcessors.ProcessorHolder> processorHolders;

	public BakedModelManagerReloadExtension(ResourceManager resourceManager, Executor prepareExecutor) {
		ctmLoadingResultFuture = CompletableFuture.supplyAsync(() -> CtmPropertiesLoader.loadAllWithState(resourceManager), prepareExecutor);
		spriteLoaderLoadContext = new SpriteLoaderLoadContextImpl(ctmLoadingResultFuture.thenApply(CtmPropertiesLoader.LoadingResult::getTextureDependencies), wrapEmissiveModels);
		EmissiveSuffixLoader.load(resourceManager);
		ModelWrappingHandler.resetInstance();
	}

	public void setContext() {
		SpriteLoaderLoadContext.THREAD_LOCAL.set(spriteLoaderLoadContext);
	}

	public void clearContext() {
		SpriteLoaderLoadContext.THREAD_LOCAL.set(null);
	}

	@Override
	public void beforeBake(Map<Identifier, AtlasManager.Metadata> preparations) {
		CtmPropertiesLoader.LoadingResult result = ctmLoadingResultFuture.join();

		// Access AtlasManager from MinecraftClient to get sprites
		AtlasManager atlasManager = ((AtlasManagerAccess) MinecraftClient.getInstance().getBakedModelManager()).continuity$getAtlasManager();
		
		// Get the block atlas texture
		// In 1.21.10, atlas IDs changed from "minecraft:textures/atlas/blocks.png" to "minecraft:blocks"
		var blockAtlas = atlasManager.getAtlasTexture(Identifier.of("minecraft", "blocks"));
		
		List<QuadProcessors.ProcessorHolder> processorHolders = result.createProcessorHolders(spriteId -> {
			// Get sprite from the atlas texture directly using the texture ID
			// SpriteIdentifier contains both atlas ID and texture ID - we only need the texture ID
			return blockAtlas.getSprite(spriteId.getTextureId());
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
		private final Map<Identifier, CompletableFuture<Set<Identifier>>> extraIdsFutures = new Object2ObjectOpenHashMap<>();
		private final EmissiveControl blockAtlasEmissiveControl;

		public SpriteLoaderLoadContextImpl(CompletableFuture<Map<Identifier, Set<Identifier>>> allExtraIdsFuture, AtomicBoolean blockAtlasHasEmissivesHolder) {
			this.allExtraIdsFuture = allExtraIdsFuture;
			blockAtlasEmissiveControl = new EmissiveControlImpl(blockAtlasHasEmissivesHolder);
		}

		@Override
		public CompletableFuture<@Nullable Set<Identifier>> getExtraIdsFuture(Identifier atlasId) {
			return extraIdsFutures.computeIfAbsent(atlasId, id -> allExtraIdsFuture.thenApply(allExtraIds -> allExtraIds.get(id)));
		}

		@Override
		@Nullable
		public EmissiveControl getEmissiveControl(Identifier atlasId) {
			// In 1.21.10, atlas IDs changed from "minecraft:textures/atlas/blocks.png" to "minecraft:blocks"
			if (atlasId.equals(Identifier.of("minecraft", "blocks"))) {
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
