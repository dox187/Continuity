# Continuity - Minecraft 1.21.10 Upgrade Strategy

**Status**: 🔄 In Progress  
**Java Version**: Java 21 (stable, no Java 23+ support)  
**Current Target**: Minecraft 1.21.6 → 1.21.10  
**Last Updated**: November 9, 2025

---

## 📋 Strategic Decisions

### ✅ Decision 1: Java Version
- **DECISION**: Stay on **Java 21** throughout the upgrade
- **Reason**: Java 21 is LTS (Long Term Support), stable, production-ready
- **Consequence**: No Java 23+ preview features will be used
- **Implementation**: Java 21 records already applied (ProcessingDataKeyImpl)

### ✅ Decision 2: File-by-File Analysis Required
- **DECISION**: Every Java file must be analyzed before upgrade
- **Scope**: Import statements, API calls, Mixin patterns
- **Output**: Detailed analysis document (this file + supplementary docs)
- **Benefit**: Clear migration path, no surprises during porting

---

## 🔍 Analysis Framework

Each file will be analyzed using this checklist:

```
FILE: [path/to/File.java]
├─ IMPORTS
│  ├─ Minecraft classes (versions differ)
│  ├─ Fabric API (might change)
│  └─ Other dependencies
├─ API CALLS
│  ├─ Methods that might not exist in 1.21.10
│  ├─ Deprecated patterns
│  └─ New alternatives available
├─ MIXIN PATTERNS
│  ├─ @Inject, @ModifyArg, @ModifyReturn descriptors
│  ├─ Method signatures (obfuscated names)
│  └─ Compatibility risks
├─ CHANGES NEEDED
│  ├─ What to replace
│  ├─ Why
│  └─ How
└─ RISK LEVEL: [LOW/MEDIUM/HIGH]
```

---

## 📁 Files to Analyze

### API Layer (`src/main/java/me/pepperbell/continuity/api/client/`)

- [ ] `CachingPredicates.java` - Check for mutable state patterns
- [ ] `ContinuityFeatureStates.java` - Verify state management APIs
- [ ] `CtmLoader.java` - Factory pattern stability
- [ ] `CtmLoaderRegistry.java` - Registry interfaces
- [ ] `CtmProperties.java` - Property parsing compatibility
- [ ] `EmissiveSpriteApi.java` - Sprite reference handling
- [ ] `ProcessingDataKey.java` - Already modernized ✅
- [ ] `ProcessingDataKeyRegistry.java` - Registry pattern
- [ ] `ProcessingDataProvider.java` - Data context interfaces
- [ ] `QuadProcessor.java` - Core processing API

### Implementation Layer (`src/main/java/me/pepperbell/continuity/impl/client/`)

- [ ] `ContinuityFeatureStatesImpl.java` - State implementation
- [ ] `CtmLoaderRegistryImpl.java` - Registry implementation
- [ ] `ProcessingContextImpl.java` - Core context
- [ ] `ProcessingDataKeyImpl.java` - ✅ Already converted to record
- [ ] `ProcessingDataKeyRegistryImpl.java` - Registry impl

### Client Layer - Main (`src/main/java/me/pepperbell/continuity/client/`)

- [ ] `ContinuityClient.java` - Entry point, resource pack registration

### Client Config (`src/main/java/me/pepperbell/continuity/client/config/`)

- [ ] `ContinuityConfig.java` - Configuration structure
- [ ] `ContinuityConfigScreen.java` - GUI components
- [ ] `ModMenuApiImpl.java` - ModMenu integration
- [ ] `Option.java` - Option types

### Client Mixins (`src/main/java/me/pepperbell/continuity/client/mixin/`)

- [ ] `AtlasLoaderMixin.java` - ⚠️ **HIGH PRIORITY** - Atlas changes in 1.21.10
- [ ] `BakedModelManagerMixin.java` - ⚠️ **HIGH PRIORITY** - Model baking changes
- [ ] `LifecycledResourceManagerImplMixin.java` - Resource manager changes
- [ ] `RenderLayersMixin.java` - Render layer access
- [ ] `SpriteLoaderMixin.java` - ⚠️ **HIGH PRIORITY** - Sprite loading refactor
- [ ] `SpriteMixin.java` - Sprite extension interface

### Client Mixin Interfaces (`src/main/java/me/pepperbell/continuity/client/mixinterface/`)

- [ ] `SpriteExtension.java` - Mixin interface for sprites

### Client Model (`src/main/java/me/pepperbell/continuity/client/model/`)

- [ ] `CtmBlockStateModel.java` - Block state wrapping
- [ ] `EmissiveBlockStateModel.java` - Emissive rendering
- [ ] `ModelObjectsContainer.java` - Thread-local models
- [ ] `QuadProcessors.java` - Processor coordination

### Client Processor (`src/main/java/me/pepperbell/continuity/client/processor/`)

- [ ] `AbstractQuadProcessorFactory.java` - Base processor factory
- [ ] `BaseCachingPredicates.java` - Predicate caching
- [ ] `BaseProcessingPredicate.java` - Base predicates
- [ ] `CompactCtmQuadProcessor.java` - Compact CTM algorithm
- [ ] `ProcessingDataKeys.java` - Key definitions
- [ ] `ProcessingPredicate.java` - Predicate interface
- [ ] `TopQuadProcessor.java` - Top quad handling
- [ ] Subdirectories: `simple/`, `overlay/`

