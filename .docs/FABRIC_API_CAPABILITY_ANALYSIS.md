# Fabric API Capability Analysis - AtlasStorage & AtlasLoaderMixin Review

**Phase**: Architecture Review (Pre-Implementation)  
**Date**: November 9, 2025  
**Purpose**: Verify current implementations follow Fabric best practices and identify any missing API capabilities

---

## Executive Summary

**Status**: ✅ IMPLEMENTATIONS ARE SOUND

Both `AtlasStorage` and `AtlasLoaderMixin` are properly designed and follow Fabric conventions correctly. After detailed analysis of the actual implementations versus Fabric API patterns:

1. **AtlasStorage** ✅ 
   - Correctly uses volatile for thread visibility
   - Follows Mixin restrictions (no public static methods in mixin itself)
   - Minimalist design is appropriate—storing more would be premature optimization
   - Matches Fabric's pattern for cross-mixin data sharing

2. **AtlasLoaderMixin** ✅
   - Properly uses `@ModifyVariable` to inject extra sprite IDs before stitching
   - Correctly captures `AtlasSource.SpriteRegion` via LocalCapture
   - EmissiveSuffixLoader integration is well-designed
   - Thread-local contexts follow Fabric resource loader patterns

**Finding**: No missing API capabilities identified. Current implementations are sufficient for the architecture.

---

## Detailed Analysis

### 1. AtlasStorage Implementation Review

**File**: `src/main/java/me/pepperbell/continuity/client/util/AtlasStorage.java` (30 lines)

#### Current Design
```java
public final class AtlasStorage {
    private static volatile SpriteAtlasTexture blockAtlas;
    
    public static void setBlockAtlas(SpriteAtlasTexture atlas)
    public static SpriteAtlasTexture getBlockAtlas()
}
```

#### Architectural Pattern Analysis

**Why External Utility Class?**
- **Mixin Rule #1**: Mixins cannot contain public static methods (would pollute target class)
- **Solution**: Store reference in separate utility class that SpriteAtlasTextureMixin can safely call
- **This Pattern**: Matches Fabric's own code organization (`AbstractBlockRenderContext`, texture helpers)

**Thread Safety Assessment**
- ✅ `volatile` keyword ensures visibility across threads (atlas set in resource load thread, read in render thread)
- ✅ No synchronization needed (simple field assignment is atomic for reference types)
- ✅ Null-safe (null checks occur in caller code like RenderUtil)

#### Is This Implementation Complete?

**Evaluated Capabilities**:
1. ✅ Store single SpriteAtlasTexture reference — CORRECT SCOPE
2. ❓ Should it store per-atlas metadata?
3. ❓ Should it cache sprite finder?
4. ❓ Should it expose SpriteAtlasTexture.getId()?

**Analysis**:
- Storing ONLY the reference is correct. Metadata would be premature—we'd need to know what data, when to compute it, thread-safety implications
- Caching sprite finder would add complexity without performance benefit (spriteFinder is O(1) lookup)
- No other consumers exist that would need getId()—only RenderUtil uses this

**Conclusion**: ✅ **Design is sufficient and appropriately minimal**. The pattern of "store reference, compute/cache on demand in consumer" is exactly how Fabric does it.

---

### 2. AtlasLoaderMixin Implementation Review

**File**: `src/main/java/me/pepperbell/continuity/client/mixin/AtlasLoaderMixin.java` (67 lines)

#### Current Design Structure
```java
@Mixin(AtlasLoader.class)
abstract class AtlasLoaderMixin {
    // 1. @ModifyVariable: Inject extra sprite IDs
    continuity$modifySources(List<AtlasSource> sources)
    
    // 2. @Inject with LocalCapture: Capture suppliers after loading
    continuity$afterLoadSources(ResourceManager, ..., Map<Identifier, AtlasSource.SpriteRegion> suppliers)
}
```

#### Pattern Verification Against Fabric API

**Pattern 1: Extra IDs Injection (continuity$modifySources)**
- ✅ Uses `@ModifyVariable` at LOAD ordinal 0 (first parameter)
- ✅ Reads `AtlasLoaderInitContext.THREAD_LOCAL` (correct thread-local pattern)
- ✅ Wraps in `SingleAtlasSource` (correct wrapper type for single sprite ID)
- ✅ Handles both ArrayList and immutable collections (defensive)
- ✅ Returns modified list properly

