# Phase 3: Model System Migration Plan

## Overview
Migrate from BakedModel system to BlockStateModel system in Minecraft 1.21.10

## Architecture Changes

### Old System (1.21.4)
```
BakedModel (interface)
└── WrapperBakedModel (class) - Fabric API
    ├── EmissiveBakedModel - adds emissive rendering
    └── CtmBakedModel - adds connected textures

Methods:
- getQuads(BlockState, Direction, Random) → List<BakedQuad>
- emitBlockQuads(...) - Fabric Rendering API
- emitItemQuads(...) - Fabric Rendering API
```

### New System (1.21.10)
```
BlockStateModel (interface)
└── WrappedBlockStateModel (custom base class) ✅ CREATED
    ├── EmissiveBlockStateModel - adds emissive rendering
    └── CtmBlockStateModel - adds connected textures

BlockStateModel → addParts() → List<BlockModelPart> → getQuads() → List<BakedQuad>

Methods:
- addParts(Random, List<BlockModelPart>) - adds parts to list
- particleSprite() - returns particle texture
- Fabric integration: TBD (research needed)
```

## Key Discoveries

✅ **Good News**:
1. `BakedQuad` still exists - no changes to quad structure
2. `BlockModelPart.getQuads()` exists - can still get/modify quads
3. Wrapping pattern is still possible via composition

⚠️ **Challenges**:
1. `WrapperBakedModel` removed - need custom `WrappedBlockStateModel`
2. Fabric Rendering API integration unclear
   - `emitBlockQuads()` and `emitItemQuads()` might have changed
   - Material/BlendMode imports failing (cascading error?)
3. Need to understand how to intercept and modify `BlockModelPart` instances

## Migration Strategy

### Step 1: Create Base Wrapper ✅ DONE
- [x] Create `WrappedBlockStateModel` class
- [x] Implements `BlockStateModel` interface
- [x] Uses composition pattern

### Step 2: Understand Fabric Integration ⚠️ BLOCKED
- [x] Research how Fabric Rendering API integrates with BlockStateModel
- [ ] Find replacement for `emitBlockQuads()` and `emitItemQuads()`
- [x] Determine if we wrap at BlockStateModel level or BlockModelPart level
  - **Decision**: Wrap at BlockModelPart level with lazy evaluation
- [ ] Check if FabricBlockStateModel or FabricBlockModelPart interfaces exist

**🚨 CRITICAL BLOCKER DISCOVERED**:
- Fabric Rendering API v1 material package (`BlendMode`, `RenderMaterial`, `MaterialFinder`) **cannot be imported**
- Classes exist in JAR but fail compilation: "package does not exist"
- Likely Loom remapping issue or intentional API deprecation in 7.2.x
- 20+ files affected (emissive rendering, overlay materials, utilities)
- **Temporary Solution**: Stub out material code, use vanilla `BlockRenderLayer`
- **Impact**: Emissive features temporarily disabled until API resolved

### Step 3: Create BlockModelPart Wrapper
Option A: Wrap entire BlockStateModel (current approach)
- Override `addParts()` to modify the parts list after getting it from wrapped model

Option B: Wrap individual BlockModelPart instances
- Create `WrappedBlockModelPart` class
- Each part wraps and modifies quads from the original part

### Step 4: Migrate EmissiveBakedModel
- [ ] Rename to `EmissiveBlockStateModel`
- [ ] Extend `WrappedBlockStateModel`
- [ ] Override `addParts()` method
- [ ] Wrap each BlockModelPart to apply emissive materials
- [ ] Preserve quad transformation logic
- [ ] Handle Fabric Rendering API integration

### Step 5: Migrate CtmBakedModel
- [ ] Rename to `CtmBlockStateModel`
- [ ] Extend `WrappedBlockStateModel`
- [ ] Override `addParts()` method
- [ ] Apply CTM processing to quads within each part
- [ ] Preserve connection logic and sprite selection

### Step 6: Update ModelWrappingHandler
- [ ] Remove `ModelIdentifier` usage
- [ ] Use `BlockState` directly for mapping
- [ ] Update wrapping logic for BlockStateModel
- [ ] Update Fabric Model Loading Plugin integration

