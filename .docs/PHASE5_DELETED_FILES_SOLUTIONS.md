# PHASE 5 - CRITICAL DISCOVERY: Why CTM Isn't Working

**Date**: November 9, 2025  
**Phase**: Phase 5A - Root Cause Analysis  
**Status**: 🔴 **CRITICAL ISSUE IDENTIFIED**  
**Minecraft Version**: 1.21.10  

---

## Executive Summary

**THE BIG REALIZATION**: All 7 deleted files functioned as a **coordinated initialization pipeline** that is completely missing in the new 1.21.10 architecture.

When `BakedModelManager` was removed in Minecraft 1.21.10, we deleted the files but **NEVER REPLACED THE INITIALIZATION LOGIC**. 

Result: **The CTM properties are NEVER loaded, quad processors are NEVER created, and textures are NEVER replaced.**

---

## The Deleted Pipeline (1.21.6 - What Worked)

### Step 1: Resource Reload Trigger
```
BakedModelManagerReloadHandler.reload(ResourceManager manager)
  └─ Implements: SimpleSynchronousResourceReloadListener
  └─ Dependency: ResourceReloadListenerKeys.MODELS
  └─ Purpose: Listen for resource pack changes
```

### Step 2: Extension Creation
```
new BakedModelManagerReloadExtension(manager, executor)
  └─ Class: BakedModelManagerReloadExtension
  └─ Purpose: Centralized orchestrator for CTM initialization
  └─ Stores: Reference to BakedModelManager
```

### Step 3: CTM Properties Loading (ASYNC)
```
CompletableFuture<CtmPropertiesLoader.LoadingResult> ctmLoadingResultFuture
  = CompletableFuture.supplyAsync(
      () -> CtmPropertiesLoader.loadAllWithState(resourceManager),
      prepareExecutor)
  
  └─ Scans: optifine/ctm/ in all resource packs
  └─ Parses: .properties files
  └─ Creates: CtmProperties objects for each file
  └─ Returns: LoadingResult with all CTM configurations
```

### Step 4: Context Setup (SYNC)
```
spriteLoaderLoadContext = new SpriteLoaderLoadContextImpl(
    ctmLoadingResultFuture.thenApply(...),
    wrapEmissiveModels)

SpriteLoaderLoadContext.THREAD_LOCAL.set(spriteLoaderLoadContext)
  
  └─ Purpose: Make context available to sprite loader mixin
  └─ Contains: Texture dependencies, emissive control
  └─ Timing: Must happen BEFORE sprite stitching
```

### Step 5: Model Wrapping Setup
```
beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations)
  
  └─ Waits: For CTM properties loading to complete (join())
  └─ Creates: List<QuadProcessors.ProcessorHolder> from properties
  └─ Calls: ModelWrappingHandler.setInstance(wrapCtm, wrapEmissive)
  └─ Result: Model wrapping enabled for this atlas
```

### Step 6: Quad Processor Registration
```
QuadProcessors.reload(processorHolders)
  
  └─ Registers: All quad processors for this atlas
  └─ Purpose: Processors now available during model baking
  └─ Timing: Must happen AFTER properties loaded, BEFORE models baked
```

### Step 7: During Sprite Loading
```
SpriteLoaderMixin.continuity$modifySupplier()
  └─ Context available from thread-local ✓
  └─ Extra texture IDs injected ✓
  
SpriteLoaderMixin.continuity$modifyFunction()
  └─ Emissive mapping accessed from context ✓
  
SpriteLoaderMixin.continuity$onReturnStitch()
  └─ Emissive sprites attached to base sprites ✓
```

### Step 8: During Model Baking
```
Models wrapped with CtmBlockStateModel
  └─ Quad processor applied
  └─ Connected textures calculated
  └─ Textures replaced
  
Result: Connected textures appear in-game ✓
```

---

## The Current Architecture (1.21.10 - What's Broken)