**Verified Against**: Fabric's own usage of `@ModifyVariable` in rendering systems

**Pattern 2: Emissive Suffix Loading (continuity$afterLoadSources)**
- ✅ Uses `@Inject` with `INVOKE` target (points to ImmutableList.builder() call)
- ✅ Uses `LocalCapture.CAPTURE_FAILHARD` (forces verification—good practice)
- ✅ Accesses `Map<Identifier, AtlasSource.SpriteRegion> suppliers` from locals
- ✅ Creates `SpriteContents` via `opener.loadSprite(emissiveId, resource)`
- ✅ Stores mapping in thread-local context for later retrieval

**Verified Against**: Fabric's own ResourceLoader implementations

#### Missing Capabilities?

**Evaluated Concerns**:
1. ❓ Should it validate that extra IDs don't already exist?
2. ❓ Should it handle AtlasSource types other than SingleAtlasSource?
3. ❓ Should it cache emissive resources?
4. ❓ Should it verify TextureContents integrity?

**Analysis**:

1. **Duplicate ID Check**: 
   - Current: Skips if emissiveId already in suppliers (line: `if (!suppliers.containsKey(emissiveId))`)
   - ✅ CORRECT—defensive check is there
   - ✅ Silent skip is appropriate (emissive variants might pre-exist)

2. **AtlasSource Type Flexibility**: 
   - Current: Only creates `SingleAtlasSource`
   - ✅ CORRECT—extra IDs are always single sprites, not collections
   - ✅ Other source types are used by Minecraft but not applicable to emissive variant lookups

3. **Resource Caching**: 
   - Current: Loads fresh each time `loadSources()` is called
   - ✅ CORRECT—resource manager handles caching; mixin shouldn't duplicate
   - ✅ Matches Fabric's principle: "Let the framework cache, not the mod"

4. **Texture Integrity Verification**: 
   - Current: Assumes `opener.loadSprite()` succeeds
   - ✅ CORRECT—ResourceManager would already have validated existence via `optionalResource.isPresent()`
   - ✅ Exception handling would be in ResourceManager, not mixin responsibility

#### Fabric Conventions Compliance

| Convention | AtlasLoaderMixin | Status |
|-----------|------------------|--------|
| Thread-local contexts for resource loading | ✅ Uses `AtlasLoaderInitContext`, `AtlasLoaderLoadContext` | CORRECT |
| @ModifyVariable for collection modification | ✅ At LOAD ordinal 0 before stitching | CORRECT |
| @Inject with LocalCapture for data capture | ✅ Uses CAPTURE_FAILHARD for safety | CORRECT |
| Resource Manager API usage | ✅ `Optional<Resource>` pattern | CORRECT |
| No modification of Minecraft data outside mixin scope | ✅ Only touches suppliers during atlas loading | CORRECT |
| Function<T,R> pattern for lazy loading | ✅ Uses `Function<SpriteOpener, SpriteContents>` | CORRECT |

**Conclusion**: ✅ **Fully compliant with Fabric resource loading patterns**

---

### 3. SpriteAtlasTextureMixin Integration Review

**File**: `src/main/java/me/pepperbell/continuity/client/mixin/SpriteAtlasTextureMixin.java` (147 lines)

#### Injection Point Analysis

**Current Injection**:
```java
@Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At("HEAD"))
private void continuity$onUpload(SpriteLoader.StitchResult stitchResult, CallbackInfo ci)
```

**Why HEAD injection is correct**:
- ✅ Happens AFTER sprite stitching (StitchResult contains all sprites)
- ✅ Happens BEFORE GPU texture upload (allows sprite modification)
- ✅ Before mipmap generation (if applicable)
- ✅ Single thread context (no race conditions during upload)

#### Data Access Patterns

**Pattern 1: Extract sprite map from StitchResult**
```java
Map<Identifier, Sprite> sprites = 
    ((StitchResultExtension) (Object) stitchResult).continuity$getSprites();
```
- ✅ Uses mixin interface extension (safe type casting)
- ✅ Accesses stable API (StitchResult.regions() → sprites)
- ✅ Defensive: gets sprites directly, not derived data

**Pattern 2: Access emissive context**
```java
SpriteLoaderStitchContext context = SpriteLoaderStitchContext.THREAD_LOCAL.get();
boolean hasEmissives = (context != null);
```
- ✅ Checks thread-local set by AtlasLoaderMixin
- ✅ Correct order: AtlasLoaderMixin fires first (during loading), then upload
- ✅ Null-safe: gracefully handles missing context

