# CTM Migration Solution for Minecraft 1.21.10

## Problem Statement

**Goal**: Migrate Connected Textures Mod (CTM) from `BakedModel` system (1.21.4) to `BlockStateModel` system (1.21.10)

**Challenge**: CTM requires access to neighboring blocks to determine texture connections, but the new `BlockStateModel.addParts()` method has no world context.

## Architecture Analysis

### Old System (1.21.4) - Working ✓

```java
public class CtmBakedModel extends WrapperBakedModel {
    @Override
    public void emitBlockQuads(
        BlockRenderView world,     // ← World context available!
        BlockState state,
        BlockPos pos,              // ← Position available!
        Supplier<Random> random,
        RenderContext context
    ) {
        // Can check neighbors immediately
        boolean connectNorth = shouldConnect(world, pos, Direction.NORTH);
        boolean connectSouth = shouldConnect(world, pos, Direction.SOUTH);
        // ... generate connected texture quads
    }
}
```

### New System (1.21.10) - Context Problem ✗

```java
public interface BlockStateModel {
    // NO world or position context!
    void addParts(Random random, List<BlockModelPart> parts);
}
```

## The Solution: Lazy Evaluation via CtmBlockModelPart

### Key Discovery

After reading Minecraft 1.21.10 sources, I discovered that `BlockModelPart.getQuads()` **IS called during rendering with world context**:

```java
// From BlockModelRenderer.java lines 80-120
public void render(
    BlockRenderView world,        // ← Context IS available here!
    List<BlockModelPart> parts,   // ← Parts from addParts()
    BlockState state,
    BlockPos pos,                 // ← Position available!
    MatrixStack matrices,
    VertexConsumer vertexConsumer,
    boolean cull,
    int overlay
) {
    for (BlockModelPart blockModelPart : parts) {
        for (Direction direction : DIRECTIONS) {
            // getQuads() is called HERE, during rendering!
            List<BakedQuad> list = blockModelPart.getQuads(direction);
            
            // World context is used for culling
            if (shouldDrawFace(world, state, cull, direction, pos)) {
                renderQuadsSmooth(world, state, pos, matrices, vertexConsumer, list, ...);
            }
        }
    }
}
```

**Solution**: Store context in ThreadLocal during `render()`, then access it in `getQuads()`!

## Implementation

### Step 1: Create CtmRenderContext (ThreadLocal Storage)

```java
public class CtmRenderContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    
    public record Context(BlockRenderView world, BlockPos pos, BlockState state) {}
    
    public static void set(BlockRenderView world, BlockPos pos, BlockState state) {
        CURRENT.set(new Context(world, pos, state));
    }
    
    @Nullable
    public static Context get() {
        return CURRENT.get();
    }
    
    public static void clear() {
        CURRENT.remove();
    }
}
```

### Step 2: Inject Context via Mixin

```java
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void storeCtmContext(
        BlockRenderView world,
        List<BlockModelPart> parts,
        BlockState state,
        BlockPos pos,
        MatrixStack matrices,
        VertexConsumer vertexConsumer,
        boolean cull,
        int overlay,
        CallbackInfo ci
    ) {
        // Store context before parts.getQuads() is called
        CtmRenderContext.set(world, pos, state);
    }
    
    @Inject(method = "render", at = @At("RETURN"))
    private void clearCtmContext(CallbackInfo ci) {
        CtmRenderContext.clear();
    }
    
    // Same for renderFlat() method
    @Inject(method = "renderFlat", at = @At("HEAD"))
    private void storeCtmContextFlat(...) {
        CtmRenderContext.set(world, pos, state);
    }
    
    @Inject(method = "renderFlat", at = @At("RETURN"))
    private void clearCtmContextFlat(CallbackInfo ci) {
        CtmRenderContext.clear();
    }
}
```

### Step 3: Create CtmBlockModelPart (Lazy Evaluation)