### What EXISTS:
✅ ContinuityClient.onInitializeClient() - Registers 20+ CTM loaders  
✅ ModelWrappingHandler - Wrapping system ready  
✅ SpriteLoaderMixin - Injection points in place  
✅ RenderUtil.ReloadListener - Sprite finder update  
✅ SpriteAtlasTextureMixin - Atlas upload hook  
✅ CtmPropertiesLoader - Can load .properties files  
✅ BakedModelManagerReloadExtension - Class exists but NEVER USED  

### What's MISSING:
❌ **No resource reload listener for CTM properties**  
❌ **No instantiation of BakedModelManagerReloadExtension**  
❌ **No call to CtmPropertiesLoader.loadAllWithState()**  
❌ **No setup of SpriteLoaderLoadContext thread-local**  
❌ **No registration with ModelWrappingHandler**  
❌ **No quad processor creation**  

### The Result:
```
CTM Loaders Registered ✓
  └─ But never get properties to process
  
Context Thread-Local Ready ✓
  └─ But never gets populated
  
Quad Processors Framework Ready ✓
  └─ But never receives processor instances
  
Model Wrapping Ready ✓
  └─ But never called with actual CTM settings
  
SpriteLoaderMixin Injection Points Ready ✓
  └─ But context always NULL
  
Result: All textures unchanged, no CTM visible ❌
```

---

## Test Results - What We Observed

### Logs That APPEAR (Working):
```
[Continuity] Initialization started
[Continuity] Initializing ModelWrappingHandler via ModelLoadingPlugin
[Continuity] Registered 20+ CTM methods - texture replacements ready for loading
[Continuity] Enabling ModelWrappingHandler - shouldWrapCtm: true, hasEmissives: false
[Continuity] ModelWrappingHandler instance created - wrapCtm: true, wrapEmissive: false
[Continuity] RenderUtil.ReloadListener.reload() - sprite finder updated
```

### Logs That DON'T APPEAR (Broken):
```
[Continuity] CtmPropertiesLoader.loadAll() - starting resource pack scan ❌
[Continuity] Found CTM properties file: minecraft:optifine/ctm/default/bookshelf/bookshelf.properties ❌
[Continuity] Loaded properties file - 7 properties ❌
[Continuity] CtmPropertiesLoader.load() - file: ..., method: horizontal, loaderFound: true ❌
[Continuity] Created properties - true ❌
[Continuity] SpriteLoaderMixin.modifySupplier() called - id: ..., has context ❌
[Continuity] SpriteLoaderMixin.modifyFunction() called - id: ..., has emissive control ❌
[Continuity] onReturnStitch() - spriteCount: ..., emissiveCount: ... ❌
```

### In-Game Results:
- Stone blocks: Plain texture (no connected texture) ❌
- Bookshelf: Plain texture (no horizontal connection) ❌
- Glass panes: Plain texture (no glass CTM) ❌
- Random blocks: Same texture everywhere (no variation) ❌

---

## The Missing Link: BakedModelManagerReloadExtension Never Created

### Where It Should Be Called

**Old 1.21.6 Pattern:**
```java
class BakedModelManagerReloadHandler implements SimpleSynchronousResourceReloadListener {
    public void reload(ResourceManager manager) {
        BakedModelManagerReloadExtension extension = 
            new BakedModelManagerReloadExtension(manager, executor);
        
        extension.setContext();  // Set thread-local
        // CTM properties loaded automatically in constructor
    }
}
```

**Current 1.21.10 Status:**
```java
// BakedModelManagerReloadExtension CLASS EXISTS
// But it's NEVER INSTANTIATED

// No resource reload listener registered for it
// No place calls: new BakedModelManagerReloadExtension(...)
// No place calls: CtmPropertiesLoader.loadAllWithState(...)
// No place calls: setContext() to populate thread-local
```

### Proof It's Missing