**Pattern 3: Coordinator synchronization**
```java
CtmInitializationCoordinator coordinator = CtmInitializationCoordinator.getInstance();
BakedModelManagerReloadExtension extension = coordinator.getExtensionWhenReady();
```
- ✅ Singleton pattern for coordinator
- ✅ getExtensionWhenReady() handles both F3+T (blocks) and initial load (returns null)
- ⚠️ **NOTE**: This is where the initial-load problem manifests (discussed in separate doc)

#### Missing Capabilities?

**API Surface Explored**:
1. ✅ `SpriteAtlasTexture.getId()` - accessed via @Shadow
2. ✅ `SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE` - constant used for identity check
3. ✅ `SpriteLoader.StitchResult` - passed as parameter
4. ✅ `Sprite` object - accessed via StitchResultExtension
5. ❓ `SpriteAtlasTexture.getGlId()` - OpenGL texture ID
6. ❓ `SpriteAtlasTexture.getWidth()`, `.getHeight()` - Texture dimensions

**Should we capture more?**

| Data | Current | Needed? | Why/Why Not |
|------|---------|---------|------------|
| Atlas ID | ✅ Accessed | Used for identity check | ✅ CORRECT |
| Sprite map | ✅ Accessed | Used for CTM processing | ✅ CORRECT |
| Atlas dimensions | ❌ Not accessed | Not used anywhere | ❌ NO—premature optimization |
| OpenGL texture ID | ❌ Not accessed | Not used by CTM/emissive | ❌ NO—would break encapsulation |
| Mipmap levels | ❌ Not accessed | Minecraft handles this | ❌ NO—not our responsibility |

**Conclusion**: ✅ **Current data access is appropriate—accessing exactly what's needed, nothing more**

---

## API Capabilities Matrix

### SpriteAtlasTexture API Surface (1.21.10)

| Method/Field | Current Use | Suitable? | Notes |
|--------------|------------|----------|-------|
| `.getId()` | ✅ Atlas identity | YES | Correct for determining if block atlas |
| `.upload(StitchResult)` | ✅ Injection point | YES | Stable, used for CTM processing |
| `.getGlId()` | ❌ Unused | NO | Not needed—GPU texture ID managed by Minecraft |
| `.getWidth()`, `.getHeight()` | ❌ Unused | NO | Not needed—sprite dimensions in Sprite objects |
| `.BLOCK_ATLAS_TEXTURE` | ✅ Identity constant | YES | Used to identify block atlas |
| `.PARTICLE_ATLAS_TEXTURE` | ✅ Not used | NO | Not part of CTM scope |

### SpriteLoader.StitchResult API Surface (1.21.10)

| Method/Field | Current Use | Suitable? | Notes |
|--------------|------------|----------|-------|
| `.regions()` | ✅ Via StitchResultExtension | YES | Returns sprite map |
| `.textureMetadata()` | ❌ Unused | NO | Animation metadata not needed for CTM |
| `.atlasWidth()`, `.atlasHeight()` | ❌ Unused | NO | Calculated from sprite data if needed |

### Thread-Local Context Pattern

| Context | Current | Suitable? | Notes |
|---------|---------|----------|-------|
| `AtlasLoaderInitContext` | ✅ Extra sprite IDs | YES | Injects emissive IDs before stitching |
| `AtlasLoaderLoadContext` | ✅ Emissive mapping | YES | Captures emissive→original sprite links |
| `SpriteLoaderStitchContext` | ✅ Emissive context | YES | Signals that emissives exist this load |

**Conclusion**: ✅ **All thread-local contexts are appropriately scoped and used**

---

## Fabric Conventions Compliance Summary

### Code Organization
- ✅ AtlasStorage in `util/` package (not mixin, not resource)
- ✅ AtlasLoaderMixin in `mixin/` package
- ✅ SpriteAtlasTextureMixin in `mixin/` package
- ✅ Resource contexts in `resource/` package
- **Standard**: Matches Fabric's separation of concerns

### Mixin Rules Adherence
- ✅ No public static methods in mixins (violated only in separate utility)
- ✅ @Unique fields properly named with class prefix
- ✅ Injection points clearly documented
- ✅ Callback parameters use mixin types
- **Standard**: Follows all Mixin framework rules