```java
public class CtmBlockModelPart implements BlockModelPart {
    private final BlockModelPart wrapped;
    private final CtmInfo ctmInfo;  // Contains CtmProperties, container, etc.
    
    public CtmBlockModelPart(BlockModelPart wrapped, CtmInfo ctmInfo) {
        this.wrapped = wrapped;
        this.ctmInfo = ctmInfo;
    }
    
    @Override
    public List<BakedQuad> getQuads(@Nullable Direction face) {
        // Get base quads from wrapped part
        List<BakedQuad> baseQuads = wrapped.getQuads(face);
        
        // Try to access render context
        CtmRenderContext.Context ctx = CtmRenderContext.get();
        if (ctx == null) {
            // No context available - return unmodified quads
            return baseQuads;
        }
        
        // Apply CTM processing with world context!
        return processCtmQuads(
            baseQuads,
            ctx.world(),
            ctx.pos(),
            ctx.state(),
            face
        );
    }
    
    private List<BakedQuad> processCtmQuads(
        List<BakedQuad> baseQuads,
        BlockRenderView world,
        BlockPos pos,
        BlockState state,
        @Nullable Direction face
    ) {
        // Check connections to neighbors
        boolean connectNorth = shouldConnect(world, pos, state, Direction.NORTH);
        boolean connectSouth = shouldConnect(world, pos, state, Direction.SOUTH);
        boolean connectEast = shouldConnect(world, pos, state, Direction.EAST);
        boolean connectWest = shouldConnect(world, pos, state, Direction.WEST);
        // ... (up/down if needed)
        
        // Select appropriate CTM texture based on connections
        Sprite ctmSprite = ctmInfo.getTextureForConnections(
            connectNorth, connectSouth, connectEast, connectWest, face
        );
        
        // Replace sprite in quads
        List<BakedQuad> result = new ArrayList<>();
        for (BakedQuad quad : baseQuads) {
            result.add(replaceSprite(quad, ctmSprite));
        }
        
        return result;
    }
    
    private boolean shouldConnect(
        BlockRenderView world,
        BlockPos pos,
        BlockState state,
        Direction direction
    ) {
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighborState = world.getBlockState(neighborPos);
        
        // CTM connection logic (same as before)
        return ctmInfo.shouldConnect(state, neighborState);
    }
    
    private BakedQuad replaceSprite(BakedQuad quad, Sprite newSprite) {
        // Use existing quad rebuilding logic from CtmBakedModel
        // (This part remains unchanged from 1.21.4)
        return QuadUtil.rebuildQuadWithSprite(quad, newSprite);
    }
}
```

### Step 4: Create CtmBlockStateModel

```java
public class CtmBlockStateModel extends WrappedBlockStateModel {
    private final CtmInfo ctmInfo;
    
    public CtmBlockStateModel(BlockStateModel wrapped, CtmInfo ctmInfo) {
        super(wrapped);
        this.ctmInfo = ctmInfo;
    }
    
    @Override
    public void addParts(Random random, List<BlockModelPart> parts) {
        // Get parts from wrapped model
        List<BlockModelPart> wrappedParts = new ArrayList<>();
        wrapped.addParts(random, wrappedParts);
        
        // Wrap each part with CTM lazy evaluation
        for (BlockModelPart part : wrappedParts) {
            parts.add(new CtmBlockModelPart(part, ctmInfo));
        }
    }
}
```

### Step 5: Update ModelWrappingHandler

```java
public class ModelWrappingHandler {
    public static BlockStateModel wrapBlockStateModel(
        BlockStateModel model,
        Identifier id,
        @Nullable BlockState state
    ) {
        // Check if CTM should be applied
        CtmProperties ctmProperties = CtmPropertiesLoader.get(id, state);
        if (ctmProperties != null) {
            CtmInfo ctmInfo = new CtmInfo(ctmProperties, /* container, etc. */);
            model = new CtmBlockStateModel(model, ctmInfo);
        }
        
        // Check if emissive should be applied
        if (EmissiveProperties.shouldApply(id, state)) {
            model = new EmissiveBlockStateModel(model);
        }
        
        return model;
    }
}
```

## Migration Checklist

### Phase 1: Infrastructure ✅
- [x] Create `CtmRenderContext` ThreadLocal holder
- [x] Create `BlockModelRendererMixin` to inject context
- [x] Document the solution architecture

### Phase 2: Core CTM Implementation
- [ ] Implement `CtmBlockModelPart` with lazy evaluation
- [ ] Port `shouldConnect()` logic from `CtmBakedModel`
- [ ] Port sprite replacement logic (reuse `QuadUtil`)
- [ ] Create `CtmInfo` record to hold CTM configuration

### Phase 3: Integration
- [ ] Update `CtmBlockStateModel` to use new part wrapper
- [ ] Update `ModelWrappingHandler` to wrap with CTM
- [ ] Remove old `CtmBakedModel` class
- [ ] Test with various CTM resource packs

### Phase 4: Testing
- [ ] Test basic CTM connections (glass, bookshelves)
- [ ] Test overlay CTM (ores)
- [ ] Test horizontal/vertical CTM
- [ ] Test random CTM
- [ ] Test edge cases (chunk boundaries, etc.)

## Advantages of This Approach

1. **Minimal Changes**: Reuses most existing CTM logic
2. **Thread-Safe**: ThreadLocal ensures no cross-thread issues
3. **Graceful Fallback**: Returns unmodified quads if context unavailable
4. **Performance**: Lazy evaluation only when needed
5. **Clean Architecture**: Separates baking (addParts) from rendering (getQuads)
6. **Fabric-Compatible**: Uses standard Minecraft rendering pipeline

