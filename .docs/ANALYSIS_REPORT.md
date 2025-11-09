# Minecraft 1.21.10 Upgrade - File-by-File Analysis Report

**Generated**: November 9, 2025  
**Total Files**: 93 Java files  
**Analysis Status**: Phase 1 - In Progress

---

## 📋 Quick Summary by Category

| Category | Files | Priority | Status |
|----------|-------|----------|--------|
| API Layer | 10 | MEDIUM | ⏳ Pending |
| Implementation | 5 | LOW | ⏳ Pending |
| Config & UI | 4 | LOW | ⏳ Pending |
| Mixins | 6 | **HIGH** | 🔴 Critical |
| Model & Rendering | 4 | MEDIUM | ⏳ Pending |
| Processors | 25+ | MEDIUM | ⏳ Pending |
| Properties | 10+ | MEDIUM | ⏳ Pending |
| Resources | 11 | **HIGH** | 🔴 Critical |
| Utilities | 13+ | LOW | ⏳ Pending |

---

## 🔴 CRITICAL ISSUES (Must Fix First)

### Issue #1: `SpriteAtlasManager` Removal
**Severity**: 🔴 CRITICAL  
**Discovery Point**: Hidden until dependencies updated to 1.21.10  
**Files Affected**:
- `BakedModelManagerBakeContext.java` - API interface (MUST DELETE)
- `BakedModelManagerReloadExtension.java` - Implementation (MUST DELETE)
- `BakedModelManagerMixin.java` - Mixin (MUST SIMPLIFY)

**Current Code**:
```java
import net.minecraft.client.render.model.SpriteAtlasManager;  // ❌ REMOVED
public void beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations)
```

**Problem**: 
- `SpriteAtlasManager` class **no longer exists** in Minecraft 1.21.10
- `AtlasPreparation` nested class **no longer exists**
- Entire class hierarchy removed due to API redesign

**Impact**: 
- ❌ Compilation fails when dependencies updated
- ❌ Three files cannot be used with 1.21.10

**Solution Strategy**:
- ✅ Delete both context files (interface + implementation)
- ✅ Simplify BakedModelManagerMixin to placeholder
- ✅ Replace functionality with new `SpriteAtlasTexture.upload()` injection point

**Related Tasks**:
- [ ] Delete `BakedModelManagerBakeContext.java`
- [ ] Delete `BakedModelManagerReloadExtension.java`
- [ ] Simplify `BakedModelManagerMixin.java` to minimal implementation

**Status**: ⏳ **Awaiting Phase 3 Implementation**

---

### Issue #2: `SpriteLoader.StitchResult` API Change - Hidden Method Rename
**Severity**: 🔴 CRITICAL  
**Discovery Point**: Hidden until dependencies updated to 1.21.10  
**Files Affected**:
- `SpriteLoaderMixin.java` Line 119 - Mixin implementation

**Current Code**:
```java
Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();  // ❌ METHOD NOT FOUND
```

**Problem**: 
- `StitchResult.regions()` method **does not exist** in Minecraft 1.21.10
- Record component renamed: `regions` → `sprites`
- Method name changed accordingly

**Impact**: 
- ❌ Compilation fails: "cannot find symbol: method regions()"
- ❌ Emissive sprite attachment cannot work

**Solution Strategy**:
- [ ] Create `StitchResultExtension` mixin interface
- [ ] Create `StitchResultMixin` with `@Shadow private Map sprites`
- [ ] Update line 119 to use interface pattern: `((StitchResultExtension) (Object) cir.getReturnValue()).continuity$getSprites()`
- [ ] Register mixin in `continuity.mixins.json`

**Verification Checklist**:
- [ ] Verify correct method name in Yarn 1.21.10 mappings (likely `sprites()`)
- [ ] Confirm record structure change
- [ ] Test that emissive sprites attach correctly

**Status**: ⏳ **Awaiting Phase 3 Implementation**

