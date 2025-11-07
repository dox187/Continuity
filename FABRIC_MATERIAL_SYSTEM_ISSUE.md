# Fabric Rendering API Material System Import Issue

**Date**: November 6, 2025  
**Severity**: CRITICAL BLOCKER  
**Status**: Under Investigation  

## Problem Summary

The Fabric Rendering API v1 material package (`net.fabricmc.fabric.api.renderer.v1.material`) cannot be imported during compilation, despite the classes physically existing in the JAR file.

## Error Message

```
error: package net.fabricmc.fabric.api.renderer.v1.material does not exist
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
                                                   ^
```

## Investigation Results

### ✅ What Works

- **Other Fabric Renderer API classes compile successfully**:
  - `net.fabricmc.fabric.api.renderer.v1.Renderer` ✓
  - `net.fabricmc.fabric.api.renderer.v1.mesh.MutableMesh` ✓
  - `net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter` ✓
  - `net.fabricmc.fabric.api.renderer.v1.mesh.QuadView` ✓
  - All mesh-related classes work fine

### ❌ What Doesn't Work

- **Material package specifically fails**:
  - `net.fabricmc.fabric.api.renderer.v1.material.BlendMode` ✗
  - `net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial` ✗
  - `net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder` ✗
  - `net.fabricmc.fabric.api.renderer.v1.material.MaterialView` ✗
  - All material-related classes fail to import

### ✅ Classes Exist in JAR

Confirmed by extracting `fabric-renderer-api-v1-5.0.2+84404cdd04.jar`:

```
net/fabricmc/fabric/api/renderer/v1/material/BlendMode.class
net/fabricmc/fabric/api/renderer/v1/material/GlintMode.class
net/fabricmc/fabric/api/renderer/v1/material/MaterialFinder.class
net/fabricmc/fabric/api/renderer/v1/material/MaterialView.class
net/fabricmc/fabric/api/renderer/v1/material/RenderMaterial.class
net/fabricmc/fabric/api/renderer/v1/material/ShadeMode.class
```

**The classes physically exist in the JAR file!**

### ✅ Dependency Configuration

Verified that Fabric Renderer API is on the compile classpath:

```
compileClasspath
+--- remapped.net.fabricmc.fabric-api:fabric-renderer-api-v1-22e4ff05:7.2.2+3706cdf36f
```

Version: `7.2.2+3706cdf36f` (remapped by Loom)

## Root Cause Hypothesis

The material system has been **deprecated or removed from the public API surface** in Fabric Renderer API v1 7.2.x for Minecraft 1.21.10. Possible reasons:

1. **Intentional API Deprecation**: Fabric may have removed material system in favor of vanilla rendering
2. **Loom Remapping Issue**: The classes exist but aren't being exposed during remapping
3. **Breaking Change**: Material system refactored but not yet documented

## Affected Code

### Files with Import Errors (20+)

**Emissive Rendering**:
- `EmissiveBakedModel.java` - Heavy material usage for emissive quads
- `EmissiveBlockModelPart.java` - Material finder and blend mode logic

**Utilities**:
- `RenderUtil.java` - `findOverlayMaterial()`, `getMaterialFinder()` methods
- `QuadUtil.java` - `emitOverlayQuad()` takes RenderMaterial parameter

**Overlay Processors**:
- `SimpleOverlayQuadProcessor.java` - Uses BlendMode and RenderMaterial
- `StandardOverlayQuadProcessor.java` - Uses BlendMode and RenderMaterial

**Properties**:
- `OverlayPropertiesSection.java` - BlendMode field for layer property

**Test Files**:
- `ApiTest.java` - Uses BlendMode enum

### Typical Usage Pattern

```java
// Material Finder (FAILS)
MaterialFinder finder = Renderer.get().materialFinder();
RenderMaterial material = finder
    .blendMode(BlendMode.TRANSLUCENT)
    .emissive(true)
    .find();

// Quad Emission with Material (FAILS)
emitter.material(material);
emitter.emit();

// Overlay Material (FAILS)
RenderMaterial overlayMat = RenderUtil.findOverlayMaterial(BlendMode.CUTOUT_MIPPED, tintBlock);
QuadUtil.emitOverlayQuad(emitter, face, sprite, color, overlayMat);
```

## Vanilla Replacement Available

Minecraft 1.21.10 has `BlockRenderLayer` enum that can partially replace `BlendMode`:

### BlendMode → BlockRenderLayer Mapping

```java
// OLD (Fabric)
BlendMode.DEFAULT         → BlockRenderLayer.SOLID
BlendMode.CUTOUT_MIPPED   → BlockRenderLayer.CUTOUT_MIPPED
BlendMode.CUTOUT          → BlockRenderLayer.CUTOUT
BlendMode.TRANSLUCENT     → BlockRenderLayer.TRANSLUCENT
BlendMode.SOLID           → BlockRenderLayer.SOLID
```

