package me.pepperbell.continuity.client.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.pepperbell.continuity.client.config.ContinuityConfig;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

@SuppressWarnings("deprecation")
public class CtmBlockStateModel extends WrappedBlockStateModel {
	private static final Logger LOGGER = LoggerFactory.getLogger("Continuity");
	private static final Predicate<Direction> NO_CULL = direction -> false;

	private final BlockState defaultState;

	public CtmBlockStateModel(BlockStateModel wrapped, BlockState defaultState) {
		super(wrapped);
		this.defaultState = defaultState;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockRenderView blockView, BlockPos pos,
			BlockState state, Random random, Predicate<@Nullable Direction> cullTest) {
		BlockState renderState = Objects.requireNonNullElse(state, defaultState);

		// DEBUG: Log EVERY emitQuads call for glass blocks
		if (renderState.getBlock().getTranslationKey().contains("glass")) {
			LOGGER.info("[CTM] CtmBlockStateModel.emitQuads() CALLED for: " + renderState
					+ " at pos: " + pos);
		}

		if (!shouldApplyCtm()) {
			if (renderState.getBlock().getTranslationKey().contains("glass")) {
				LOGGER.warn("[CTM] CTM config disabled! Config value: "
						+ ContinuityConfig.INSTANCE.connectedTextures.get());
			}
			emitWrapped(emitter, blockView, pos, renderState, random, cullTest);
			return;
		}

		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getConnectedTexturesState().isEnabled()) {
			if (renderState.getBlock().getTranslationKey().contains("glass")) {
				LOGGER.warn("[CTM] CTM feature state disabled!");
			}
			emitWrapped(emitter, blockView, pos, renderState, random, cullTest);
			return;
		}

		Random geometryRandom = random.split();
		Random processingRandom = random.split();
		Supplier<Random> randomSupplier = new SplitRandomSupplier(processingRandom);

		MutableMesh mesh = Renderer.get().mutableMesh();
		QuadEmitter collectingEmitter = mesh.emitter();
		emitWrapped(collectingEmitter, blockView, pos, renderState, geometryRandom, NO_CULL);

		Function<Sprite, QuadProcessors.Slice> sliceFunc = QuadProcessors.getCache(renderState);

		if (renderState.getBlock().getTranslationKey().contains("glass")) {
			LOGGER.info("[CTM] Processing: sliceFunc=" + (sliceFunc != null ? "present" : "NULL")
					+ ", mesh quad count: " + mesh.size());
		}

		var transform = container.ctmQuadTransform;
		transform.prepare(blockView, renderState, renderState, pos, randomSupplier, cullTest,
				sliceFunc);

		try {
			final int[] processedCount = {0};
			mesh.forEachMutable(mutableQuad -> {
				processQuad(mutableQuad, emitter, transform);
				processedCount[0]++;
			});

			if (renderState.getBlock().getTranslationKey().contains("glass")) {
				LOGGER.info("[CTM] Processed " + processedCount[0] + " quads for glass at " + pos);
			}
		} finally {
			transform.reset();
			mesh.clear();
		}
	}

	@Override
	public void addParts(Random random, List<BlockModelPart> parts) {
		wrapped.addParts(random, parts);
	}

	public BlockState getDefaultState() {
		return defaultState;
	}

	private boolean shouldApplyCtm() {
		return ContinuityConfig.INSTANCE.connectedTextures.get();
	}

	private void processQuad(MutableQuadView quad, QuadEmitter outputEmitter,
			CtmBakedModel.CtmQuadTransform transform) {
		// DEBUG: Log sprite info before transform
		Sprite sprite =
				me.pepperbell.continuity.client.util.RenderUtil.getSpriteFinder().find(quad);
		LOGGER.info("[CTM] Processing quad with sprite: "
				+ (sprite != null ? sprite.getContents().getId() : "null"));

		boolean keep = transform.transform(quad);

		if (keep) {
			outputEmitter.copyFrom(quad);
			outputEmitter.emit();
		}

		transform.processingContext.outputTo(outputEmitter);
		transform.processingContext.reset();
	}

	private void emitWrapped(QuadEmitter emitter, BlockRenderView blockView, BlockPos pos,
			BlockState state, Random random, Predicate<@Nullable Direction> cullTest) {
		if (wrapped instanceof FabricBlockStateModel fabric) {
			fabric.emitQuads(emitter, blockView, pos, state, random, cullTest);
			return;
		}

		List<BlockModelPart> parts = wrapped.getParts(random);
		for (BlockModelPart part : parts) {
			part.emitQuads(emitter, cullTest);
		}
	}

	private static final class SplitRandomSupplier implements Supplier<Random> {
		private final Random parent;

		private SplitRandomSupplier(Random parent) {
			this.parent = parent;
		}

		@Override
		public Random get() {
			return parent.split();
		}
	}
}

