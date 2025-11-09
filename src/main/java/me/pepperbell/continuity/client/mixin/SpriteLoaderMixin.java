package me.pepperbell.continuity.client.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;


import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.pepperbell.continuity.client.mixinterface.SpriteExtension;
import me.pepperbell.continuity.client.mixinterface.StitchResultExtension;
import me.pepperbell.continuity.client.resource.AtlasLoaderInitContext;
import me.pepperbell.continuity.client.resource.AtlasLoaderLoadContext;
import me.pepperbell.continuity.client.resource.SpriteLoaderLoadContext;
import me.pepperbell.continuity.client.resource.SpriteLoaderStitchContext;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.client.texture.SpriteOpener;
import net.minecraft.util.Identifier;

@Mixin(SpriteLoader.class)
abstract class SpriteLoaderMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("Continuity/SpriteLoader");

	@Shadow
	@Final
	private Identifier id;

	@ModifyArg(
			method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/Identifier;ILjava/util/concurrent/Executor;Ljava/util/Set;)Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE",
					target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
					ordinal = 0),
			index = 0)
	private Supplier<List<Function<SpriteOpener, SpriteContents>>> continuity$modifySupplier(
			Supplier<List<Function<SpriteOpener, SpriteContents>>> supplier) {
		// PHASE 5 TEST: SpriteLoaderMixin.modifySupplier() called
		LOGGER.debug("[Continuity] SpriteLoaderMixin.modifySupplier() - Atlas: {}", id);

		SpriteLoaderLoadContext context = SpriteLoaderLoadContext.THREAD_LOCAL.get();

		// PHASE 7: Fallback to global context if ThreadLocal is not set
		if (context == null) {
			context = me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension
					.getGlobalContext();
			if (context != null) {
				LOGGER.info(
						"[Continuity] PHASE 7: Using global context for atlas: {} (ThreadLocal was null)",
						id);
			}
		}

		if (context != null) {
			LOGGER.debug("[Continuity] SpriteLoaderLoadContext found for atlas: {}", id);
			CompletableFuture<@Nullable Set<Identifier>> extraIdsFuture =
					context.getExtraIdsFuture(id);
			SpriteLoaderLoadContext.EmissiveControl emissiveControl =
					context.getEmissiveControl(id);
			if (emissiveControl != null) {
				return () -> {
					AtlasLoaderInitContext initContext = extraIdsFuture::join;
					AtlasLoaderInitContext.THREAD_LOCAL.set(initContext);
					AtlasLoaderInitContext.GLOBAL_CONTEXT.set(initContext); // PHASE 7: Set global
					AtlasLoaderLoadContext.THREAD_LOCAL.set(emissiveControl::setEmissiveIdMap);
					List<Function<SpriteOpener, SpriteContents>> list = supplier.get();
					AtlasLoaderInitContext.THREAD_LOCAL.set(null);
					AtlasLoaderInitContext.GLOBAL_CONTEXT.set(null); // PHASE 7: Clear global
					AtlasLoaderLoadContext.THREAD_LOCAL.set(null);
					return list;
				};
			}
			return () -> {
				AtlasLoaderInitContext initContext = extraIdsFuture::join;
				AtlasLoaderInitContext.THREAD_LOCAL.set(initContext);
				AtlasLoaderInitContext.GLOBAL_CONTEXT.set(initContext); // PHASE 7: Set global
				List<Function<SpriteOpener, SpriteContents>> list = supplier.get();
				AtlasLoaderInitContext.THREAD_LOCAL.set(null);
				AtlasLoaderInitContext.GLOBAL_CONTEXT.set(null); // PHASE 7: Clear global
				return list;
			};
		}
		return supplier;
	}

	@ModifyArg(
			method = "load(Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/Identifier;ILjava/util/concurrent/Executor;Ljava/util/Set;)Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE",
					target = "Ljava/util/concurrent/CompletableFuture;thenApply(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;",
					ordinal = 0),
			index = 0)
	private Function<List<SpriteContents>, SpriteLoader.StitchResult> continuity$modifyFunction(
			Function<List<SpriteContents>, SpriteLoader.StitchResult> function) {
		// PHASE 5 TEST: SpriteLoaderMixin.modifyFunction() called
		LOGGER.debug("[Continuity] SpriteLoaderMixin.modifyFunction() - Atlas: {}", id);

		SpriteLoaderLoadContext context = SpriteLoaderLoadContext.THREAD_LOCAL.get();
		if (context != null) {
			LOGGER.debug(
					"[Continuity] SpriteLoaderLoadContext found for modifyFunction - Atlas: {}",
					id);
			SpriteLoaderLoadContext.EmissiveControl emissiveControl =
					context.getEmissiveControl(id);
			if (emissiveControl != null) {
				LOGGER.debug("[Continuity] EmissiveControl found - will process emissive sprites");
				return spriteContentsList -> {
					Map<Identifier, Identifier> emissiveIdMap = emissiveControl.getEmissiveIdMap();
					if (emissiveIdMap != null) {
						LOGGER.debug(
								"[Continuity] Emissive sprite mapping available, attaching to stitch context");
						SpriteLoaderStitchContext.THREAD_LOCAL.set(new SpriteLoaderStitchContext() {
							@Override
							public Map<Identifier, Identifier> getEmissiveIdMap() {
								return emissiveIdMap;
							}

							@Override
							public void markHasEmissives() {
								emissiveControl.markHasEmissives();
							}
						});
						SpriteLoader.StitchResult result = function.apply(spriteContentsList);
						SpriteLoaderStitchContext.THREAD_LOCAL.set(null);
						return result;
					}
					return function.apply(spriteContentsList);
				};
			}
		}
		return function;
	}

	@Inject(method = "stitch(Ljava/util/List;ILjava/util/concurrent/Executor;)Lnet/minecraft/client/texture/SpriteLoader$StitchResult;",
			at = @At("RETURN"))
	private void continuity$onReturnStitch(List<SpriteContents> spriteContentsList,
			int mipmapLevels, Executor executor,
			CallbackInfoReturnable<SpriteLoader.StitchResult> cir) {
		// PHASE 5 TEST: Stitch completed, emissive sprites should be attached
		LOGGER.debug("[Continuity] SpriteLoaderMixin.onReturnStitch() called");

		Map<Identifier, Sprite> sprites =
				((StitchResultExtension) (Object) cir.getReturnValue()).continuity$getSprites();

		SpriteLoaderStitchContext context = SpriteLoaderStitchContext.THREAD_LOCAL.get();
		if (context != null) {
			LOGGER.debug(
					"[Continuity] SpriteLoaderStitchContext available - processing emissive sprites");
			Map<Identifier, Identifier> emissiveIdMap = context.getEmissiveIdMap();
			LOGGER.debug("[Continuity] Processing {} emissive sprite mappings",
					emissiveIdMap.size());

			emissiveIdMap.forEach((id, emissiveId) -> {
				Sprite sprite = sprites.get(id);
				if (sprite != null) {
					Sprite emissiveSprite = sprites.get(emissiveId);
					if (emissiveSprite != null) {
						((SpriteExtension) sprite).continuity$setEmissiveSprite(emissiveSprite);
						context.markHasEmissives();
						LOGGER.debug("[Continuity] Attached emissive sprite: {} -> {}", id,
								emissiveId);
					} else {
						LOGGER.debug("[Continuity] Emissive sprite not found: {}", emissiveId);
					}
				}
			});
		} else {
			LOGGER.debug(
					"[Continuity] No SpriteLoaderStitchContext - emissive sprites not processed");
		}
	}
}
