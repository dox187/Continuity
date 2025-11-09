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
**Files Affected**:
- `BakedModelManagerBakeContext.java` - API interface
- `BakedModelManagerReloadExtension.java` - Implementation
- `BakedModelManagerMixin.java` - Mixin

**Current Code**:
```java
import net.minecraft.client.render.model.SpriteAtlasManager;
public void beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations)
```

**Problem**: `SpriteAtlasManager` doesn't exist in Minecraft 1.21.10

**Solution**: Requires research into new Fabric API or Minecraft 1.21.10 atlas handling

**Status**: 🔍 Research needed

---

### Issue #2: `SpriteLoader.StitchResult` API Change
**Severity**: 🔴 CRITICAL  
**Files Affected**:
- `SpriteLoaderMixin.java` - Mixin implementation
- `SpriteLoaderLoadContext.java` - Context interface

**Current Code**:
```java
Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();
```

**Problem**: `.regions()` method might not exist or signature changed

**Solution**: Requires Yarn mapping verification and method descriptor update

**Status**: 🔍 Research needed

---

### Issue #3: Mixin Method Descriptors
**Severity**: 🔴 CRITICAL  
**Files Affected**: All files in `client/mixin/` directory

**Problem**: Minecraft 1.21.10 uses different obfuscated names, mixin descriptors must match

**Solution**: Regenerate descriptor signatures with Yarn 1.21.10 mappings

**Status**: 🔍 Waiting for mapping update

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
