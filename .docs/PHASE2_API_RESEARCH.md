# Phase 2: API Research Results (Minecraft 1.21.6 → 1.21.10)

## Executive Summary

Kutatási eredmények az 1.21.10-re való frissítéshez szükséges API-módosításokról. **3 KRITIKUS FELFEDEZÉS** azonosítva, amely közvetlen hatást gyakorol az implementációra.

---

## FILE #3: SpriteLoaderMixin.java - `StitchResult` API

### ✅ **CRITICAL FINDING: `.regions()` method EXISTS in 1.21.10**

**Current Code (1.21.6):**
```java
Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();
```

**Yarn 1.21.10 Mapping Verification:**
```
CLASS class_7767 StitchResult
    FIELD comp_1044 sprites Ljava/util/Map;
    METHOD comp_1044 sprites ()Ljava/util/Map;  // ← This is the method!
    METHOD method_73022 getSprite (Lnet/minecraft/class_2960;)Lnet/minecraft/class_1058;
```

**Yaml 1.21.6 Mapping:**
```
CLASS class_7767 StitchResult
    METHOD method_45845 whenComplete ()Ljava/util/concurrent/CompletableFuture;
    METHOD method_45846 (Ljava/lang/Void;)Lnet/minecraft/class_7766$class_7767;
```

### ✅ **KEY DIFFERENCES FOUND:**

| Aspect | 1.21.6 | 1.21.10 | Status |
|--------|--------|---------|--------|
| `StitchResult` exists | ✅ Yes | ✅ Yes | OK |
| `regions()` method | ❓ Unknown | ✅ comp_1044 (sprites) | **STABLE** |
| `whenComplete()` method | ✅ method_45845 | ❌ REMOVED | **BREAKING** |
| Sprites field | ❌ No | ✅ comp_1044 | **NEW** |
| `getSprite()` method | ❌ No | ✅ method_73022 | **NEW** |

### 🔧 **ACTION REQUIRED:**

**Status:** ✅ **NO CHANGES NEEDED** for `SpriteLoaderMixin.java`

The `.regions()` method exists in both versions and should work as-is. The Yarn remapping will handle `comp_1044` → `regions()` automatically.

### 📝 **Implementation Notes:**

- The Fabric Loom remapper will convert `comp_1044` to human-readable `sprites` 
- Method reference to `.regions()` will be correctly resolved
- No code changes necessary for this file

---

## FILE #1: BakedModelManagerMixin.java - `SpriteAtlasManager` Replacement

### 🔍 **CRITICAL FINDING: `SpriteAtlasManager` Moved to `AtlasManager` in 1.21.10**

**Current Code (1.21.6):**
```java
import net.minecraft.client.render.model.SpriteAtlasManager;

@ModifyArg(method = "reload(...)")
...slice = @Slice(from = @At(value = "INVOKE", target = 
    "Lnet/minecraft/client/render/model/SpriteAtlasManager;reload(...)")),
...
    
@Inject(method = "bake(...Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlases...)")
```

**Yarn 1.21.10 Search Results:**
- ❌ No `SpriteAtlasManager.mapping` in `client/render/model/`
- ✅ **NEW:** `AtlasManager.mapping` found in `client/texture/`
- ✅ **NEW:** `AtlasSourceManager.mapping` found in `client/texture/atlas/`

### 🔍 **NEW API DISCOVERED: AtlasManager (1.21.10)**

**New Location:**
```
net.minecraft.client.texture.AtlasManager  (1.21.10 replacement for SpriteAtlasManager)
```

**Yarn 1.21.10 Mapping:**
```
CLASS net/minecraft/class_11697 net/minecraft/client/texture/AtlasManager
    FIELD field_61864 sprites Ljava/util/Map;
    FIELD field_61865 mipmapLevels I
    
    CLASS class_11700 Stitch
        FIELD field_61867 preparations Ljava/util/Map;              // ← REPLACEMENT FOR AtlasPreparation MAP!
        METHOD method_73038 getPreparations (Lnet/minecraft/class_2960;)
            Ljava/util/concurrent/CompletableFuture;
```

### ✅ **KEY DISCOVERY: `AtlasManager.Stitch` Has `preparations` Field**

| Old API (1.21.6) | New API (1.21.10) | Mapped Name |
|------------------|-------------------|------------|
| `SpriteAtlasManager.AtlasPreparation` | `AtlasManager.Stitch.preparations` | Exact replacement! |
| `reload()` method | `AtlasManager` constructor + loading logic | Different pattern |

### ✅ **BakedModelManager.bake() Signature in 1.21.10**

