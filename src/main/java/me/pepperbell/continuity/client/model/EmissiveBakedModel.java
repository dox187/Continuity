package me.pepperbell.continuity.client.model;

import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.api.client.EmissiveSpriteApi;
import me.pepperbell.continuity.client.config.ContinuityConfig;
import me.pepperbell.continuity.client.util.QuadUtil;
import me.pepperbell.continuity.client.util.RenderUtil;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

/**
 * DEPRECATED: BakedModel API was removed in Minecraft 1.21.10. Use EmissiveBlockStateModel instead.
 * 
 * This class is kept as a stub to provide access to inner transform classes which are still
 * referenced by ModelObjectsContainer.
 * 
 * @deprecated Use {@link EmissiveBlockStateModel} instead
 */
@Deprecated
public class EmissiveBakedModel {

    /**
     * Helper method to apply emissive properties directly to QuadEmitter. Replaces the old
     * RenderMaterial array approach with direct property setters.
     */
    protected static void applyEmissiveProperties(QuadEmitter emitter,
            BlockRenderLayer renderLayer) {
        emitter.renderLayer(renderLayer).emissive(true).diffuseShade(false)
                .ambientOcclusion(TriState.FALSE);
    }

    protected static class EmissiveBlockQuadTransform implements QuadTransform {
        protected QuadEmitter emitter;
        protected BlockState state;
        protected Predicate<@Nullable Direction> cullTest;

        protected boolean active;
        protected boolean calculateDefaultLayer;
        protected boolean isDefaultLayerSolid;

        @Override
        public boolean transform(MutableQuadView quad) {
            if (cullTest.test(quad.cullFace())) {
                return false;
            }

            Sprite sprite = RenderUtil.getSpriteFinder().find(quad);
            Sprite emissiveSprite = EmissiveSpriteApi.get().getEmissiveSprite(sprite);
            if (emissiveSprite != null) {
                emitter.copyFrom(quad);

                // Determine the appropriate render layer for emissive rendering
                BlockRenderLayer renderLayer = quad.renderLayer();
                if (renderLayer == null) {
                    // Default layer based on block state
                    if (calculateDefaultLayer) {
                        isDefaultLayerSolid =
                                RenderLayers.getBlockLayer(state) == BlockRenderLayer.SOLID;
                        calculateDefaultLayer = false;
                    }

                    if (isDefaultLayerSolid) {
                        renderLayer = BlockRenderLayer.CUTOUT_MIPPED;
                    } else {
                        // Use CUTOUT_MIPPED as default for emissive
                        renderLayer = BlockRenderLayer.CUTOUT_MIPPED;
                    }
                } else if (renderLayer == BlockRenderLayer.SOLID) {
                    // Solid blocks should use CUTOUT_MIPPED for emissive overlay
                    renderLayer = BlockRenderLayer.CUTOUT_MIPPED;
                }

                // Apply emissive properties directly
                applyEmissiveProperties(emitter, renderLayer);
                QuadUtil.interpolate(emitter, sprite, emissiveSprite);
                emitter.emit();
            }
            return true;
        }

        public boolean isActive() {
            return active;
        }

        public void prepare(QuadEmitter emitter, BlockState state,
                Predicate<@Nullable Direction> cullTest) {
            this.emitter = emitter;
            this.state = state;
            this.cullTest = cullTest;

            active = true;
            calculateDefaultLayer = true;
            isDefaultLayerSolid = false;
        }

        public void reset() {
            emitter = null;
            state = null;
            cullTest = null;

            active = false;
        }
    }

    protected static class EmissiveItemQuadTransform implements QuadTransform {
        protected QuadEmitter emitter;

        protected boolean active;

        @Override
        public boolean transform(MutableQuadView quad) {
            Sprite sprite = RenderUtil.getSpriteFinder().find(quad);
            Sprite emissiveSprite = EmissiveSpriteApi.get().getEmissiveSprite(sprite);
            if (emissiveSprite != null) {
                emitter.copyFrom(quad);
                // For items, use CUTOUT_MIPPED as the default emissive layer
                applyEmissiveProperties(emitter, BlockRenderLayer.CUTOUT_MIPPED);
                QuadUtil.interpolate(emitter, sprite, emissiveSprite);
                emitter.emit();
            }
            return true;
        }

        public boolean isActive() {
            return active;
        }

        public void prepare(QuadEmitter emitter) {
            this.emitter = emitter;

            active = true;
        }

        public void reset() {
            emitter = null;

            active = false;
        }
    }
}
