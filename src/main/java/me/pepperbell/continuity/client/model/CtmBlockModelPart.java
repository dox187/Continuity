package me.pepperbell.continuity.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.util.CtmRenderContext;
import me.pepperbell.continuity.client.util.RenderUtil;
import me.pepperbell.continuity.impl.client.ProcessingContextImpl;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * Wraps a BlockModelPart to apply CTM (Connected Textures Mod) processing.
 * 
 * This uses lazy evaluation - the CTM logic is applied when getQuads() is called
 * during rendering, when world context is available via CtmRenderContext.
 */
public class CtmBlockModelPart implements BlockModelPart {
	public static final int PASSES = 4;
	
	private final BlockModelPart wrapped;
	private final BlockState defaultState;
	private volatile Function<Sprite, QuadProcessors.Slice> defaultSliceFunc;
	
	public CtmBlockModelPart(BlockModelPart wrapped, BlockState defaultState) {
		this.wrapped = wrapped;
		this.defaultState = defaultState;
	}
	
	@Override
	public List<BakedQuad> getQuads(@Nullable Direction side) {
		// Check if CTM is enabled
		if (!ContinuityConfig.INSTANCE.connectedTextures.get()) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
				"CTM disabled in config");
			return wrapped.getQuads(side);
		}
		
		ModelObjectsContainer container = ModelObjectsContainer.get();
		if (!container.featureStates.getConnectedTexturesState().isEnabled()) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
				"CTM feature state disabled");
			return wrapped.getQuads(side);
		}
		
		// Try to get rendering context
		CtmRenderContext.Context ctx = CtmRenderContext.get();
		if (ctx == null) {
			// No context available - silently return unmodified quads
			// This is expected in 1.21.10 - chunk builders don't go through BlockModelRenderer.render()
			return wrapped.getQuads(side);
		}
		
		me.pepperbell.continuity.client.ContinuityClient.LOGGER.info(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
			"CTM context found! Processing quads for {} at {}", ctx.state().getBlock(), ctx.pos());
		
		// Get base quads from wrapped part
		List<BakedQuad> baseQuads = wrapped.getQuads(side);
		if (baseQuads.isEmpty()) {
			return baseQuads;
		}
		
		// Apply CTM processing with world context
		return processCtmQuads(
			baseQuads,
			ctx.world(),
			ctx.pos(),
			ctx.state(),
			side
		);
	}
	
	/**
	 * Process quads with CTM logic using world context.
	 * This is where the actual CTM texture connection logic happens.
	 */
	private List<BakedQuad> processCtmQuads(
		List<BakedQuad> baseQuads,
		net.minecraft.world.BlockRenderView world,
		net.minecraft.util.math.BlockPos pos,
		BlockState state,
		@Nullable Direction side
	) {
		// Get appearance state for CTM checks
		// See comment in CtmBakedModel about why we use Direction.DOWN
		BlockState appearanceState = state.getAppearance(world, pos, Direction.DOWN, state, pos);
		
		// Get the CTM processors for this state
		Function<Sprite, QuadProcessors.Slice> sliceFunc = getSliceFunc(appearanceState);
		
		// Debug logging (first time only)
		if (defaultSliceFunc == null) {
			me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + "Processing CTM quads for block: {} at {} (side: {})", 
				state.getBlock(), pos, side);
		}
		
		// Create processing context and emitter
		ProcessingContextImpl processingContext = new ProcessingContextImpl();
		Supplier<Random> randomSupplier = () -> Random.create();
		
		// Use Fabric Rendering API for quad processing
		var renderer = net.fabricmc.fabric.api.renderer.v1.Renderer.get();
		var mutableMesh = renderer.mutableMesh();
		var emitter = mutableMesh.emitter();
		
		// Process each quad
		List<BakedQuad> result = new ArrayList<>();
		for (BakedQuad quad : baseQuads) {
			Sprite sprite = quad.sprite();
			QuadProcessors.Slice slice = sliceFunc.apply(sprite);
			
			// Debug: Check if there are any processors for this sprite
			if (defaultSliceFunc == null && slice.processors().length > 0) {
				me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
					"Found {} CTM processors for sprite: {}", slice.processors().length, sprite.getContents().getId());
			}
			
			// Convert BakedQuad to MutableQuadView for processing
			emitter.fromBakedQuad(quad);
			
			// Apply CTM processors
			boolean include = processQuadWithEmitter(
				emitter, sprite, slice, world, appearanceState, state, 
				pos, randomSupplier, processingContext
			);
			
			// Debug: Check result
			if (defaultSliceFunc == null && !include) {
				me.pepperbell.continuity.client.ContinuityClient.LOGGER.debug(me.pepperbell.continuity.client.ContinuityClient.LOG_PREFIX + 
					"Quad was discarded by CTM processing");
			}
			
			if (include) {
				// Convert processed MutableQuadView back to BakedQuad
				result.add(emitter.toBakedQuad(sprite));
			}
		}
		
		// TODO: Add any additional quads generated by processors
		// processingContext may have extra quads that need to be added
		// processingContext.outputTo() would need a way to collect BakedQuads
		
		return result;
	}
	
	/**
	 * Process a single quad through CTM processors using the emitter.
	 * Returns true if the quad should be included in output.
	 */
	private boolean processQuadWithEmitter(
		QuadEmitter emitter,
		Sprite sprite,
		QuadProcessors.Slice slice,
		net.minecraft.world.BlockRenderView world,
		BlockState appearanceState,
		BlockState state,
		net.minecraft.util.math.BlockPos pos,
		Supplier<Random> randomSupplier,
		ProcessingContextImpl processingContext
	) {
		// Process through all CTM passes
		for (int pass = 0; pass < PASSES; pass++) {
			QuadProcessor[] processors = pass == 0 ? slice.processors() : slice.multipassProcessors();
			for (QuadProcessor processor : processors) {
				QuadProcessor.ProcessingResult result = processor.processQuad(
					emitter, sprite, world, appearanceState, state, pos, 
					randomSupplier, pass, processingContext
				);
				
				if (result == QuadProcessor.ProcessingResult.NEXT_PROCESSOR) {
					continue;
				}
				if (result == QuadProcessor.ProcessingResult.NEXT_PASS) {
					break; // Move to next pass
				}
				if (result == QuadProcessor.ProcessingResult.STOP) {
					return true; // Include quad
				}
				if (result == QuadProcessor.ProcessingResult.DISCARD) {
					return false; // Discard quad
				}
			}
		}
		
		return true; // Include by default
	}
	
	protected Function<Sprite, QuadProcessors.Slice> getSliceFunc(BlockState state) {
		if (state == defaultState) {
			Function<Sprite, QuadProcessors.Slice> sliceFunc = defaultSliceFunc;
			if (sliceFunc == null) {
				synchronized (this) {
					sliceFunc = defaultSliceFunc;
					if (sliceFunc == null) {
						sliceFunc = QuadProcessors.getCache(state);
						defaultSliceFunc = sliceFunc;
					}
				}
			}
			return sliceFunc;
		}
		return QuadProcessors.getCache(state);
	}
	
	@Override
	public boolean useAmbientOcclusion() {
		return wrapped.useAmbientOcclusion();
	}
	
	@Override
	public Sprite particleSprite() {
		return wrapped.particleSprite();
	}
}