The following class exists but has **ZERO USAGES**:
```java
public class BakedModelManagerReloadExtension implements BakedModelManagerBakeContext {
    public BakedModelManagerReloadExtension(ResourceManager resourceManager, 
                                            Executor prepareExecutor) {
        // Constructor never called!
        ctmLoadingResultFuture = CompletableFuture.supplyAsync(
            () -> CtmPropertiesLoader.loadAllWithState(resourceManager), 
            prepareExecutor);
        // This line is NEVER EXECUTED
    }
    
    public void setContext() {
        // This is NEVER CALLED
        SpriteLoaderLoadContext.THREAD_LOCAL.set(spriteLoaderLoadContext);
    }
}
```

---

## Solution: The Missing Resource Reload Listener

### What Needs to Happen

A **resource reload listener** must be created that:

1. **Listens for resource pack changes** (via ResourceManagerHelper)
2. **Creates BakedModelManagerReloadExtension instance** 
3. **Triggers CtmPropertiesLoader.loadAllWithState()**
4. **Sets SpriteLoaderLoadContext thread-local**
5. **Registers quad processors with ModelWrappingHandler**

### Pattern to Follow

```java
public class CtmResourceReloadListener implements SimpleSynchronousResourceReloadListener {
    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
            .registerReloadListener(new CtmResourceReloadListener());
    }
    
    public void reload(ResourceManager manager) {
        // Create the missing orchestrator
        BakedModelManagerReloadExtension extension = 
            new BakedModelManagerReloadExtension(manager, executor);
        
        // Set up context for sprite loader mixin
        extension.setContext();
        
        // Load properties and register processors
        extension.beforeBake(atlasPreparations);
        extension.apply();
    }
}
```

### Where It Gets Registered

Must be called in `ContinuityClient.onInitializeClient()`:
```java
@Override
public void onInitializeClient() {
    LOGGER.info("[Continuity] Initialization started");
    
    // ... existing initialization ...
    
    // ADD THIS:
    CtmResourceReloadListener.init();
    
    // NOW CTM properties will load!
}
```

---

## Deleted Files: Their Roles in the Pipeline

### File 1: BakedModelManagerReloadHandler.java
**Role**: RESOURCE RELOAD LISTENER  
**Responsibility**: Listen for resource pack changes, trigger orchestration  
**Status**: ❌ FUNCTIONALITY MISSING - No listener registered

### File 2: BakedModelManagerReloadExtension.java
**Role**: ORCHESTRATOR  
**Responsibility**: Coordinate CTM loading, context setup, processor registration  
**Status**: ⚠️ CLASS EXISTS BUT NEVER INSTANTIATED

### File 3: CtmPropertiesReloadHandler.java
**Role**: RESOURCE LISTENER (alternative pattern)  
**Responsibility**: Same as File 1  
**Status**: ❌ FUNCTIONALITY MISSING - No listener registered

### File 4: CtmPropertiesProcessorRegistry.java
**Role**: REGISTRY STORAGE  
**Responsibility**: Store CTM loaders and processors  
**Status**: ✅ REPLACED BY CtmLoaderRegistryImpl (but never populated with processors)

### File 5: BakedModelManagerBakeContext.java
**Role**: THREAD-LOCAL CONTEXT  
**Responsibility**: Store emissive mappings per thread  
**Status**: ⚠️ REPLACED BY SpriteLoaderStitchContext (but context never populated)

### File 6: BakedModelManagerMixin.java
**Role**: INJECTION POINTS  
**Responsibility**: Intercept model manager, trigger initialization  
**Status**: ✅ REPLACED BY SpriteAtlasTextureMixin (but not triggering CTM load)

### File 7: BakedModelManagerBakeContext.java (also in mixin)
**Role**: MIXIN INTERFACE  
**Responsibility**: Expose reload functionality  
**Status**: ✅ REPLACED BY distributed pattern

---

## The Real Problem: Architectural Mismatch

### Old 1.21.6: Centralized Around BakedModelManager
```
Resource Manager
  └─ Fires reload event
     └─ BakedModelManagerReloadHandler.reload()
        └─ Creates BakedModelManagerReloadExtension
           └─ Loads CTM properties
           └─ Sets up context
           └─ Registers processors
           └─ Enables wrapping
        └─ Models baked with CTM applied
```