---

### Issue #3: `BakedModelManager.getAtlas()` Removal - Critical for Sprite Finder
**Severity**: 🔴 CRITICAL  
**Discovery Point**: Hidden until dependencies updated to 1.21.10  
**Files Affected**:
- `RenderUtil.java` Line 65 - Utility initialization

**Current Code**:
```java
blockAtlasSpriteFinder = MODEL_MANAGER
    .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)  // ❌ METHOD REMOVED
    .spriteFinder();
```

**Problem**: 
- Method `BakedModelManager.getAtlas(Identifier)` **no longer exists** in 1.21.10
- Part of removed `SpriteAtlasManager` API
- No direct replacement provided by Minecraft

**Impact**: 
- ❌ Compilation fails: "cannot find symbol: method getAtlas(Identifier)"
- ❌ Sprite finder cannot be created
- ❌ CTM quad processing cannot work without sprite lookup

**Solution Strategy**:
- [ ] Create new `SpriteAtlasTextureMixin` (mixin layer) to capture block atlas at upload
- [ ] Create `AtlasStorage` utility class (util layer) to hold reference
- [ ] Update line 65 to: `blockAtlasSpriteFinder = AtlasStorage.getBlockAtlas().spriteFinder()`

**Implementation Dependency Chain**:
```
1. SpriteAtlasTextureMixin created
   ├─ Injects into upload() method
   ├─ Stores atlas in AtlasStorage
   └─ Only if ID == BLOCK_ATLAS_TEXTURE
    ↓
2. AtlasStorage utility created
   ├─ Static field: private static volatile SpriteAtlasTexture blockAtlas
   ├─ Setter: setBlockAtlas(SpriteAtlasTexture)
   └─ Getter: SpriteAtlasTexture getBlockAtlas()
    ↓
3. RenderUtil updated
   └─ Uses AtlasStorage.getBlockAtlas() instead of MODEL_MANAGER.getAtlas()
```

**Critical Mixin Rule**:
- ⚠️ **MUST**: Static methods in mixins are PRIVATE only
- ❌ **FORBIDDEN**: Public static methods pollute target class namespace
- ✅ **USE**: External utility class (AtlasStorage) to avoid this violation

**Status**: ⏳ **Awaiting Phase 3 Implementation**

---

### Issue #4: Mixin Static Method Visibility Rule (Runtime Error)
**Severity**: 🔴 CRITICAL (Runtime Blocker)  
**Discovery Point**: Runtime error during Minecraft launch (Phase 4)  
**Problem Context**: 
When creating `SpriteAtlasTextureMixin` to fix Issue #3, mixin framework enforces static method visibility rules.

**The Problem**:
```java
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    public static SpriteAtlasTexture continuity$getBlockAtlas() {  // ❌ ERROR
        return continuity$blockAtlas;
    }
}
```

**Error**:
```
InvalidMixinException: Mixin contains non-private static method continuity$getBlockAtlas()
```

**Why**:
- Static methods in mixins become part of the **target class** (SpriteAtlasTexture)
- Public static methods would expose internal mod methods in Minecraft's public API
- This violates mixin contract: mixins should not pollute target class

**Solution**:
- ❌ **DO NOT** make static methods public in mixins
- ✅ **USE** external utility class (AtlasStorage) instead
- ✅ **PATTERN**: Keep mixin methods private, expose via external utility

**Prevention**:
- [ ] Never create public static methods in mixin classes
- [ ] Use external utility classes for shared state
- [ ] Document this rule in MIXIN_RULES_AND_GOTCHAS.md

**Status**: ⏳ **Awaiting AtlasStorage pattern implementation**

---

## 📂 Detailed File Analysis

### API Layer - `api/client/`