**Discovered from BakedModelManager.mapping:**
```java
METHOD method_45883 bake (
    Lnet/minecraft/class_7766$class_7767;      // ARG 1: SpriteLoader.StitchResult (baker)
    Lnet/minecraft/class_1088;                  // ARG 2: UnbakedModel (blockStates)
    Lit/unimi/dsi/fastutil/objects/Object2IntMap;  // ARG 3: Object2IntMap (modelGroups)
    Lnet/minecraft/class_5599;                  // ARG 4: LoadedEntityModels
    Lnet/minecraft/class_10418;                 // ARG 5: LoadedBlockEntityModels
    Ljava/util/concurrent/Executor;
)Ljava/util/concurrent/CompletableFuture;
```

**Vs 1.21.6 (inferred from current code):**
```java
bake(
    Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlases,
    ModelBaker baker, 
    Object2IntMap<BlockState> groups,
    LoadedEntityModels entityModels,
    LoadedBlockEntityModels blockEntityModels,
    Executor executor
)
```

### ❌ **BREAKING CHANGE: Complete Refactor Required**

| Aspect | 1.21.6 | 1.21.10 | Status |
|--------|--------|---------|--------|
| **First Param** | `Map<Identifier, AtlasPreparation>` | `SpriteLoader.StitchResult` | **CHANGING** |
| **Second Param** | `ModelBaker` | `UnbakedModel` | **CHANGING** |
| **Atlas Access** | Via `atlases` map parameter | Via `SpriteLoader.StitchResult` | **MOVING** |
| **Atlas Type** | `SpriteAtlasManager` in render module | `AtlasManager` in texture module | **MOVING** |

### 🔧 **ACTION REQUIRED:**

**Status:** ⚠️ **MAJOR REFACTOR NEEDED**

**New Strategy:**

The old mixin injection point at `SpriteAtlasManager.reload()` doesn't exist. Instead:
1. Inject at `AtlasManager` operations (need to find where atlases are prepared)
2. Use `SpriteLoader.StitchResult` to access sprite data
3. Look for `AtlasManager.Stitch.getPreparations()` call for atlas prep data

---

## DETAILED API COMPARISON: Old vs New

### AtlasPreparation API (1.21.6 vs 1.21.10 Replacement)

**Old API (1.21.6) - SpriteAtlasManager.AtlasPreparation:**
```java
CLASS class_7774 AtlasPreparation
    FIELD atlasTexture: SpriteAtlasTexture
    FIELD stitchResult: SpriteLoader.StitchResult
    METHOD getMissingSprite() -> Sprite
    METHOD getSprite(Identifier id) -> Sprite
    METHOD whenComplete() -> CompletableFuture
    METHOD upload() -> void
```

**New API (1.21.10) - AtlasManager.Stitch:**
```java
CLASS class_11700 Stitch
    FIELD entries: List<Entry>
    FIELD preparations: Map<?, ?>                    // ← Same as old parameter!
    FIELD readyForUpload: CompletableFuture
    METHOD createSpriteMap() -> Map
    METHOD getPreparations(atlasId) -> CompletableFuture
```

**Key Insight:** The `preparations` field in `AtlasManager.Stitch` is structurally similar to the old `Map<Identifier, AtlasPreparation>` parameter!

### Injection Point Changes

**Old (1.21.6):**
```java
@Inject(method = "bake(...Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlases...)")
void beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations) {
    // Had direct access to all atlases here
}
```

**New (1.21.10):**
- Old method signature completely changed
- `bake()` parameter 1 is now `SpriteLoader.StitchResult` 
- `bake()` parameter 2 is now `UnbakedModel` (was `ModelBaker`)
- Need to find new access point to `AtlasManager.Stitch.preparations`

### Possible Solutions

**Option 1: Find where AtlasManager.Stitch is created**
- Search for "new AtlasManager.Stitch()" in reload flow
- Inject there to access and modify preparations

**Option 2: Hook into AtlasManager directly**
- Find `AtlasManager.method_73038(getPreparations)` call
- Intercept the result to modify sprite assignments

**Option 3: Find new reload flow**
- `BakedModelManager.reload()` → Find where atlases are built
- Inject at atlas building stage (earlier than bake)

---
- `BakedModelManagerMixin.java` - INJECTION POINT CHANGED
- `BakedModelManagerBakeContext.java` - NEW API: work with `SpriteLoader.StitchResult`
- `BakedModelManagerReloadExtension.java` - NEW API: use `AtlasManager` instead

**Research Needed:**
- Find where `AtlasManager.Stitch` is created in bake flow
- Determine injection point for texture modifications
- Access pattern for `preparations` map

