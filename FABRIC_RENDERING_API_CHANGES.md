# Fabric Rendering API Changes for Minecraft 1.21.10

## Material System Removal - OFFICIAL CONFIRMATION

**Status:** ✅ **CONFIRMED** - Material system completely removed from Fabric API 0.138.0+1.21.10

### Evidence from Official API Documentation

1. **Package Index Check:**
   - URL: https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/allpackages-index.html
   - Result: NO `net.fabricmc.fabric.api.renderer.v1.material` package exists

2. **Renderer v1 Package Check:**
   - URL: https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/package-summary.html
   - Related packages found:
     - ✅ `renderer.v1.mesh` (QuadEmitter, QuadView, MutableQuadView)
     - ✅ `renderer.v1.model`
     - ✅ `renderer.v1.render`
     - ✅ `renderer.v1.sprite`
     - ❌ `renderer.v1.material` (NOT PRESENT)

3. **Mesh Package Check:**
   - URL: https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/mesh/package-summary.html
   - Classes found: Mesh, MeshView, MutableMesh, QuadEmitter, QuadTransform, QuadView, ShadeMode
   - ❌ NO BlendMode, RenderMaterial, MaterialFinder classes

---

## Official Replacement APIs

### 1. BlendMode → BlockRenderLayer (Vanilla Enum)

**OLD (Fabric Material System):**
```java
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;

BlendMode blendMode = material.blendMode();
```

**NEW (Vanilla BlockRenderLayer):**
```java
import net.minecraft.client.render.BlockRenderLayer;

BlockRenderLayer renderLayer = BlockRenderLayer.SOLID; // or CUTOUT, CUTOUT_MIPPED, TRANSLUCENT, TRIPWIRE
```

**Available on QuadEmitter/MutableQuadView:**
```java
QuadEmitter renderLayer(@Nullable BlockRenderLayer renderLayer)
```

**Javadoc:**
> Controls how this quad's pixels should be blended with the scene.
> If set to `null`, `RenderLayers.getBlockLayer(BlockState)` will be used to retrieve the render layer in block contexts.

**Mapping:**
| Old BlendMode | New BlockRenderLayer |
|--------------|---------------------|
| `SOLID` | `BlockRenderLayer.SOLID` |
| `CUTOUT` | `BlockRenderLayer.CUTOUT` |
| `CUTOUT_MIPPED` | `BlockRenderLayer.CUTOUT_MIPPED` |
| `TRANSLUCENT` | `BlockRenderLayer.TRANSLUCENT` |
| N/A | `BlockRenderLayer.TRIPWIRE` (new) |

---

### 2. RenderMaterial → Direct Quad Property Methods

**OLD (Fabric Material System):**
```java
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;

MaterialFinder finder = renderer.materialFinder();
RenderMaterial material = finder
    .blendMode(BlendMode.TRANSLUCENT)
    .emissive(true)
    .diffuseShade(false)
    .ambientOcclusion(TriState.FALSE)
    .find();

emitter.material(material); // Set all properties at once
```

**NEW (Direct QuadEmitter Methods):**
```java
// Import vanilla and Fabric classes
import net.minecraft.client.render.BlockRenderLayer;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;

// Set properties directly on QuadEmitter
emitter
    .renderLayer(BlockRenderLayer.TRANSLUCENT)  // Replaces blendMode
    .emissive(true)                             // Same method name!
    .diffuseShade(false)                        // Same method name!
    .ambientOcclusion(TriState.FALSE)           // Same method name!
    .shadeMode(ShadeMode.ENHANCED);             // New! Hint for AO/diffuse
```

---

### 3. Emissive Rendering (Official Method)

**QuadEmitter.emissive(boolean) - Official Javadoc:**
```java
QuadEmitter emissive(boolean emissive)
```

> When true, this quad will be rendered at full brightness. Lightmap values provided via `QuadView.lightmap(int)` will be ignored. This is the preferred method for emissive lighting effects as some renderers with advanced lighting pipelines may not use lightmaps.
> 
> Note that vertex colors will still be modified by diffuse shading and ambient occlusion, unless disabled via `MutableQuadView.diffuseShade(boolean)` and `MutableQuadView.ambientOcclusion(TriState)`.

**Usage Example:**
```java
// For full emissive effect (no shading)
emitter
    .emissive(true)                      // Full brightness
    .diffuseShade(false)                 // Disable diffuse shading
    .ambientOcclusion(TriState.FALSE);   // Disable AO

// For emissive with shading (recommended)
emitter
    .emissive(true)                      // Full brightness
    .diffuseShade(true)                  // Keep diffuse shading
    .ambientOcclusion(TriState.DEFAULT); // Keep AO
```

---

### 4. ShadeMode Enum (NEW)

**Package:** `net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode`

**Purpose:** Hint to renderer about shading approach

**QuadEmitter.shadeMode(ShadeMode) - Official Javadoc:**
```java
QuadEmitter shadeMode(ShadeMode mode)
```

> A hint to the renderer about how this quad is intended to be shaded, for example through ambient occlusion and diffuse shading. The renderer is free to ignore this hint.
> 
> The default value is `ShadeMode.ENHANCED`.
> 
> This property is respected only in block contexts. It will not have an effect in other contexts.

**Values:** (Need to check ShadeMode enum docs for exact values)
- Likely: `ENHANCED`, `FLAT`, or similar