#### `CachingPredicates.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Interface definition, no API dependencies
Action: Verify at compile time
```

#### `ContinuityFeatureStates.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Feature state enumeration
Action: No changes needed
```

#### `CtmLoader.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Factory pattern interface
Action: No changes needed
```

#### `CtmLoaderRegistry.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Registry interface
Action: No changes needed
```

#### `CtmProperties.java`
```
Risk Level: MEDIUM
Changes Needed: Check for deprecated patterns
Issues: Might use old parsing utilities
Action: Review property parsing compatibility
```

#### `EmissiveSpriteApi.java`
```
Risk Level: MEDIUM
Changes Needed: Sprite reference might need update
Issues: Depends on Sprite class API
Action: Verify Sprite accessor methods exist
```

#### `ProcessingDataKey.java`
```
Risk Level: LOW
Changes Needed: None - interface only
Status: ✅ Already verified in Java 21 conversion
```

#### `ProcessingDataKeyRegistry.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Registry interface
Action: No changes needed
```

#### `ProcessingDataProvider.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Context provider interface
Action: No changes needed
```

#### `QuadProcessor.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Core processing interface
Action: No changes needed
```

---

### Implementation Layer - `impl/client/`

#### `ContinuityFeatureStatesImpl.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Simple state holder
Action: No changes needed
```

#### `CtmLoaderRegistryImpl.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Simple registry implementation
Action: No changes needed
```

#### `ProcessingContextImpl.java`
```
Risk Level: MEDIUM
Changes Needed: Check quad processing compatibility
Issues: Uses Fabric Renderer API v1
Action: Verify quad emitter API unchanged
```

#### `ProcessingDataKeyImpl.java`
```
Risk Level: LOW
Status: ✅ DONE - Converted to Java 21 record
Changes Applied: Modernized with record syntax
```

#### `ProcessingDataKeyRegistryImpl.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Simple registry implementation
Action: No changes needed
```

---

### Config Layer - `config/`

#### `ContinuityConfig.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Configuration data holder
Action: No changes needed
```

#### `ContinuityConfigScreen.java`
```
Risk Level: MEDIUM
Changes Needed: Verify Screen class compatibility
Issues: Extends Minecraft Screen class
Action: Check GUI event handling changes
```

#### `ModMenuApiImpl.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: ModMenu API integration
Action: Depends on ModMenu version compatibility
```

#### `Option.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Configuration option type
Action: No changes needed
```

---

### Mixin Layer - `mixin/` ⚠️ CRITICAL

#### `AtlasLoaderMixin.java`
```
Risk Level: 🔴 HIGH
Changes Needed: Method descriptors update
Issues: 
  - AtlasSource might have changed
  - Method injection points might not match
  - Atlas loading pipeline refactored
Action: Regenerate with new Yarn mappings
```

#### `BakedModelManagerMixin.java`
```
Risk Level: 🔴 HIGH
Changes Needed: CRITICAL - SpriteAtlasManager usage
Issues:
  - SpriteAtlasManager.AtlasPreparation doesn't exist
  - reload() method signature might differ
  - New atlas handling pattern needed
Action: Rewrite with new API
```

#### `LifecycledResourceManagerImplMixin.java`
```
Risk Level: MEDIUM
Changes Needed: Verify resource manager APIs
Issues: Resource loading might have changed
Action: Test at runtime, check new methods
```

#### `RenderLayersMixin.java`
```
Risk Level: LOW
Changes Needed: Likely compatible
Issues: RenderLayers interface usually stable
Action: Verify at compile time
```

#### `SpriteLoaderMixin.java`
```
Risk Level: 🔴 HIGH
Changes Needed: CRITICAL - API changes
Issues:
  - StitchResult.regions() method changed
  - Sprite loading descriptor might not match
  - Thread-local context handling different