### Client Properties (`src/main/java/me/pepperbell/continuity/client/properties/`)

- [ ] `BaseCtmProperties.java` - Base properties
- [ ] `ConnectingCtmProperties.java` - Connecting texture properties
- [ ] `CompactConnectingCtmProperties.java` - Compact variant
- [ ] `OrientedConnectingCtmProperties.java` - Oriented properties
- [ ] `PropertiesParsingHelper.java` - Parsing utilities
- [ ] `RandomCtmProperties.java` - Random texture properties
- [ ] `RepeatCtmProperties.java` - Repeat texture properties
- [ ] `TileAmountValidator.java` - Tile validation
- [ ] Subdirectories: `overlay/`

### Client Resource (`src/main/java/me/pepperbell/continuity/client/resource/`)

- [ ] `AtlasLoaderInitContext.java` - Atlas initialization
- [ ] `AtlasLoaderLoadContext.java` - Atlas loading context
- [ ] `BakedModelManagerBakeContext.java` - ⚠️ **HIGH PRIORITY** - Model bake context
- [ ] `BakedModelManagerReloadExtension.java` - ⚠️ **HIGH PRIORITY** - Reload extension
- [ ] `CtmPropertiesLoader.java` - CTM file loading
- [ ] `CustomBlockLayers.java` - Custom layer handling
- [ ] `EmissiveSuffixLoader.java` - Emissive texture loading
- [ ] `ModelWrappingHandler.java` - Model wrapping
- [ ] `ResourceRedirectHandler.java` - Resource redirection
- [ ] `SpriteLoaderLoadContext.java` - ⚠️ **HIGH PRIORITY** - Sprite load context
- [ ] `SpriteLoaderStitchContext.java` - Sprite stitching context

### Client Utilities (`src/main/java/me/pepperbell/continuity/client/util/`)

- [ ] `DirectionUtil.java` - Direction utilities
- [ ] `MathUtil.java` - Math operations
- [ ] `QuadUtil.java` - Quad processing utilities
- [ ] `RenderUtil.java` - Rendering utilities
- [ ] `TextureUtil.java` - Texture utilities
- [ ] Subdirectories: `biome/`

---

## ⚠️ High-Priority Files (1.21.10 Breaking Changes)

These files are known to have changes between 1.21.6 and 1.21.10:

1. **`BakedModelManagerBakeContext.java`** & **`BakedModelManagerReloadExtension.java`**
   - Issue: `SpriteAtlasManager` class removed/refactored in 1.21.10
   - Status: Requires deep API research
   - Impact: Model baking pipeline

2. **`SpriteLoaderMixin.java`** & **`SpriteLoaderLoadContext.java`**
   - Issue: `SpriteLoader.StitchResult.regions()` method signature changed
   - Status: Requires remapping verification
   - Impact: Sprite atlas stitching

3. **`AtlasLoaderMixin.java`**
   - Issue: Atlas loading pipeline restructured
   - Status: Mixin method descriptors might not match
   - Impact: Texture atlas loading

---

## 📊 Analysis Progress

### Tier 1: Low Risk (No changes expected)
- Configuration classes
- Utility classes
- Property parsing
- Data holders

### Tier 2: Medium Risk (API usage review needed)
- Processor implementations
- Predicate classes
- Model wrapping

### Tier 3: High Risk (Known breaking changes)
- `*Context.java` files with `SpriteAtlasManager`
- Sprite loading mixins
- Model baking mixins

---

## 🎯 Upgrade Phases

### Phase 1: Analysis (Current)
- ✅ Framework created (this file)
- ⏳ Individual file analysis pending
- ⏳ Risk assessment per file

### Phase 2: Mapping Changes (Next)
- Yarn mappings: 1.21.6+build.1 → 1.21.10+build.1
- Method descriptor updates
- Import statement changes

### Phase 3: Implementation (After Phase 2)
- Apply mapped changes
- Test mixin injections
- Verify CTM functionality

### Phase 4: Testing (Final)
- Full build verification
- Runtime testing with client
- Manual CTM verification

---

## 📝 Notes for Future Work

### Key Research Areas
- [ ] Sprite atlas architecture in 1.21.10 vs 1.21.6
- [ ] Model baking pipeline changes
- [ ] Resource manager modifications
- [ ] Fabric API changes in 0.128.2 → newer versions

### Known Issues (from initial research)
- `SpriteAtlasManager` doesn't exist in 1.21.10 yarn mappings
- `TextureAtlasData` class location/accessibility changes
- `BakedModelManager.getAtlas()` method signature different

### Questions to Answer
- [ ] What replaces `SpriteAtlasManager` in the new API?
- [ ] How does sprite stitching work in 1.21.10?
- [ ] Are the mixin injection points still valid?

---

## 📚 Reference Documents

- `.github/changelog/3.0.2_java21_modernization.md` - Java 21 changes already applied
- `.github/copilot-instructions.md` - Architecture documentation
- `.lib_src/fabric-1.21.10/` - Fabric API source code
- `.lib_src/yarn-1.21.10/` - Yarn mappings reference

---

## 🔗 How to Use This Document

1. **At Project Open**: Read "Strategic Decisions" section
2. **During Analysis**: Use "Analysis Framework" checklist for each file
3. **Before Coding**: Check "High-Priority Files" section
4. **When Stuck**: Reference "Known Issues" and "Research Areas"

---

**Next Step**: Begin Phase 1 Analysis - Create detailed file-by-file analysis report.