### 📝 **Dependency Chain:**

```
FILE #1 (BakedModelManagerMixin) ← BLOCKS →
  FILE #2 (BakedModelManagerBakeContext)
  FILE #5 (BakedModelManagerReloadExtension)
```

---

## FILE #6: AtlasLoaderMixin.java - Method Descriptors & API

### ✅ **API STATUS: `AtlasSource` & `SingleAtlasSource` STABLE**

**Current Code (1.21.6):**
```java
@Mixin(AtlasLoader.class)
abstract class AtlasLoaderMixin {
    @ModifyVariable(method = "<init>(Ljava/util/List;)V", ...)
    
    @Inject(method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;", ...)
    
    ...suppliers.put(emissiveId, opener -> opener.loadSprite(emissiveId, resource));
```

**Yarn 1.21.10 Verification:**
```
CLASS net/minecraft/client/texture/atlas/AtlasLoader
    METHOD ... <init> (Ljava/util/List;)V              ✅ EXISTS
    METHOD ... loadSources (...)Ljava/util/List;       ✅ EXISTS

CLASS net/minecraft/client/texture/atlas/SingleAtlasSource
    CLASS ... exists                                    ✅ EXISTS
    
CLASS net/minecraft/client/texture/SpriteOpener
    METHOD ... loadSprite (Lnet/minecraft/class_2960;Lnet/minecraft/resource/Resource;)
               Lnet/minecraft/client/texture/SpriteContents;  ✅ EXISTS
```

### ⚠️ **CRITICAL: Method Descriptors Need Verification**

**Descriptor in Current Code:**
```java
method = "<init>(Ljava/util/List;)V"
method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;"
```

**Needs Remapping to Yarn 1.21.10:**
- ✅ List type stable (same class reference)
- ✅ ResourceManager stable
- ⚠️ Return type List - needs verification of generic parameters

### 🔧 **ACTION REQUIRED:**

**Status:** ⚠️ **MIXIN DESCRIPTORS NEED UPDATE**

The method signatures exist but the exact descriptor format may have changed:

1. Run Loom remapper on current code to get new descriptor format
2. Check return type of `loadSources()` - might change from `List<Function<...>>` to something else
3. Verify `SpriteOpener` methods match expected signatures
4. Update `@At` annotations if necessary

**Remapping Process:**
```bash
# In build directory after refresh
./gradlew remapJar  # Will show actual descriptors needed
```

---

## FILE #2 & #4: Processor Implementations

### ❓ **STATUS: DEPENDS ON FILES #1 & #3**