Action: Update method injection points
```

#### `SpriteMixin.java`
```
Risk Level: HIGH
Changes Needed: Verify Sprite class extensions
Issues: Adding emissive field to Sprite might break
Action: Test mixin compatibility
```

---

### Mixin Interfaces - `mixinterface/`

#### `SpriteExtension.java`
```
Risk Level: HIGH
Changes Needed: Verify mixin interface still works
Issues: Sprite class might be sealed/final in new version
Action: Check Sprite class modifiers
```

---

### Model Layer - `model/`

#### `CtmBlockStateModel.java`
```
Risk Level: MEDIUM
Changes Needed: Verify BlockStateModel interface
Issues: Model rendering API changes possible
Action: Check model builder compatibility
```

#### `EmissiveBlockStateModel.java`
```
Risk Level: MEDIUM
Changes Needed: Same as CtmBlockStateModel
Issues: Depends on rendering API
Action: Verify at runtime
```

#### `ModelObjectsContainer.java`
```
Risk Level: LOW
Changes Needed: None expected
Notes: Thread-local container
Action: No changes needed
```

#### `QuadProcessors.java`
```
Risk Level: MEDIUM
Changes Needed: Verify quad emitter API
Issues: Depends on Fabric Renderer v1
Action: Check mutable quad interface
```

---

### Processor Layer - `processor/` (25+ files)

#### Core Processors:
```
Files: AbstractQuadProcessorFactory, BaseCachingPredicates, 
       BaseProcessingPredicate, CompactCtmQuadProcessor, 
       ProcessingDataKeys, ProcessingPredicate, TopQuadProcessor

Risk Level: MEDIUM
Changes Needed: Mostly internal - verify BlockRenderView API
Issues: Block access patterns might change
Action: Compile check - likely compatible

Status: ⏳ Detailed analysis needed per file
```

#### Simple Processors (subdirectory):
```
Files: CtmSpriteProvider, FixedSpriteProvider, 
       HorizontalSpriteProvider, RandomSpriteProvider, etc.

Risk Level: LOW-MEDIUM
Changes Needed: Verify Sprite/texture APIs
Issues: Sprite accessor methods
Action: Compile check needed
```

#### Overlay Processors (subdirectory):
```
Similar to simple processors
Risk Level: MEDIUM
Action: Same as simple processors
```

---

### Properties Layer - `properties/` (10+ files)

#### Base Properties:
```
Files: BaseCtmProperties, ConnectingCtmProperties,
       CompactConnectingCtmProperties, OrientedConnectingCtmProperties,
       RandomCtmProperties, RepeatCtmProperties

Risk Level: LOW-MEDIUM
Changes Needed: Verify Properties/Identifier APIs
Issues: Property parsing should be stable
Action: Compile check needed
```

#### Property Validators:
```
Files: TileAmountValidator