### New 1.21.10: Distributed (But Incomplete)
```
ContinuityClient.onInitializeClient()
  ├─ Registers 20+ CTM loaders ✓
  ├─ Initializes ModelWrappingHandler ✓
  └─ [MISSING] Load CTM properties ❌
  
SpriteAtlasTextureMixin.onUpload()
  ├─ Stores atlas reference ✓
  └─ Enables wrapping ✓
     └─ [MISSING] Quad processors to apply ❌
     
SpriteLoaderMixin injection points
  ├─ Ready to process sprites ✓
  └─ [MISSING] Context with CTM properties ❌
```

---

## Why the Tests Show: Everything Works But Nothing Works

### What the Tests Show

✅ **Initialization logs appear**: CTM loaders registered, system ready  
✅ **Mixin injection successful**: No mixin errors  
✅ **Model wrapping enabled**: Flag set correctly  
✅ **Minecraft launches**: No crashes  

### But In-Game

❌ **No CTM textures**: Because no quad processors exist  
❌ **No connected textures**: Because CtmPropertiesLoader never called  
❌ **No texture variants**: Because no properties parsed  

### The Paradox

The initialization system is perfect. It's just **never triggered for CTM properties loading**.

It's like building a car with:
- ✅ Engine installed and running
- ✅ Transmission working
- ✅ Wheels spinning
- ❌ **But the fuel tank is empty and no fuel pump installed**

---

## Solution Strategy: 3-Step Fix

### Step 1: Create CTM Resource Reload Listener
A simple class that creates `BakedModelManagerReloadExtension` and triggers the initialization pipeline.

**Location**: `src/main/java/me/pepperbell/continuity/client/resource/CtmResourceReloadListener.java`

### Step 2: Register the Listener
Call `CtmResourceReloadListener.init()` in `ContinuityClient.onInitializeClient()`

**Location**: Modify `ContinuityClient.java` initialization

### Step 3: Test and Verify
- Rebuild
- Check for CTM property loading logs
- Test in-game for connected textures

---

## Files That Actually Need to Be Restored

Not deleted, but **NEED RESTORATION OF FUNCTIONALITY**:

1. **BakedModelManagerReloadExtension** (already exists, class stays)
   - Purpose: Still valid as orchestrator
   - Action: Just needs to be called

2. **SpriteLoaderLoadContext** (already exists, class stays)
   - Purpose: Still valid for thread-local context
   - Action: Just needs to be populated

3. **CtmPropertiesLoader** (already exists, class stays)
   - Purpose: Still valid for loading properties
   - Action: Just needs to be called

---

## Key Insight

**The deleted files weren't really "deleted" in functionality - they were just orphaned.**

The orchestration logic (`BakedModelManagerReloadExtension`) still exists but is never used because there's no resource reload listener to trigger it.

**The fix is not to recreate deleted files, but to CREATE A NEW RESOURCE RELOAD LISTENER that uses the existing `BakedModelManagerReloadExtension` orchestrator.**

---

## Timeline Summary

### 1.21.6 (Working)
```
Resource Reload → BakedModelManagerReloadHandler 
                → BakedModelManagerReloadExtension 
                → CtmPropertiesLoader.loadAllWithState() 
                → Context setup 
                → Processors registered 
                → CTM works ✅
```

### 1.21.10 (Broken)
```
[Nothing triggers CTM loading] ❌
→ [BakedModelManagerReloadExtension never created]
→ [CtmPropertiesLoader never called]
→ [Context never populated]
→ [Processors never registered]
→ CTM broken ❌
```

### 1.21.10 (Fixed - Required)
```
ContinuityClient.init() 
→ CtmResourceReloadListener.init() [NEW]
→ [On resource load]
→ CtmResourceReloadListener.reload()
→ BakedModelManagerReloadExtension [USE EXISTING]
→ CtmPropertiesLoader.loadAllWithState() [CALL EXISTING]
→ Context setup [USE EXISTING]
→ Processors registered [USE EXISTING]
→ CTM works ✅
```

---

