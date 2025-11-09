package me.pepperbell.continuity.client.processor.simple;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.mixinterface.SpriteExtension;
import me.pepperbell.continuity.client.processor.AbstractQuadProcessorFactory;
import me.pepperbell.continuity.client.processor.BaseProcessingPredicate;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import me.pepperbell.continuity.client.util.QuadUtil;
import me.pepperbell.continuity.client.util.TextureUtil;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

public class SimpleQuadProcessor implements QuadProcessor {
	private static final Logger LOGGER = LoggerFactory.getLogger("Continuity/SimpleQuadProcessor");

	protected SpriteProvider spriteProvider;
	protected ProcessingPredicate processingPredicate;

	public SimpleQuadProcessor(SpriteProvider spriteProvider,
			ProcessingPredicate processingPredicate) {
		this.spriteProvider = spriteProvider;
		this.processingPredicate = processingPredicate;
	}

	@Override
	public ProcessingResult processQuad(MutableQuadView quad, Sprite sprite,
			BlockRenderView blockView, BlockPos pos, BlockState appearanceState, BlockState state,
			Random random, int pass, ProcessingContext context) {
		// PHASE 8: Check if sprite has emissive property
		if (sprite instanceof SpriteExtension) {
			SpriteExtension spriteExt = (SpriteExtension) sprite;
			Sprite emissiveSprite = spriteExt.continuity$getEmissiveSprite();
			if (emissiveSprite != null) {
				LOGGER.debug("[Continuity] QUAD PROCESSING:");
				LOGGER.debug("  Sprite: {}", sprite.getContents().getId());
				LOGGER.debug("  Has emissive: TRUE");
				LOGGER.debug("  Emissive sprite: {}", emissiveSprite.getContents().getId());
			}
		}

		if (!processingPredicate.shouldProcessQuad(quad, sprite, blockView, pos, appearanceState,
				state, context)) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		Sprite newSprite = spriteProvider.getSprite(quad, sprite, blockView, pos, appearanceState,
				state, random, context);
		return process(quad, sprite, newSprite);
	}

	public static ProcessingResult process(MutableQuadView quad, Sprite oldSprite,
			@Nullable Sprite newSprite) {
		if (newSprite == null) {
			return ProcessingResult.STOP;
		}
		if (TextureUtil.isMissingSprite(newSprite)) {
			return ProcessingResult.NEXT_PROCESSOR;
		}
		QuadUtil.interpolate(quad, oldSprite, newSprite);
		return ProcessingResult.NEXT_PASS;
	}

	public static class Factory<T extends BaseCtmProperties>
			extends AbstractQuadProcessorFactory<T> {
		protected SpriteProvider.Factory<? super T> spriteProviderFactory;

		public Factory(SpriteProvider.Factory<? super T> spriteProviderFactory) {
			this.spriteProviderFactory = spriteProviderFactory;
		}

		@Override
		public QuadProcessor createProcessor(T properties, Sprite[] sprites) {
			return new SimpleQuadProcessor(
					spriteProviderFactory.createSpriteProvider(sprites, properties),
					BaseProcessingPredicate.fromProperties(properties));
		}

		@Override
		public int getTextureAmount(T properties) {
			return spriteProviderFactory.getTextureAmount(properties);
		}
	}
}
