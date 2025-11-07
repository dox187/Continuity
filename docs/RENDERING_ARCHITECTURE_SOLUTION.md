# Rendering Architecture Solution: OrderedRenderCommandQueue

## Critical Information Sources

1. **Fabric 1.21.9/1.21.10 Blog Post**: https://fabricmc.net/2025/09/23/1219.html
2. **Minecraft 1.20.5/6 → 1.21 Migration Primer**: https://gist.github.com/ChampionAsh5357/d895a7b1a34341e19c80870720f9880f

## The Problem

**OLD CTM System (1.21.4)**:
```java
public class CtmBakedModel extends WrapperBakedModel {
    @Override
    public void emitBlockQuads(BlockRenderView world, BlockState state, BlockPos pos, 
                              Supplier<Random> randomSupplier, RenderContext context) {
        // Had access to world and pos for neighbor checks ✓
        BlockState neighbor = world.getBlockState(pos.north());
        // Could determine CTM connections immediately
    }
}
```

**NEW BlockStateModel System (1.21.10)**:
```java
public interface BlockStateModel {
    // No world context! Only random and output list
    void addParts(Random random, List<BlockModelPart> parts);
}
```

**Problem**: CTM needs world context (neighbors) but `addParts()` has no access to `BlockRenderView` or `BlockPos`!

## The Solution: OrderedRenderCommandQueue

### Discovery from Fabric Blog

**Fabric 1.21.9+ introduces `OrderedRenderCommandQueue` for deferred rendering**:

> "Almost all world rendering has been reworked to group objects with similar rendering requirements together. Most places now use `OrderedRenderCommandQueue` to submit things to be drawn later."

### Block Entity Example (from Fabric blog)

```java
// NEW ARCHITECTURE
public class TestBlockEntityRenderer implements BlockEntityRenderer<TestBlockEntity, BlockEntityRenderState> {
    @Override
    public void render(BlockEntityRenderState state, MatrixStack matrices, 
                      OrderedRenderCommandQueue queue,  // ← THE QUEUE!
                      CameraRenderState cameraRenderState) {
        // Render using the queue
        queue.submitText(
            matrices,
            0, 0,
            Text.literal("Hello, world!").asOrderedText(),
            false,
            TextRenderer.TextLayerType.NORMAL,
            state.lightmapCoordinates,
            Colors.WHITE,
            0,
            Colors.BLACK
        );
    }
}
```

## Two-Stage Rendering Pipeline

### Stage 1: Bake Time (No Context)
```java
BlockStateModel.addParts(Random random, List<BlockModelPart> parts) {
    // Called during model baking
    // NO world context available
    // NO BlockPos available
    // Creates static parts
}
```

### Stage 2: Render Time (With Context)
```java
BlockModelRenderer.render(
    BlockRenderView world,        // ← World context available!
    List<BlockModelPart> parts,   // ← Parts from stage 1
    BlockState state,             // ← State info
    BlockPos pos,                 // ← Position info
    MatrixStack matrices,
    OrderedRenderCommandQueue queue,  // ← The rendering queue
    boolean cull,
    int overlay
) {
    // This is where actual rendering happens
    // World context is available here!
}
```

## Solution Architecture for CTM

### Option 1: Dynamic BlockModelPart (Lazy Evaluation)

Create a `CtmBlockModelPart` that defers quad generation until `getQuads()` is called:

```java
public class CtmBlockModelPart implements BlockModelPart {
    private final BlockModelPart wrapped;
    private final ThreadLocal<RenderContext> contextHolder;
    
    @Override
    public List<BakedQuad> getQuads(@Nullable Direction face) {
        // getQuads() is called during BlockModelRenderer.render()
        // At this point, world context might be available via ThreadLocal
        
        RenderContext ctx = contextHolder.get();
        if (ctx != null && ctx.hasWorldContext()) {
            // Access world and position for neighbor checks
            BlockState neighbor = ctx.world().getBlockState(ctx.pos().north());
            // Generate CTM-connected quads dynamically
            return generateCtmQuads(wrapped.getQuads(face), neighbor);
        }
        
        // Fallback: return unmodified quads
        return wrapped.getQuads(face);
    }
}

public class CtmBlockStateModel extends WrappedBlockStateModel {
    @Override
    public void addParts(Random random, List<BlockModelPart> parts) {
        // Get parts from wrapped model
        List<BlockModelPart> wrappedParts = new ArrayList<>();
        wrapped.addParts(random, wrappedParts);
        
        // Wrap each part with CTM lazy evaluation
        for (BlockModelPart part : wrappedParts) {
            parts.add(new CtmBlockModelPart(part, renderContextHolder));
        }
    }
}
```

### Option 2: Custom Renderer Hook