## Implementation Complete ✅

### What Was Implemented (November 9, 2025)

#### 1. Created CtmResourceReloadListener.java
**Location**: `src/main/java/me/pepperbell/continuity/client/resource/CtmResourceReloadListener.java`

```java
public class CtmResourceReloadListener implements SimpleSynchronousResourceReloadListener {
    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(INSTANCE);
    }
    
    @Override
    public void reload(ResourceManager manager) {
        Executor prepareExecutor = Runnable::run;
        BakedModelManagerReloadExtension extension = 
                new BakedModelManagerReloadExtension(manager, prepareExecutor);
        extension.setContext();
        BakedModelManagerReloadExtensionHolder.set(extension);
    }
}
```

**Purpose**: 
- Registers as Fabric resource reload listener
- Creates `BakedModelManagerReloadExtension` on resource reload
- Sets up thread-local context for sprite loading
- Stores extension reference for mixin access

#### 2. Registered Listener in ContinuityClient.java
**Location**: `src/main/java/me/pepperbell/continuity/client/ContinuityClient.java`

Added to `onInitializeClient()`:
```java
// PHASE 5: Register CTM properties resource reload listener
me.pepperbell.continuity.client.resource.CtmResourceReloadListener.init();
```

**Purpose**: Ensures listener is registered during mod initialization

#### 3. Updated BakedModelManagerReloadExtension.java
**Location**: `src/main/java/me/pepperbell/continuity/client/resource/BakedModelManagerReloadExtension.java`

**Changes**:
- ❌ Removed: `implements BakedModelManagerBakeContext` (deprecated interface)
- ❌ Removed: `beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation>)` (deprecated API)
- ✅ Added: `beforeBake(Map<Identifier, Sprite> sprites, Sprite missingSprite)` (new 1.21.10 API)

**New Implementation**:
```java
public void beforeBake(Map<Identifier, Sprite> sprites, Sprite missingSprite) {
    CtmPropertiesLoader.LoadingResult result = ctmLoadingResultFuture.join();
    
    List<QuadProcessors.ProcessorHolder> processorHolders = 
        result.createProcessorHolders(spriteId -> {
            Sprite sprite = sprites.get(spriteId.getTextureId());
            return (sprite != null) ? sprite : missingSprite;
        });
    
    this.processorHolders = processorHolders;
    ModelWrappingHandler.setInstance(!processorHolders.isEmpty(), 
                                      wrapEmissiveModels.get());
}
```

**Purpose**: Adapted to use `StitchResult` sprite map instead of deprecated `AtlasPreparation`

#### 4. Enhanced SpriteAtlasTextureMixin.java
**Location**: `src/main/java/me/pepperbell/continuity/client/mixin/SpriteAtlasTextureMixin.java`

**Added to `continuity$onUpload()` injection**:
```java
if (shouldWrapCtm) {
    BakedModelManagerReloadExtension extension = 
        CtmResourceReloadListener.BakedModelManagerReloadExtensionHolder.get();
    
    if (extension != null) {
        // Get sprites from StitchResult
        Map<Identifier, Sprite> sprites = 
            ((StitchResultExtension) (Object) stitchResult).continuity$getSprites();
        
        // Get missing sprite fallback
        Sprite missingSprite = sprites.get(Identifier.of("minecraft", "missingno"));
        if (missingSprite == null) {
            missingSprite = sprites.values().iterator().next();
        }
        
        // Call beforeBake with sprite map
        extension.beforeBake(sprites, missingSprite);
        
        // Register quad processors
        extension.apply();
    }
}
```

**Purpose**: 
- Retrieves stored `BakedModelManagerReloadExtension` from holder
- Extracts sprite map from `StitchResult` using mixin interface
- Calls `beforeBake()` to create quad processors
- Calls `apply()` to register processors with `QuadProcessors`

---

## The Complete Flow (1.21.10 Implementation)

### Step 1: Mod Initialization
```
ContinuityClient.onInitializeClient()
  └─ CtmResourceReloadListener.init()
     └─ Registers listener with Fabric ResourceManagerHelper
```