## Alternative Approaches Considered

### ❌ Option 1: Store World Reference in Model
**Problem**: Models are cached and shared across chunks, can't store per-block data

### ❌ Option 2: Pre-generate All Variants
**Problem**: CTM has 2^4 = 16 states per face × 6 faces = 96 variants per block, too expensive

### ❌ Option 3: Custom Rendering Pipeline
**Problem**: Would require replacing Minecraft's entire block rendering system

### ✅ Option 4: ThreadLocal + Lazy Evaluation (CHOSEN)
**Benefits**: Clean, performant, compatible with existing code

## Performance Considerations

### Memory
- ThreadLocal overhead: Minimal (one Context object per render thread)
- Part wrapping: One wrapper object per BlockModelPart (typically 1-3 per model)

### CPU
- Context lookup: O(1) ThreadLocal get
- Connection checks: 4-6 neighbor checks per block (same as before)
- Quad modification: Only when CTM applies (unchanged from 1.21.4)

### Comparison to Old System
- **Memory**: Same (still need to check neighbors)
- **CPU**: Same (same logic, just called at different time)
- **Compatibility**: Better (uses standard rendering pipeline)

## Potential Issues & Solutions

### Issue 1: Multithreaded Chunk Building
**Problem**: Multiple threads rendering different chunks simultaneously
**Solution**: ThreadLocal automatically handles per-thread isolation ✓

### Issue 2: Context Not Available
**Problem**: getQuads() might be called outside of rendering
**Solution**: Graceful fallback - return unmodified quads ✓

### Issue 3: Mixin Compatibility
**Problem**: Other mods might mixin to BlockModelRenderer
**Solution**: Use @At("HEAD") and @At("RETURN") for compatibility ✓

### Issue 4: Chunk Boundary Connections
**Problem**: Neighbor chunk might not be loaded
**Solution**: Same handling as 1.21.4 - treat as disconnected ✓

## Next Steps

1. ✅ **Implement CtmRenderContext** - COMPLETED
   - ✅ Create ThreadLocal holder
   - ✅ Add get/set/clear methods
   - ✅ Add null-safety checks

2. ✅ **Implement BlockModelRendererMixin** - COMPLETED
   - ✅ Add @Inject at HEAD
   - ✅ Add @Inject at RETURN
   - ✅ Add same for renderFlat()
   - ✅ Registered in continuity.mixins.json

3. 🚨 **BLOCKER: Fabric Material System Import Failures** (NEW)
   - **Problem**: Cannot import `net.fabricmc.fabric.api.renderer.v1.material` package
   - **Investigation**: Classes exist in JAR but Loom remapping fails
   - **Impact**: 20+ files affected (emissive, overlay, utilities)
   - **Temporary Fix** (1-2 hours):
     - [ ] Stub out `RenderUtil.findOverlayMaterial()` and `getMaterialFinder()`
     - [ ] Replace `BlendMode` with vanilla `BlockRenderLayer` in properties
     - [ ] Comment out material usage in `EmissiveBakedModel` and `EmissiveBlockModelPart`
     - [ ] Comment out material parameters in `QuadUtil.emitOverlayQuad()`
     - [ ] Focus on core CTM logic that doesn't need materials

4. 🔄 **Implement CtmBlockModelPart** (2-3 hours) - BLOCKED
   - ✅ Create wrapper class skeleton
   - ⏳ Port shouldConnect() logic (doesn't need materials)
   - ⏳ Port sprite replacement logic (doesn't need materials)
   - ✅ Add fallback for no context
   - ⚠️ **Note**: Skip material-related features until API resolved

5. ⏳ **Implement CtmBlockStateModel** (30 min) - READY
   - Update addParts() to wrap parts
   - Pass through particleSprite()

6. ⏳ **Test & Debug** (2-4 hours)
   - Test with vanilla CTM packs
   - Test chunk boundaries
   - Test multithreading
   - Profile performance

**Total Estimated Time**: 8-12 hours (increased due to material API blocker)

## Success Criteria

- [ ] Glass connects properly with neighbors
- [ ] Connections update when blocks placed/broken
- [ ] No crashes in multithreaded chunk building
- [ ] Performance similar to 1.21.4
- [ ] Works with existing CTM resource packs (no format changes needed)
- [ ] Gracefully handles missing context (no crashes)

## References

- BlockModelRenderer.java (Minecraft 1.21.10)
- BlockStateModel.java (Minecraft 1.21.10) 
- RenderCommandQueue.java (Minecraft 1.21.10)
- Fabric Blog: https://fabricmc.net/2025/09/23/1219.html
- Migration Primer: https://gist.github.com/ChampionAsh5357/d895a7b1a34341e19c80870720f9880f