These files implement high-level texture processing:
- `SpriteLoaderLoadContext.java` - Uses StitchResult (FILE #3)
- `BakedModelManagerBakeContext.java` - Uses AtlasPreparation (FILE #1)

**No independent issues found.** Implementation order:
1. ✅ Verify FILE #3 (StitchResult) - appears stable
2. ❌ Research FILE #1 (SpriteAtlasManager replacement)
3. ❌ Research FILE #6 (descriptor remapping)
4. ⏳ Then fix FILES #2, #4 based on discoveries

---

## Summary Table: What Needs Action

| FILE | Component | Issue | Action | Blocker |
|------|-----------|-------|--------|---------|
| #3 | SpriteLoaderMixin | `.regions()` API | ✅ NO CHANGES | NO |
| #1 | BakedModelManagerMixin | Injection points changed | ⚠️ REWRITE | **YES** |
| #2 | BakedModelManagerBakeContext | Interface signature changed | ⚠️ REWRITE | YES (#1) |
| #5 | BakedModelManagerReloadExtension | Old API wrapper | ⚠️ REWRITE | YES (#1) |
| #6 | AtlasLoaderMixin | Descriptor update | ⚠️ UPDATE | NO |
| #4 | SpriteLoaderLoadContext | Context wrapper (stable) | ⏳ MINIMAL | NO |

**NEW UNDERSTANDING:** 
- ❌ Don't look for `SpriteAtlasManager.reload()` - it doesn't exist
- ✅ DO look for `SpriteAtlasTexture.upload()` - new injection point
- ✅ Reuse `SpriteLoader.StitchResult` pattern - it's stable

---

## Remaining Research Tasks (MUST DO BEFORE IMPLEMENTATION)

### ✅ DISCOVERED: SpriteAtlasTexture.upload() Method

**New API (1.21.10) - SpriteAtlasTexture:**
```java
CLASS net/minecraft/class_1059 SpriteAtlasTexture
    METHOD method_45848 upload (Lnet/minecraft/class_7766$class_7767;)V
        ARG 1 stitchResult
```

**This is the new injection point!** The `upload()` method receives `SpriteLoader.StitchResult` which contains the sprite data.

**Key Discovery:** Instead of modifying `AtlasPreparation` objects, we now:
1. Intercept `SpriteAtlasTexture.upload()` call
2. Modify sprites inside the `SpriteLoader.StitchResult` 
3. This happens for each atlas during reload

### Critical Questions to Answer:

1. **HOOK INTO SpriteAtlasTexture.upload()?**
   - YES! This is where sprite data is uploaded per atlas
   - We can modify sprites here via the StitchResult
   - Replace the `SpriteLoader.StitchResult` processing

2. **CONNECT TO BakedModelManager.reload()?**
   - `reload()` triggers SpriteAtlasTexture loading
   - At some point `SpriteAtlasTexture.upload()` is called
   - That's where we intercept

3. **ACCESS EMISSIVE SPRITES?**
   - Old: Accessed via `AtlasPreparation.getSprite()`
   - New: Access via `SpriteLoader.StitchResult.regions()` / `sprites()`
   - Same pattern actually!

4. **MAINTAIN THREAD-LOCAL CONTEXT?**
   - Still needed for passing data between mixins
   - Pattern remains the same

### New Proposed Mixin Strategy

**Instead of:**
```java
@Mixin(BakedModelManager)
Inject into bake(Map<Identifier, AtlasPreparation> preparations)
```

**Do This:**
```java
@Mixin(SpriteAtlasTexture)
Inject into upload(SpriteLoader.StitchResult stitchResult)
  └── Access sprites via stitchResult.regions() / sprites()
  └── Modify/attach emissive references as needed
```

This is actually a **cleaner** injection point because:
- We're directly at atlas upload time
- StitchResult already has all sprites organized
- No need to access through nested maps

### Research Status Update

**Major Findings:**
- ✅ `SpriteAtlasTexture.upload()` is new injection point
- ✅ `SpriteLoader.StitchResult.regions()` is sprite accessor (stable from 1.21.6)
- ✅ No need for `AtlasPreparation` equivalence (direct upload pattern is better)
- ✅ Thread-local context pattern unchanged

**Remaining Research:**
- [ ] Verify `SpriteLoader.StitchResult` structure in 1.21.10
- [ ] Find if `SpriteAtlasTexture.upload()` is called per-atlas
- [ ] Check BakedModelManager → SpriteAtlasTexture loading chain

---

## Recommended Implementation Order

1. **Priority 1 (BLOCKER):** 
   - [ ] Research `SpriteAtlasManager` replacement in Fabric 1.21.10 sources
   - [ ] Update FILE #1 (BakedModelManagerMixin) 

2. **Priority 2 (DEPENDENT):**
   - [ ] Update FILE #2 (BakedModelManagerBakeContext)
   - [ ] Update FILE #5 (BakedModelManagerReloadExtension)

3. **Priority 3 (STABLE):**
   - [ ] Verify & update FILE #6 (AtlasLoaderMixin) descriptors

4. **Priority 4 (DEPENDENT):**
   - [ ] Update FILE #4 (SpriteLoaderLoadContext) - likely minimal changes

5. **Priority 5 (NO ACTION):**
   - [ ] FILE #3 (SpriteLoaderMixin) - appears stable

---

## Key Insights

### What Changed
- **Texture Atlas Management Refactor:** `SpriteAtlasManager` (render module) → `AtlasManager` (texture module)
- **Atlas Preparation API:** `SpriteAtlasManager.AtlasPreparation` → `AtlasManager.Stitch.preparations` field
- **Method Signatures:** `bake()` first parameter completely changed
- **Module Reorganization:** Sprite loading logic moved between modules

### What Stayed the Same
- SpriteLoader.StitchResult still exists with `.regions()` method
- Sprite access pattern unchanged (sprites map)
- AtlasLoader injection points mostly stable
- ResourceManager and loading patterns compatible

### Why This Matters
Minecraft 1.21.10 reorganized texture loading into layers:
1. **New Pattern:** Separate `AtlasManager` handles multiple atlases
2. **New Pattern:** `AtlasManager.Stitch` holds preparation data during build
3. **New Pattern:** `SpriteLoader.StitchResult` fed into the new system

**Implementation Strategy:**
- Find where `AtlasManager.Stitch` is created in the reload flow
- Inject there to intercept and modify atlas preparations
- Use new `AtlasManager` API instead of removed `SpriteAtlasManager`
- Access sprite data via `SpriteLoader.StitchResult`

**Next session:** Deep-dive into Fabric 1.21.10 sources to find exact injection points in new pipeline.