---

## QuadEmitter/QuadView Complete Property Reference

### Geometry Properties:
- `QuadEmitter pos(int vertexIndex, float x, float y, float z)` - Vertex positions
- `QuadEmitter normal(int vertexIndex, float x, float y, float z)` - Vertex normals
- `QuadEmitter uv(int vertexIndex, float u, float v)` - Texture coordinates
- `QuadEmitter nominalFace(@Nullable Direction face)` - Hint for quad facing
- `QuadEmitter cullFace(@Nullable Direction face)` - Culling face

### Visual Properties:
- `QuadEmitter color(int vertexIndex, int color)` - Vertex colors (ARGB)
- `QuadEmitter tintIndex(int tintIndex)` - Tint index for biome/item coloring
- `QuadEmitter lightmap(int vertexIndex, int lightmap)` - Minimum lightmap values

### Rendering Properties (Replaced Material System):
- ✅ `QuadEmitter renderLayer(@Nullable BlockRenderLayer renderLayer)` - **Replaces BlendMode**
- ✅ `QuadEmitter emissive(boolean emissive)` - **Same as old material.emissive()**
- ✅ `QuadEmitter diffuseShade(boolean shade)` - **Same as old material.diffuseShade()**
- ✅ `QuadEmitter ambientOcclusion(TriState ao)` - **Same as old material.ambientOcclusion()**
- ✅ `QuadEmitter glint(@Nullable ItemRenderState.Glint glint)` - **Glint control (items)**
- ✅ `QuadEmitter shadeMode(ShadeMode mode)` - **NEW! Shading hint**

### Utility Methods:
- `QuadEmitter copyFrom(QuadView quad)` - Copy all properties from another quad
- `QuadEmitter fromBakedQuad(BakedQuad quad)` - Import from vanilla BakedQuad
- `QuadEmitter spriteBake(Sprite sprite, int bakeFlags)` - Bake sprite UVs
- `QuadEmitter tag(int tag)` - Custom tag for filtering/transforms
- `QuadEmitter emit()` - **Finalize and emit the quad**

---

## Migration Strategy for Continuity

### Phase 1: Replace Material Imports
```java
// REMOVE THESE:
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialView;

// ADD THESE:
import net.minecraft.client.render.BlockRenderLayer;
import net.fabricmc.fabric.api.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState; // Already exists
```

### Phase 2: Update EmissiveBakedModel
Replace material array with direct quad property calls:

```java
// OLD:
private static final RenderMaterial[] EMISSIVE_MATERIALS = new RenderMaterial[16];
static {
    MaterialFinder finder = RendererAccess.INSTANCE.getRenderer().materialFinder();
    for (int i = 0; i < 16; i++) {
        EMISSIVE_MATERIALS[i] = finder
            .clear()
            .blendMode(BlendMode.TRANSLUCENT)
            .emissive(true)
            .find();
    }
}

// NEW:
private void applyEmissiveProperties(QuadEmitter emitter, int lightLevel) {
    emitter
        .renderLayer(BlockRenderLayer.TRANSLUCENT)
        .emissive(true)
        .diffuseShade(false)
        .ambientOcclusion(TriState.FALSE);
}
```

### Phase 3: Update RenderUtil
```java
// OLD:
public static RenderMaterial findOverlayMaterial(RenderMaterial baseMaterial) {
    return getMaterialFinder()
        .clear()
        .blendMode(baseMaterial.blendMode())
        .find();
}

// NEW:
public static void applyOverlayProperties(QuadEmitter emitter, BlockRenderLayer renderLayer) {
    emitter.renderLayer(renderLayer);
    // Apply other properties as needed
}
```

### Phase 4: Update Properties Classes
```java
// OverlayPropertiesSection.java
// OLD:
private BlendMode blendMode;

// NEW:
private BlockRenderLayer renderLayer;
```

---

## API Documentation Links

### Official Fabric API 0.138.0+1.21.10 Documentation:
- **Package Index:** https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/allpackages-index.html
- **Renderer v1 Package:** https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/package-summary.html
- **Mesh Package:** https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/mesh/package-summary.html
- **QuadEmitter Interface:** https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/mesh/QuadEmitter.html
- **QuadView Interface:** https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/mesh/QuadView.html
- **MutableQuadView Interface:** https://maven.fabricmc.net/docs/fabric-api-0.138.0+1.21.10/net/fabricmc/fabric/api/renderer/v1/mesh/MutableQuadView.html

---

## Summary

✅ **Material system officially removed** - Confirmed via API documentation  
✅ **Official replacement:** Direct QuadEmitter property methods  
✅ **BlendMode → BlockRenderLayer** - Vanilla enum, fully documented  
✅ **RenderMaterial → Individual setters** - emissive(), diffuseShade(), ambientOcclusion(), etc.  
✅ **MaterialFinder → NOT NEEDED** - Set properties directly on each quad  
✅ **Emissive rendering:** Official method with clear documentation  
✅ **New feature:** ShadeMode enum for shading hints  

**Next Steps:**
1. Replace all material imports with BlockRenderLayer
2. Convert material arrays to direct quad property calls
3. Update RenderUtil to work without MaterialFinder
4. Test emissive rendering with new API
5. Verify CTM works without materials (it should - CTM doesn't need materials!)