### Step 7: Fix Remaining Files
- [ ] Update `LayerRenderStateMixin` - remove BakedModel references
- [ ] Update `SpriteCalculator` if needed
- [ ] Fix any other BakedModel usage

## Critical Questions to Research

1. **Fabric Rendering API Integration**
   - How does Fabric extend BlockStateModel?
   - What replaced `emitBlockQuads()` and `emitItemQuads()`?
   - Are there new Fabric interfaces for BlockStateModel/BlockModelPart?

2. **Model Wrapping Approach**
   - Should we wrap at BlockStateModel level or BlockModelPart level?
   - How do we intercept quad emission for emissive/CTM processing?
   - Can we still use QuadTransform with the new system?

3. **Performance Considerations**
   - Is creating wrapper BlockModelPart instances expensive?
   - Should parts be cached?
   - How does this affect render performance?

## Implementation Notes

### Current Code Structure (EmissiveBakedModel)
```java
public class EmissiveBakedModel extends WrapperBakedModel {
    @Override
    public void emitBlockQuads(...) {
        // Transform quads using QuadTransform
        // Add emissive variants of quads
    }
    
    @Override
    public void emitItemQuads(...) {
        // Transform item quads
    }
}
```

### Proposed New Structure (Option A)
```java
public class EmissiveBlockStateModel extends WrappedBlockStateModel {
    @Override
    public void addParts(Random random, List<BlockModelPart> parts) {
        // Get parts from wrapped model
        wrapped.addParts(random, parts);
        
        // Wrap each part to add emissive processing
        for (int i = 0; i < parts.size(); i++) {
            parts.set(i, new EmissiveBlockModelPart(parts.get(i)));
        }
    }
}

class EmissiveBlockModelPart implements BlockModelPart {
    private final BlockModelPart wrapped;
    
    @Override
    public List<BakedQuad> getQuads(Direction side) {
        List<BakedQuad> quads = wrapped.getQuads(side);
        // Process quads to add emissive variants
        return processedQuads;
    }
}
```

### Proposed New Structure (Option B - if Fabric provides hooks)
```java
public class EmissiveBlockStateModel extends WrappedBlockStateModel 
        implements FabricBlockStateModel { // If this exists
    
    @Override
    public void emitParts(...) { // Or whatever the new Fabric method is
        // Use Fabric's new rendering API
    }
}
```

## Next Actions

1. ✅ Research Fabric Rendering API v1 for 1.21.10 - COMPLETED WITH BLOCKER
   - ✅ BlockStateModel extensions - none found specific to Fabric
   - ✅ Quad emission methods - use standard BlockModelPart.getQuads()
   - ❌ Material/blend mode API - **CANNOT IMPORT, BLOCKER DISCOVERED**

2. ✅ Create BlockModelPart wrapper class - COMPLETED
   - ✅ `CtmBlockModelPart` skeleton created
   - ✅ Lazy evaluation approach verified
   - ⏳ Full CTM logic port - blocked by material API issues

3. 🚨 **NEW PRIORITY**: Resolve Material System Import Failures
   - [ ] Stub out all material-related code temporarily
   - [ ] Replace `BlendMode` with vanilla `BlockRenderLayer` 
   - [ ] Comment out `RenderMaterial` and `MaterialFinder` usage
   - [ ] Focus on core CTM neighbor-checking (doesn't need materials)
   - [ ] Defer emissive/overlay features until API resolved

4. ⏭️ Migrate CTM logic (non-material parts first)
   - Focus on neighbor connection checking
   - Skip material/emissive code temporarily
   - Get basic CTM working first

## Reference Files

- ✅ `BlockStateModel.java` - extracted and analyzed
- ✅ `BlockModelPart.java` - extracted and analyzed
- ✅ `SimpleBlockStateModel.java` - extracted and analyzed
- ✅ `BakedQuad.java` - verified still exists
- ⏳ Fabric Rendering API - needs research

## Status

- Phase 1: ✅ Complete (package relocations)
- Phase 3: 🔄 20% Complete (research done, base class created)
- Blocking Issue: Need to understand Fabric Rendering API integration
