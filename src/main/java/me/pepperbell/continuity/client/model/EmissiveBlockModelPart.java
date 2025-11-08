package me.pepperbell.continuity.client.model;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.api.client.EmissiveSpriteApi;
import me.pepperbell.continuity.client.util.QuadUtil;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

/**
 * Wraps a BlockModelPart to add emissive texture variants. Processes each quad to check for
 * emissive sprites and adds emissive versions.
 * 
 * @since 1.21.10 migration
 */
public class EmissiveBlockModelPart extends WrappedBlockModelPart {
	/**
	 * Helper method to apply emissive properties directly to QuadEmitter. Replaces the old
	 * RenderMaterial array approach with direct property setters.
	 */
	private static void applyEmissiveProperties(QuadEmitter emitter, BlockRenderLayer renderLayer) {
		emitter.renderLayer(renderLayer).emissive(true).diffuseShade(false)
				.ambientOcclusion(TriState.FALSE);
	}

	private final BlockState state;

	public EmissiveBlockModelPart(BlockModelPart wrapped, BlockState state) {
		super(wrapped);
		this.state = state;
	}

	@Override
	protected List<BakedQuad> processQuads(List<BakedQuad> quads, @Nullable Direction side) {
		List<BakedQuad> result = new ArrayList<>(quads);

		// Use Fabric Rendering API to add emissive variants
		var renderer = Renderer.get();
		var mutableMesh = renderer.mutableMesh();
		var emitter = mutableMesh.emitter();

		for (BakedQuad quad : quads) {
			Sprite sprite = quad.sprite();
			Sprite emissiveSprite = EmissiveSpriteApi.get().getEmissiveSprite(sprite);

			if (emissiveSprite != null) {
				// Convert BakedQuad to MutableQuadView for processing
				emitter.fromBakedQuad(quad);

				// Determine appropriate render layer for emissive overlay
				BlockRenderLayer renderLayer = determineEmissiveRenderLayer(quad);

				// Apply emissive properties directly
				applyEmissiveProperties(emitter, renderLayer);
				QuadUtil.interpolate(emitter, sprite, emissiveSprite);

				// Convert back to BakedQuad
				result.add(emitter.toBakedQuad(emissiveSprite));
			}
		}

		return result;
	}

	private BlockRenderLayer determineEmissiveRenderLayer(BakedQuad quad) {
		// Get the quad's current render layer if set
		// For vanilla quads, determine based on block's render layer
		BlockRenderLayer blockLayer = RenderLayers.getBlockLayer(state);

		if (blockLayer == BlockRenderLayer.SOLID) {
			// Solid blocks should use CUTOUT_MIPPED for emissive overlay
			return BlockRenderLayer.CUTOUT_MIPPED;
		} else {
			// For other layers, use CUTOUT_MIPPED as default for emissive
			return BlockRenderLayer.CUTOUT_MIPPED;
		}
	}
}
