package me.pepperbell.continuity.client.model;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.util.RenderUtil;
import me.pepperbell.continuity.impl.client.ProcessingContextImpl;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

/**
 * DEPRECATED: BakedModel API was removed in Minecraft 1.21.10.
 * Use CtmBlockStateModel instead.
 * 
 * This class is kept as a stub to provide access to CtmQuadTransform inner class
 * which is still referenced by ModelObjectsContainer.
 * 
 * @deprecated Use {@link CtmBlockStateModel} instead
 */
@Deprecated
public class CtmBakedModel {
	public static final int PASSES = 4;

	/**
	 * QuadTransform for CTM processing. 
	 * Still used by ModelObjectsContainer even though the outer class is deprecated.
	 */
	public static class CtmQuadTransform implements QuadTransform {
		protected final ProcessingContextImpl processingContext = new ProcessingContextImpl();

		protected BlockRenderView blockView;
		protected BlockState appearanceState;
		protected BlockState state;
		protected BlockPos pos;
		protected Supplier<Random> randomSupplier;
		protected Predicate<@Nullable Direction> cullTest;
		protected Function<Sprite, QuadProcessors.Slice> sliceFunc;

		protected boolean active;

		@Override
		public boolean transform(MutableQuadView quad) {
			if (cullTest.test(quad.cullFace())) {
				return false;
			}

			for (int pass = 0; pass < PASSES; pass++) {
				Boolean result = transformOnce(quad, pass);
				if (result != null) {
					return result;
				}
			}

			return true;
		}

		protected Boolean transformOnce(MutableQuadView quad, int pass) {
			Sprite sprite = RenderUtil.getSpriteFinder().find(quad);
			QuadProcessors.Slice slice = sliceFunc.apply(sprite);
			QuadProcessor[] processors = pass == 0 ? slice.processors() : slice.multipassProcessors();
			for (QuadProcessor processor : processors) {
				QuadProcessor.ProcessingResult result = processor.processQuad(quad, sprite, blockView, appearanceState, state, pos, randomSupplier, pass, processingContext);
				if (result == QuadProcessor.ProcessingResult.NEXT_PROCESSOR) {
					continue;
				}
				if (result == QuadProcessor.ProcessingResult.NEXT_PASS) {
					return null;
				}
				if (result == QuadProcessor.ProcessingResult.STOP) {
					return true;
				}
				if (result == QuadProcessor.ProcessingResult.DISCARD) {
					return false;
				}
			}
			return true;
		}

		public boolean isActive() {
			return active;
		}

		public void prepare(BlockRenderView blockView, BlockState appearanceState, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, Predicate<@Nullable Direction> cullTest, Function<Sprite, QuadProcessors.Slice> sliceFunc) {
			this.blockView = blockView;
			this.appearanceState = appearanceState;
			this.state = state;
			this.pos = pos;
			this.randomSupplier = randomSupplier;
			this.cullTest = cullTest;
			this.sliceFunc = sliceFunc;

			active = true;
		}

		public void reset() {
			blockView = null;
			appearanceState = null;
			state = null;
			pos = null;
			randomSupplier = null;
			cullTest = null;
			sliceFunc = null;

			active = false;

			processingContext.reset();
		}
	}
}