### Step 2: Resource Pack Load/Reload
```
Resource Reload Event
  └─ CtmResourceReloadListener.reload(ResourceManager)
     ├─ Creates BakedModelManagerReloadExtension(manager, executor)
     │  └─ Triggers async: CtmPropertiesLoader.loadAllWithState()
     │     └─ Scans optifine/ctm/ directories
     │     └─ Parses .properties files
     │     └─ Creates CtmProperties objects
     ├─ Calls extension.setContext()
     │  └─ Sets SpriteLoaderLoadContext.THREAD_LOCAL
     └─ Stores: BakedModelManagerReloadExtensionHolder.set(extension)
```

### Step 3: Sprite Atlas Stitching
```
SpriteLoader.stitch() creates StitchResult
  └─ Contains: Map<Identifier, Sprite> sprites
  └─ SpriteLoaderMixin processes emissive textures
     └─ Uses thread-local context set in Step 2
```

### Step 4: Atlas Upload (CTM Processor Registration)
```
SpriteAtlasTexture.upload(StitchResult)
  └─ SpriteAtlasTextureMixin.continuity$onUpload()
     ├─ Retrieves: BakedModelManagerReloadExtension from holder
     ├─ Extracts: Map<Identifier, Sprite> from StitchResult
     ├─ Calls: extension.beforeBake(sprites, missingSprite)
     │  └─ Waits for CTM properties loading (join())
     │  └─ Creates List<QuadProcessors.ProcessorHolder>
     │  └─ Configures ModelWrappingHandler
     └─ Calls: extension.apply()
        └─ Registers: QuadProcessors.reload(processorHolders)
```

### Step 5: Model Baking
```
Models wrapped with CtmBlockStateModel
  └─ Quad processor applied during rendering
  └─ Connected textures calculated
  └─ Textures replaced
  
Result: CTM works! ✅
```

---

## Build Results

**Build Command**: `.\gradlew build`  
**Status**: ✅ **BUILD SUCCESSFUL in 13s**  
**Compilation Errors**: 0  
**JAR Created**: `build/libs/continuity-3.0.1+1.21.10.jar`

---

## Key Architectural Changes

### What Changed from 1.21.6 to 1.21.10

| Component | 1.21.6 (Old) | 1.21.10 (New) |
|-----------|--------------|---------------|
| **Orchestrator** | BakedModelManagerReloadExtension (orphaned) | BakedModelManagerReloadExtension (now called) |
| **Trigger** | BakedModelManagerMixin.onHeadBake() | CtmResourceReloadListener.reload() |
| **Sprite Access** | SpriteAtlasManager.AtlasPreparation | StitchResult.sprites map |
| **Processor Setup** | beforeBake(AtlasPreparation) | beforeBake(Map<Identifier, Sprite>) |
| **Registration Point** | BakedModelManager lifecycle | SpriteAtlasTexture.upload() injection |

### What Stayed the Same

✅ `CtmPropertiesLoader` - Still loads .properties files  
✅ `QuadProcessors` - Still manages processor registry  
✅ `ModelWrappingHandler` - Still wraps models  
✅ `SpriteLoaderLoadContext` - Still provides thread-local context  
✅ Core CTM algorithms - All processing logic intact  

---

## Testing Status

**Code Status**: ✅ COMPLETE - All compilation errors resolved  
**Build Status**: ✅ SUCCESSFUL - JAR created  
**Runtime Status**: ⏳ PENDING - Awaiting in-game verification  

**Next Step**: 
1. Copy JAR to `.minecraft/mods/`
2. Launch Minecraft 1.21.10 with Fabric
3. Check logs for CTM property loading messages
4. Test in-game with connected textures (stone, bookshelf, glass)

---

**Status**: ✅ **IMPLEMENTATION COMPLETE**  
**Build**: ✅ **SUCCESSFUL**  
**Next Action**: Runtime testing in Minecraft  

*Updated: November 9, 2025 - Implementation Phase Complete*
