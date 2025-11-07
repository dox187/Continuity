package me.pepperbell.continuity.test;

// Test what's available in Fabric Rendering API v1
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.client.render.BlockRenderLayer;

public class ApiTest {
    public void test() {
        // Test if we can access renderer
        Renderer renderer = Renderer.get();
        
        // Check what methods are available on QuadView
        // QuadView has methods we need
        
        // BlockRenderLayer replaced BlendMode
        BlockRenderLayer layer = BlockRenderLayer.CUTOUT_MIPPED;
    }
}