Hook into `BlockModelRenderer.render()` via mixin to inject CTM logic:

```java
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void injectCtmContext(
        BlockRenderView world,
        List<BlockModelPart> parts,
        BlockState state,
        BlockPos pos,
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        boolean cull,
        int overlay,
        CallbackInfo ci
    ) {
        // Store context in ThreadLocal for CTM parts to access
        CtmRenderContext.set(world, pos);
        
        // Modify parts list to wrap with CTM logic
        for (int i = 0; i < parts.size(); i++) {
            BlockModelPart part = parts.get(i);
            if (shouldApplyCTM(state, part)) {
                parts.set(i, new CtmBlockModelPart(part, world, pos));
            }
        }
    }
    
    @Inject(method = "render", at = @At("RETURN"))
    private void clearCtmContext(CallbackInfo ci) {
        CtmRenderContext.clear();
    }
}
```

### Option 3: OrderedRenderCommandQueue Integration

Directly use the queue system like block entities do:

```java
public class CtmBlockStateModel extends WrappedBlockStateModel {
    @Override
    public void addParts(Random random, List<BlockModelPart> parts) {
        // Add a special "CTM command part" that uses the queue
        parts.add(new CtmQueueCommandPart(wrapped));
    }
}

public class CtmQueueCommandPart implements BlockModelPart {
    @Override
    public List<BakedQuad> getQuads(@Nullable Direction face) {
        // Return empty - actual rendering happens via queue
        return List.of();
    }
    
    // Custom method called by BlockModelRenderer
    public void submitToQueue(OrderedRenderCommandQueue queue, 
                             BlockRenderView world, BlockPos pos) {
        // Access world context here!
        // Generate CTM quads and submit to queue
        BlockState neighbor = world.getBlockState(pos.north());
        List<BakedQuad> ctmQuads = generateCtmQuads(neighbor);
        
        for (BakedQuad quad : ctmQuads) {
            queue.submitQuad(quad, ...);
        }
    }
}
```

## Research Next Steps

1. **Find `OrderedRenderCommandQueue` implementation**:
   - Search for the class in Minecraft sources
   - Understand its API and how to submit quads
   - Check if blocks can use it (blog shows block entities and particles can)

2. **Investigate BlockModelRenderer.render() implementation**:
   - How does it iterate over parts?
   - Does it call `getQuads()` immediately or defer?
   - Can we hook into the rendering loop?

3. **Check Fabric Rendering API changes**:
   - Does Fabric still provide `RenderContext`?
   - Is there a new API for block rendering with world context?
   - Look for Fabric-provided interfaces or mixins

4. **Test ThreadLocal approach**:
   - Can we safely store world context in ThreadLocal?
   - Will it work with multithreaded chunk building?
   - Is there a better way to pass context?

## Migration Plan Update

### Immediate Actions

1. ✅ Document the OrderedRenderCommandQueue discovery
2. 🔄 Search for OrderedRenderCommandQueue in Minecraft sources
3. 🔄 Extract full BlockModelRenderer.render() implementation
4. 🔄 Research Fabric Rendering API v1 changes for 1.21.10
5. 📝 Implement prototype CTM solution using chosen approach

### Recommended Approach

**Start with Option 1 (Dynamic BlockModelPart)** because:
- Least invasive (no mixins into vanilla renderer)
- Follows the lazy evaluation pattern
- Can fall back gracefully if context unavailable
- Aligns with existing Fabric Rendering API patterns

**Fallback to Option 2 (Mixin Hook)** if:
- ThreadLocal doesn't work with chunk building
- getQuads() is called too early (before render stage)
- Need more control over rendering pipeline

**Consider Option 3 (Queue Integration)** if:
- OrderedRenderCommandQueue has a public API for blocks
- Fabric provides hooks for custom queue commands
- Other options prove incompatible

## Key Insights

1. **BakedModel is completely removed** - confirmed by multiple sources
2. **Fabric World Render Events are gone** - temporary removal, replacement planned
3. **OrderedRenderCommandQueue is the new rendering primitive** - blocks, entities, particles all use it
4. **Two-stage pipeline is intentional** - baking vs rendering separation
5. **World context IS available at render time** - just need to access it correctly

## References

- Fabric Blog: https://fabricmc.net/2025/09/23/1219.html
  - "World Render Events" section: explains removal and queue system
  - "Block Entities" section: shows queue usage example
  - "Particles" and "Entities" sections: confirm queue usage

- Migration Primer: https://gist.github.com/ChampionAsh5357/d895a7b1a34341e19c80870720f9880f
  - Comprehensive 1.20.5/6 → 1.21 changes
  - Rendering system overhaul details
  - Vertex system changes