### Thread Safety
- ✅ Volatile fields where needed (blockAtlas)
- ✅ Thread-local contexts for cross-thread data
- ✅ No manual synchronization (not needed)
- ✅ Atomic operations only (reference assignment)
- **Standard**: Matches Fabric's thread-safety patterns

### Resource API Usage
- ✅ ResourceManager used through public API
- ✅ Optional<Resource> pattern for existence checks
- ✅ Resource/TextureContents lifecycle respected
- ✅ No caching at mixin level (leave to ResourceManager)
- **Standard**: Follows Fabric's resource loading conventions

---

## Performance Implications

### AtlasStorage
- **Memory**: 8 bytes per reference (negligible)
- **CPU**: O(1) get/set operations (atomic operations)
- **Scalability**: Single reference, no scaling issues

### AtlasLoaderMixin
- **Memory**: O(n) where n = number of extra emissive sprites (typically <50)
- **CPU**: O(n) to scan and add, O(1) per-sprite validation
- **Impact**: Occurs during resource load phase (not rendering)
- **Optimization**: Already implements: skips if emissive already exists

### SpriteAtlasTextureMixin
- **Memory**: Negligible (references only, no data copying)
- **CPU**: O(1) coordinator lookup, O(n) for beforeBake() (done per-atlas, typically 1-2 atlases)
- **Impact**: Occurs during texture upload (happens once per load)

**Conclusion**: ✅ **Performance characteristics are acceptable for framework-level code**

---

## Identified API Gaps

### No Gaps Found ✅

After comprehensive analysis, no missing API capabilities were identified. The implementations:
1. Access only what's needed (no unused API calls)
2. Use stable APIs that exist in 1.21.6 and 1.21.10
3. Follow Fabric conventions exactly
4. Have appropriate performance characteristics

---

## Risk Assessment for Current Implementations

| Component | Risk | Mitigation | Status |
|-----------|------|-----------|--------|
| AtlasStorage volatility | LOW | Volatile field ensures visibility | ✅ Mitigated |
| AtlasLoaderMixin thread-locals | LOW | Well-scoped, cleared after use | ✅ Mitigated |
| SpriteAtlasTextureMixin null extension | **MEDIUM** | Returns null on initial load | ⚠️ **This is the real issue** |
| Null sprite map access | LOW | Checked before use | ✅ Mitigated |

**Finding**: The only real problem is NOT the implementations themselves—it's the architectural timing issue where `upload()` fires before the extension is ready on initial load. This requires a solution at the `CtmInitializationCoordinator` level, not at the mixin/storage level.

---

## Recommendations

### For AtlasStorage ✅
**Status**: NO CHANGES NEEDED
- Design is correct and minimal
- Volatile field is appropriate
- Pattern matches Fabric conventions
- Null-safe in practice

### For AtlasLoaderMixin ✅
**Status**: NO CHANGES NEEDED
- Pattern is standard Fabric
- Resource loading is correct
- Thread-local contexts are properly scoped
- Emissive handling is complete

### For SpriteAtlasTextureMixin ⚠️
**Status**: WORKING BUT INCOMPLETE
- Current implementation: Works for F3+T, skips initial load
- Issue: `getExtensionWhenReady()` returns null on initial load
- **Next phase**: Implement synchronization strategy in `CtmInitializationCoordinator`

---

## Conclusion

**Verdict**: ✅ **Current implementations are well-designed and follow Fabric best practices correctly.**

The issue preventing initial load CTM support is NOT an API gap or implementation deficiency—it's an architectural timing problem:
- `SpriteAtlasTexture.upload()` fires BEFORE resource reload listener executes on initial load
- No reload event is triggered during initial Minecraft startup
- Event-based reload listeners only execute when reload is manually triggered (F3+T)

This is a **coordination problem**, not an **API problem**. The solution lies in the `CtmInitializationCoordinator` implementation, which is the subject of the next architectural review document.

---

## Related Documentation

- **PHASE5_DELETED_FILES_ANALYSIS.md** - Root cause analysis
- **PHASE5_DELETED_FILES_SOLUTIONS-PART2.md** - Coordinator pattern overview
- **CRITICAL_FILES_GUIDE.md** - Implementation details
- **CtmInitializationCoordinator.java** - Central synchronization
- **NEXT**: Initial Load Synchronization Design Document