Risk Level: LOW
Changes Needed: None expected
Action: No changes needed
```

#### Overlay Properties:
```
Similar to base properties
Risk Level: MEDIUM
Action: Compile check needed
```

---

### Resource Layer - `resource/` ⚠️ CRITICAL

#### `AtlasLoaderInitContext.java`
```
Risk Level: MEDIUM
Changes Needed: Verify context pattern
Issues: Atlas loading architecture might differ
Action: Verify usage in mixins
```

#### `AtlasLoaderLoadContext.java`
```
Risk Level: HIGH
Changes Needed: Verify context interface
Issues: Depends on new atlas API
Action: Update based on AtlasLoaderMixin changes
```

#### `BakedModelManagerBakeContext.java`
```
Risk Level: 🔴 CRITICAL
Status: BROKEN - SpriteAtlasManager usage
Changes Needed: Complete redesign
Issues: API doesn't exist in 1.21.10
Action: Research new baking API
```

#### `BakedModelManagerReloadExtension.java`
```
Risk Level: 🔴 CRITICAL
Status: BROKEN - depends on BakedModelManagerBakeContext
Changes Needed: Complete redesign
Issues: Uses SpriteAtlasManager.AtlasPreparation
Action: Research new atlas preparation API
```

#### `CtmPropertiesLoader.java`
```
Risk Level: MEDIUM
Changes Needed: Verify resource pack APIs
Issues: ResourcePack iteration might change
Action: Compile check needed
```

#### `CustomBlockLayers.java`
```
Risk Level: MEDIUM
Changes Needed: Verify property file loading
Issues: Resource loading pattern changes possible
Action: Compile check needed
```

#### `EmissiveSuffixLoader.java`
```
Risk Level: MEDIUM
Changes Needed: Same as CustomBlockLayers
Issues: Resource loading
Action: Compile check needed
```

#### `ModelWrappingHandler.java`
```
Risk Level: MEDIUM
Changes Needed: Verify model wrapping pattern
Issues: Block state model interface might change
Action: Compile check needed
```

#### `ResourceRedirectHandler.java`
```
Risk Level: LOW-MEDIUM
Changes Needed: Verify resource identifier pattern
Issues: Path-based resource handling
Action: Compile check needed
```

#### `SpriteLoaderLoadContext.java`
```
Risk Level: HIGH
Changes Needed: Verify context interface
Issues: Depends on sprite loading changes
Action: Update based on SpriteLoaderMixin
```

#### `SpriteLoaderStitchContext.java`
```
Risk Level: HIGH
Changes Needed: Verify stitching API
Issues: Sprite stitching might have changed
Action: Update based on new API research
```

---

### Utility Layer - `util/` (13+ files)

#### `DirectionUtil.java` / `MathUtil.java` / `QuadUtil.java` / etc.
```
Risk Level: LOW
Changes Needed: None expected
Issues: Utility functions are stable
Action: No changes needed
```

#### `RenderUtil.java`
```
Risk Level: MEDIUM
Changes Needed: Verify sprite finder compatibility
Issues: SpriteFinder API from Fabric Renderer
Action: Compile check needed
```

#### Biome Utilities:
```
Files: BiomeHolderManager, etc.
Risk Level: LOW-MEDIUM
Changes Needed: Verify biome registry APIs
Issues: Biome lookup might have optimizations
Action: Compile check needed
```

---

## 📊 Summary Statistics

| Risk Level | Files | Status |
|-----------|-------|--------|
| 🔴 CRITICAL | 6 | Must fix first |
| HIGH | 12 | High priority |
| MEDIUM | 45 | Normal priority |
| LOW | 30 | Low priority |

---

## 🎯 Recommended Analysis Order

### Round 1: CRITICAL (6 files - must complete first)
1. `BakedModelManagerBakeContext.java` - Research new API
2. `BakedModelManagerReloadExtension.java` - Depends on #1
3. `SpriteLoaderMixin.java` - Research new sprite API
4. `SpriteLoaderLoadContext.java` - Depends on #3
5. `BakedModelManagerMixin.java` - Depends on #1
6. `AtlasLoaderMixin.java` - Depends on research

### Round 2: HIGH Priority (12 files)
- Test all mixin compatibility
- Verify sprite extension patterns
- Check model building APIs

### Round 3: MEDIUM Priority (45 files)
- Compile checks
- API compatibility verification
- Test in client

### Round 4: LOW Priority (30 files)
- Utilities typically don't change
- Verify at end

---

## 🔬 Research Todo

Before starting implementation:

- [ ] Check `.lib_src/fabric-1.21.10/` for new atlas/sprite APIs
- [ ] Read Fabric API changelog for 1.21.6 → 1.21.10
- [ ] Search for `SpriteAtlasManager` replacement in new codebase
- [ ] Verify `StitchResult` API in new yarn mappings
- [ ] Document all descriptor changes needed in mixins

---

## 📝 Notes

- This analysis will be updated as research progresses
- Each file section will be expanded with specific code changes
- Testing phase will verify all assumptions

**Next Step**: Begin research on CRITICAL files, especially new sprite/atlas API in 1.21.10
