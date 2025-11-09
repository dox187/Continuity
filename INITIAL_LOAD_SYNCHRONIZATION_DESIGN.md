# Initial Load Synchronization - Complete Solution Design

**Phase**: Architecture Design (Pre-Implementation)  
**Date**: November 9, 2025  
**Status**: ✅ Ready for Implementation Planning  

---

## Executive Summary

**Root Cause**: On initial Minecraft load, `SpriteAtlasTexture.upload()` fires BEFORE `CtmResourceReloadListener.reload()` executes because NO resource reload event is triggered during initial startup—reload events only fire when explicitly triggered (F3+T) or when resource packs change post-initialization.

**Solution**: Implement eager CTM properties loading in `ContinuityClient.onInitializeClient()` that completes BEFORE the first model baking occurs, with proper state machine coordination.

**Implementation Complexity**: Medium (3 files modified, ~40 lines added)  
**Risk Level**: Low (graceful degradation already in place)  
**Testing Requirements**: Initial world load + F3+T reload validation

---

## Root Cause Analysis

### Timeline: Initial Load (Current - Broken)

```
TIME T0: ContinuityClient.onInitializeClient()
    ✅ Register CtmResourceReloadListener
    ✅ Coordinator.reset() → state = IDLE
    ✅ (But no reload event fires yet!)

TIME T1: Sprite stitching completes
    (SpriteLoader finishes creating sprites)

TIME T2: SpriteAtlasTexture.upload() fires 🔥
    ✅ Call AtlasStorage.setBlockAtlas()
    ❌ Call coordinator.getExtensionWhenReady()
       └─ state = IDLE
       └─ Returns null ← PROBLEM!
    ❌ Skip CTM processing
    ✅ Continue with normal texture upload
    ❌ CTM NOT APPLIED ✗

TIME T3+: (Much later, if ever)
    Resource reload event fires
    └─ CtmResourceReloadListener.reload() executes
       └─ Coordinator.startReload()
       └─ Properties loaded
       └─ Quad processors created
       └─ BUT: Too late! Texture already uploaded

RESULT: CTM invisible until F3+T reload
```

### Timeline: F3+T Reload (Current - Working)

```
TIME T0: User presses F3+T
    ✅ Minecraft fires resource reload event

TIME T1: CtmResourceReloadListener.reload() fires SYNCHRONOUSLY
    ✅ Coordinator.startReload(resourceManager)
    ✅ Coordinator.state = LOADING_PROPERTIES
    ✅ Properties loading started asynchronously
    ✅ CompletableFuture.supplyAsync(() -> load properties)

TIME T2: SpriteAtlasTexture.upload() fires 🔥 (after reload starts)
    ✅ Call AtlasStorage.setBlockAtlas()
    ✅ Call coordinator.getExtensionWhenReady()
       └─ state = LOADING_PROPERTIES
       └─ BLOCKS until state = PROPERTIES_LOADED
       └─ Properties complete (loaded in background)
    ✅ Call extension.beforeBake()
    ✅ Call extension.apply()
    ✅ Call coordinator.markComplete()
    ✅ CTM APPLIED ✓

RESULT: Works perfectly, all features visible
```

### Why F3+T Works But Initial Load Doesn't

**Key Discovery**: `CtmResourceReloadListener` uses Fabric's `ResourceReloadListener` interface which only fires on:
1. ✅ Explicit user reload (F3+T)
2. ✅ Resource pack changes after initialization
3. ❌ Initial game startup (NO EVENT)

**Verification**:
```java
// Fabric ResourceReloadListener
// Fired by: ResourceReloadListenerRegistryImpl.reload()
// Called when: reload event occurs
// NOT called: During initial game load

// Therefore:
// - Initial load: No reload event → no reload listener → no properties loading
// - F3+T: Reload event → reload listener fires → properties load → works!
```

---

## Solution Approaches

### Option A: Eager Initial Load (RECOMMENDED ✅)

**Concept**: Load CTM properties eagerly in `onInitializeClient()` so they're ready when `upload()` fires

**Implementation**:
```java
// In ContinuityClient.onInitializeClient()

// 1. Load properties immediately (synchronously or with blocking await)
ResourceManager resourceManager = ClientLifecycleEvents.getResourceManager();
CtmPropertiesLoader loader = new CtmPropertiesLoader();
Map<String, CtmProperties> properties = loader.load(resourceManager);

// 2. Create extension with loaded properties
BakedModelManagerReloadExtension extension = 
    new BakedModelManagerReloadExtension(properties);

// 3. Signal coordinator that we're ready
coordinator.setExtensionEarly(extension);

// 4. Set state to PROPERTIES_LOADED so getExtensionWhenReady() returns immediately
coordinator.setState(PROPERTIES_LOADED);

// 5. Later when upload() fires, extension is already available
```