### BlockRenderLayer Enum

Located: `net.minecraft.client.render.BlockRenderLayer`

```java
public enum BlockRenderLayer {
    SOLID,              // Opaque, 4MB buffer, mipmap
    CUTOUT_MIPPED,      // Opaque cutout, 4MB buffer, mipmap
    CUTOUT,             // Opaque cutout, 768KB buffer, no mipmap
    TRANSLUCENT,        // Translucent, 768KB buffer, mipmap
    TRIPWIRE;           // Special tripwire, 1.5KB buffer, mipmap
}
```

**However**: `BlockRenderLayer` only replaces `BlendMode`. There's no replacement for:
- `RenderMaterial` - Combines blend mode, emissive flag, etc.
- `MaterialFinder` - Builder pattern for creating materials
- Emissive flag integration

## Workaround Strategy

### Phase 1: Stub Out Material Code (1-2 hours)

1. **Update RenderUtil.java**:
   ```java
   // Comment out material finder
   // private static final ThreadLocal<MaterialFinder> MATERIAL_FINDER = ...
   
   // Simplify material methods
   public static BlockRenderLayer findOverlayLayer(BlockRenderLayer layer, @Nullable BlockState tintBlock) {
       // TODO: Fabric material system removed/changed in 1.21.10
       return layer; // Just return the input layer for now
   }
   ```

2. **Update Properties Classes**:
   ```java
   // Replace BlendMode with BlockRenderLayer
   protected BlockRenderLayer layer = BlockRenderLayer.CUTOUT_MIPPED;
   ```

3. **Comment Out Emissive Material Code**:
   ```java
   // Temporarily disable emissive features
   // TODO: Re-enable when Fabric material API resolved
   ```

4. **Update Quad Utilities**:
   ```java
   // Remove RenderMaterial parameter
   public static void emitOverlayQuad(QuadEmitter emitter, Direction face, Sprite sprite, int color) {
       // Emit without material for now
   }
   ```

### Phase 2: Focus on Core CTM (2-3 hours)

Core CTM logic (neighbor checking, texture selection) **does not require materials**:
- Connection checking works without materials
- Sprite replacement works without materials
- Quad transformation works without materials

**Defer emissive and overlay features** until material API resolved.

### Phase 3: Investigate Solutions (later)

1. **Check Fabric GitHub**: Look for 1.21.10 migration notes
2. **Ask Fabric Discord**: Report the import issue
3. **Alternative APIs**: Research if vanilla provides material equivalents
4. **Fabric Indigo**: Check if Indigo (Fabric's renderer implementation) has workarounds

## Impact Assessment

### High Priority (Blocks Core Functionality)
- ✅ CTM neighbor checking - **NOT AFFECTED** (doesn't use materials)
- ✅ Texture selection - **NOT AFFECTED** (doesn't use materials)
- ✅ Basic connected textures - **CAN PROCEED** without materials

### Medium Priority (Enhanced Features)
- ⚠️ Emissive textures - **BLOCKED** (heavily uses materials)
- ⚠️ Overlay CTM - **PARTIALLY BLOCKED** (uses materials for layer)
- ⚠️ Tint blocks - **PARTIALLY BLOCKED** (material-based)

### Low Priority (Polish)
- ⚠️ Advanced blend modes - **BLOCKED** (requires RenderMaterial)
- ⚠️ Custom render layers - **BLOCKED** (requires MaterialFinder)

## Recommended Action Plan

1. **Immediate** (Next 1-2 hours):
   - Stub out all material-related code
   - Replace BlendMode with BlockRenderLayer where possible
   - Focus on getting core CTM logic working

2. **Short Term** (Next session):
   - Implement basic CTM without materials
   - Test glass connections, bookshelf CTM
   - Verify neighbor checking works

3. **Medium Term** (When API resolved):
   - Research official Fabric migration path
   - Re-enable emissive features properly
   - Add overlay material support back

## Success Criteria (Interim)

- [ ] Project compiles without material import errors
- [ ] Core CTM works (glass connections)
- [ ] Basic texture selection works
- [ ] Neighbor checking functional
- ⏳ Emissive features temporarily disabled (acceptable)
- ⏳ Advanced materials temporarily disabled (acceptable)

## References

- Fabric Renderer API v1: `fabric-renderer-api-v1-7.2.2+3706cdf36f.jar`
- Vanilla BlockRenderLayer: `net.minecraft.client.render.BlockRenderLayer`
- Fabric Blog (1.21.9/1.21.10): https://fabricmc.net/2025/09/23/1219.html
- Migration Primer: https://gist.github.com/ChampionAsh5357/d895a7b1a34341e19c80870720f9880f

---

**Next Step**: Begin Phase 1 - Stub out material code to get project compiling.