**Pros**:
- ✅ Simple, predictable execution
- ✅ No race conditions (synchronous setup in onInitializeClient)
- ✅ No timeout/retry complexity
- ✅ Works correctly with subsequent F3+T reloads
- ✅ Clear cause-effect relationship

**Cons**:
- ⚠️ Slight delay in mod initialization (CTM load happens eagerly)
- ⚠️ Resource I/O happens on mod init thread (acceptable for small file loads)

**Risk**: Low (graceful degradation if properties don't load)

---

### Option B: Eager State Machine on First Upload (GOOD)

**Concept**: On first `upload()` call, if coordinator state is still IDLE, load properties synchronously right there

**Implementation**:
```java
// In SpriteAtlasTextureMixin.continuity$onUpload()

if (shouldWrapCtm) {
    CtmInitializationCoordinator coordinator = CtmInitializationCoordinator.getInstance();
    BakedModelManagerReloadExtension extension = coordinator.getExtensionWhenReady();
    
    if (extension == null && coordinator.isInitialLoad()) {
        // FIRST upload on initial load
        // Load properties synchronously RIGHT HERE
        ResourceManager resourceManager = ClientLifecycleEvents.getResourceManager();
        CtmPropertiesLoader loader = new CtmPropertiesLoader();
        
        try {
            Map<String, CtmProperties> properties = loader.load(resourceManager);
            extension = new BakedModelManagerReloadExtension(properties);
            coordinator.setExtensionEarly(extension);
        } catch (Exception e) {
            LOGGER.warn("Failed to load CTM properties eagerly", e);
            extension = null;
        }
    }
}
```

**Pros**:
- ✅ Only loads if actually needed (lazy)
- ✅ Doesn't delay mod init
- ✅ Transparent to initialization code

**Cons**:
- ⚠️ Synchronous I/O on render thread (undesirable)
- ⚠️ Mixin becomes responsible for resource loading (wrong layer)
- ⚠️ More complex error handling
- ⚠️ Harder to debug (initialization hidden in mixin)

**Risk**: Medium (I/O on render thread could stutter)

---

### Option C: Pre-Load via Initialization Event (COMPLEX)

**Concept**: Use Fabric's lifecycle events to pre-warm CTM in a dedicated phase

**Implementation**:
```java
// Register during mod load phase

ClientTickEvents.END_CLIENT_TICK.register(client -> {
    // On first tick, load properties
    if (!propertiesLoadedYet) {
        try {
            ResourceManager resourceManager = client.getResourceManager();
            // ... load properties ...
            propertiesLoadedYet = true;
        } catch (Exception e) {
            // ...
        }
    }
});
```

**Pros**:
- ✅ Happens on dedicated tick (not on initialization thread)
- ✅ Easier to defer if resources not ready

**Cons**:
- ⚠️ Race condition if upload() fires before first tick
- ⚠️ Requires storing state (propertiesLoadedYet flag)
- ⚠️ Doesn't guarantee ordering
- ⚠️ Less predictable

**Risk**: High (timing-dependent, hard to test)

---

## Recommended Solution: Option A (Eager Initial Load)

### Why Option A?

| Factor | Option A | Option B | Option C |
|--------|----------|----------|----------|
| Simplicity | ✅ Simple | ⚠️ Complex | ❌ Complex |
| Correctness | ✅ Guaranteed | ⚠️ Possible race | ❌ Possible race |
| Performance | ⚠️ Slight delay | ✅ No delay | ⚠️ Unpredictable |
| Thread safety | ✅ Clear | ⚠️ Render thread I/O | ⚠️ Tick timing |
| Debuggability | ✅ Linear flow | ⚠️ Hidden in mixin | ⚠️ Delayed tick |
| F3+T compatibility | ✅ Works | ✅ Works | ⚠️ State conflicts |

**Decision**: ✅ **IMPLEMENT OPTION A**

---

## Detailed Solution Design: Option A

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│ ContinuityClient.onInitializeClient() - MAIN THREAD        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ PHASE 1: Initialize Coordinator                            │
│   coordinator.reset()                                      │
│   coordinator.state = IDLE                                │
│                                                              │
│ PHASE 2: Load CTM Properties Eagerly                      │
│   resourceManager = getResourceManager()                  │
│   properties = CtmPropertiesLoader.load(resourceManager)  │
│   (Synchronous - loads optifine/ctm/*.properties files)   │
│                                                              │
│ PHASE 3: Create Extension with Properties                │
│   extension = new BakedModelManagerReloadExtension(props) │
│   coordinator.setExtensionEarly(extension)               │
│   coordinator.setState(PROPERTIES_LOADED)                │
│                                                              │
│ PHASE 4: Register Reload Listener                         │
│   ResourceReloadListenerRegistry.register(listener)       │
│   (For F3+T - will override properties if needed)         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                            ↓
        ┌───────────────────┴───────────────────┐
        │   Time passes, Minecraft starts       │
        │   resource pack loads, textures prep  │
        └───────────────────┬───────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ SpriteAtlasTexture.upload() - CALLED ONCE FOR BLOCK ATLAS │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ coordinator.getExtensionWhenReady()                        │
│   ↓ coordinator.state == PROPERTIES_LOADED                │
│   ↓ EXTENSION AVAILABLE IMMEDIATELY (no blocking!)        │
│   ↓ return extension (NOT NULL)                           │
│                                                              │
│ extension.beforeBake(sprites, missingSprite)             │
│   ↓ Create quad processors from loaded properties        │
│                                                              │
│ extension.apply()                                          │
│   ↓ Register processors in ModelWrappingHandler           │
│                                                              │
│ coordinator.markComplete()                                │
│   ↓ state = COMPLETE                                     │
│                                                              │
│ ✅ CTM APPLIED ON INITIAL WORLD LOAD                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### State Machine: Enhanced for Initial Load

**Old State Transitions** (F3+T only):
```
IDLE → LOADING_PROPERTIES → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE → IDLE
```

**New State Transitions** (Initial load + F3+T):
```
INITIAL LOAD:
  IDLE → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE
         ↑(eager load)       ↑(from upload)
         
F3+T RELOAD (starts fresh):
  IDLE → LOADING_PROPERTIES → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE → IDLE
         ↑(reload event)     ↑(async done)       ↑(from upload)
```

### Coordinator State Machine Code

```java
public class CtmInitializationCoordinator {
    
    // Phases in lifecycle
    enum State {
        IDLE,                  // Initial or between reloads
        LOADING_PROPERTIES,    // Async loading in progress
        PROPERTIES_LOADED,     // Ready for use
        ATLAS_PROCESSING,      // upload() has been called
        COMPLETE               // Everything done
    }
    
    private volatile State state = State.IDLE;
    private volatile BakedModelManagerReloadExtension extension;
    private final CompletableFuture<Void> propertiesReadyFuture = new CompletableFuture<>();
    
    // ===== INITIAL LOAD PATH (NEW) =====
    
    /**
     * Set extension directly during eager initialization (INITIAL LOAD).
     * Called from ContinuityClient.onInitializeClient()
     */
    public void setExtensionEarly(BakedModelManagerReloadExtension ext) {
        this.extension = ext;
        // State remains IDLE or gets set to PROPERTIES_LOADED by caller
    }
    
    /**
     * Set state to PROPERTIES_LOADED after eager load.
     * Allows getExtensionWhenReady() to return immediately.
     */
    public void setStateEarly(State newState) {
        if (newState == State.PROPERTIES_LOADED) {
            this.state = newState;
            propertiesReadyFuture.complete(null);
        }
    }
    
    // ===== RELOAD PATH (EXISTING) =====
    
    /**
     * Called by CtmResourceReloadListener.reload() on F3+T
     */
    public void startReload(ResourceManager resourceManager) {
        this.state = State.LOADING_PROPERTIES;
        
        CompletableFuture.supplyAsync(() -> {
            return CtmPropertiesLoader.load(resourceManager);
        }).thenAccept(properties -> {
            this.extension = new BakedModelManagerReloadExtension(properties);
            this.state = State.PROPERTIES_LOADED;
            propertiesReadyFuture.complete(null);
        });
    }
    
    /**
     * Get extension, blocking until ready if necessary.
     * Used by SpriteAtlasTextureMixin.onUpload()
     */
    public BakedModelManagerReloadExtension getExtensionWhenReady() {
        if (state == State.IDLE) {
            // NOT ready yet - initial load hasn't loaded properties
            return null;
        }
        
        if (state == State.LOADING_PROPERTIES) {
            // Async loading in progress - BLOCK until ready
            try {
                propertiesReadyFuture.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOGGER.warn("CTM properties loading timed out");
                return null;
            } catch (Exception e) {
                LOGGER.error("Error waiting for CTM properties", e);
                return null;
            }
        }
        
        // State is PROPERTIES_LOADED or later - return extension
        return extension;
    }
    
    /**
     * Called when upload() processing is complete
     */
    public void markComplete() {
        this.state = State.COMPLETE;
    }
    
    /**
     * Reset between reloads
     */
    public void reset() {
        this.state = State.IDLE;
        this.extension = null;
        this.propertiesReadyFuture.complete(null);
    }
}
```

### Modified ContinuityClient Initialization

**Current Code** (incomplete):
```java
public class ContinuityClient {
    public static void onInitializeClient() {
        // ... existing initialization ...
        
        // Register reload listener for F3+T
        ResourceReloadListenerRegistry.register(
            new CtmResourceReloadListener()
        );
    }
}
```

**New Code** (with eager load):
```java
public class ContinuityClient {
    public static void onInitializeClient() {
        // ... existing initialization ...
        
        // ========== NEW: EAGER INITIAL LOAD ==========
        
        // 1. Initialize coordinator
        CtmInitializationCoordinator coordinator = 
            CtmInitializationCoordinator.getInstance();
        
        // 2. Try to load CTM properties eagerly for initial world load
        try {
            ResourceManager resourceManager = 
                MinecraftClient.getInstance().getResourceManager();
            
            LOGGER.info("[Continuity] Loading CTM properties eagerly for initial world load");
            
            Map<String, CtmProperties> properties = 
                CtmPropertiesLoader.loadFromResourceManager(resourceManager);
            
            // 3. Create extension with properties
            BakedModelManagerReloadExtension extension = 
                new BakedModelManagerReloadExtension(properties);
            
            // 4. Signal coordinator that extension is ready
            coordinator.setExtensionEarly(extension);
            coordinator.setStateEarly(CtmInitializationCoordinator.State.PROPERTIES_LOADED);
            
            LOGGER.info("[Continuity] CTM properties loaded eagerly - initial world load will have CTM");
            
        } catch (Exception e) {
            LOGGER.warn("[Continuity] Failed to load CTM properties eagerly - will use F3+T reload", e);
            // Gracefully degrade - F3+T reload will still work
            coordinator.reset();
        }
        
        // ========== EXISTING: REGISTER RELOAD LISTENER FOR F3+T ==========
        
        // Register reload listener - will override eager properties if user presses F3+T
        ResourceReloadListenerRegistry.register(
            new CtmResourceReloadListener()
        );
    }
}
```

---

## State Machine Diagram with Timing

```
INITIAL WORLD LOAD:

T0: onInitializeClient()
    │ Eager load properties
    └─→ state = PROPERTIES_LOADED
        extension = ReloadExtension(props)

T1: [Resource pack loads, sprite stitching begins]

T2: SpriteAtlasTexture.upload() fires
    │ getExtensionWhenReady()
    ├─ state == PROPERTIES_LOADED
    ├─ return extension (NOT NULL) ✅
    │
    └─→ beforeBake(sprites, missing)
    └─→ apply() → register processors
    └─→ markComplete()
    └─→ state = COMPLETE

T3: Quad processing active ✅
    CTM textures visible ✅


F3+T RELOAD (from COMPLETE state):

T0: User presses F3+T
    │ Minecraft fires reload event
    └─→ CtmResourceReloadListener.reload()
        state = LOADING_PROPERTIES
        async: load properties

T1: SpriteAtlasTexture.upload() fires
    │ getExtensionWhenReady()
    ├─ state == LOADING_PROPERTIES
    ├─ BLOCK on propertiesReadyFuture
    ├─ (properties complete, future resolved)
    ├─ return extension (NOT NULL) ✅
    │
    └─→ beforeBake(sprites, missing) - with NEW props
    └─→ apply() → register NEW processors
    └─→ markComplete()
    └─→ state = COMPLETE

T2: Quad processing with updated properties ✅
    CTM textures updated ✅
```

---

## Implementation Files Overview

### File 1: ContinuityClient.java (MODIFY)

**Location**: `src/main/java/me/pepperbell/continuity/client/ContinuityClient.java`

**Changes**:
- Add eager properties loading in `onInitializeClient()`
- Call coordinator methods to signal early readiness
- Wrap in try-catch with graceful degradation

**Lines Added**: ~25  
**Lines Modified**: 0 (insert new block)

---

### File 2: CtmInitializationCoordinator.java (MODIFY)

**Location**: `src/main/java/me/pepperbell/continuity/client/resource/CtmInitializationCoordinator.java`

**Changes**:
- Add `setExtensionEarly()` method
- Add `setStateEarly()` method  
- Modify `getExtensionWhenReady()` to check for IDLE state (return null only if truly IDLE)
- Add constructor-level State enum (if not already present)

**Lines Added**: ~15  
**Lines Modified**: ~3 (getExtensionWhenReady logic)

---

### File 3: CtmResourceReloadListener.java (NO CHANGES)

**Status**: No changes needed
- Already handles F3+T reloads correctly
- Eager load sets initial state, reload listener can override it

---

## Testing Strategy

### Test 1: Initial World Load (Primary)
```
1. Fresh Minecraft launch with mod
2. Create or load world
3. Verify CTM textures visible immediately ✅
   - Connected grass blocks
   - Emissive textures visible
   - No artifacts or errors
4. Check logs for:
   - "[Continuity] Loading CTM properties eagerly..."
   - "[Continuity] CTM properties loaded eagerly..."
   - "[Continuity] CTM quad processors registered successfully"
```

### Test 2: F3+T Reload (Secondary)
```
1. After initial load, press F3+T
2. Verify properties reload ✅
   - Resource pack modifications picked up
   - Quad processors recreated
3. Check logs for:
   - "[Continuity] Resource reload triggered"
   - "[Continuity] CTM properties loaded asynchronously..."
   - "[Continuity] CTM quad processors registered successfully"
```

### Test 3: Grace Degradation
```
1. Corrupt .properties files (intentionally)
2. Launch game
3. Verify ✅:
   - Game launches without crash
   - No CTM visible (graceful degradation)
   - Logs show warning about failed load
   - F3+T reload recovers if properties fixed
```

### Test 4: Performance
```
1. Monitor startup time
2. Expected impact: +50-100ms (file I/O for .properties scanning)
3. No stuttering during initial world load
4. Rendering FPS normal after world loads
```

---

## Risk Mitigation

### Risk 1: What if ResourceManager isn't ready in onInitializeClient()?
**Mitigation**: 
- Check `MinecraftClient.getInstance().getResourceManager()` for null
- Wrap in try-catch and gracefully degrade
- Properties still load on F3+T

### Risk 2: What if properties files are huge?
**Mitigation**:
- Properties files are typically small (<1MB total)
- File scanning is linear (fast)
- User gets slight delay (~50-100ms) acceptable for one-time init

### Risk 3: What if concurrent F3+T during initial load?
**Mitigation**:
- State machine handles this: reload listener calls startReload()
- startReload() sets state = LOADING_PROPERTIES
- getExtensionWhenReady() blocks if LOADING_PROPERTIES
- Result: Correct (latest) properties loaded

### Risk 4: What if initial load fails, then F3+T is pressed?
**Mitigation**:
- reset() sets state back to IDLE
- Reload listener runs fresh startReload()
- Properties load correctly on second attempt

---

## Backward Compatibility

### With 1.21.6
- Code only added, not removed
- Thread-local context pattern unchanged
- Reload listener pattern unchanged
- ✅ Fully compatible

### With Future Versions
- Using stable APIs (ResourceManager, file I/O)
- No version-specific code
- ✅ Forward compatible

---

## Conclusion

**Solution Chosen**: Option A - Eager Initial Load

**Why**: 
- Simple, predictable execution
- No race conditions
- Minimal code changes
- Clear cause-effect relationship
- Works correctly with all reload scenarios

**Implementation Cost**: ~40 lines across 2 files  
**Testing Time**: ~30 minutes  
**Risk Level**: Low (graceful degradation in place)  
**Expected Result**: ✅ CTM visible on initial world load + F3+T reload works perfectly

---

## Next Steps

1. ✅ **API Review Complete** - AtlasStorage and AtlasLoaderMixin are correct
2. ⏳ **This Document** - Initial load synchronization strategy (you are here)
3. ⏳ **Implementation Specification** - Exact file changes with code samples
4. ⏳ **Code Implementation** - Apply changes to ContinuityClient and Coordinator
5. ⏳ **Build and Test** - Verify compilation and runtime behavior
6. ⏳ **Documentation** - Update changelogs and guides

---

## Related Documentation

- **FABRIC_API_CAPABILITY_ANALYSIS.md** - API capabilities review (JUST COMPLETED)
- **PHASE5_DELETED_FILES_SOLUTIONS-PART2.md** - Coordinator pattern overview
- **CtmInitializationCoordinator.java** - Current coordinator implementation
- **ContinuityClient.java** - Modification target
