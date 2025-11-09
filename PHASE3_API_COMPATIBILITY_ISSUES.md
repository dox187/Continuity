# Phase 3 API Compatibility Issues

**Date**: November 9, 2025  
**Status**: 2 compilation errors discovered after updating to Minecraft 1.21.10 dependencies  
**Build Result**: ❌ FAILED (2 errors)  

---

## Context

After updating `gradle.properties` and `fabric.mod.json` to use Minecraft 1.21.10 and Fabric API 0.138.0, the build now correctly uses the 1.21.10 APIs and revealed 2 API compatibility issues that were hidden when building against 1.21.6 dependencies.

### Dependency Changes Made

**gradle.properties**:
```properties
# OLD (1.21.6)
minecraft_version = 1.21.6
yarn_mappings = 1.21.6+build.1
mod_minecraft_version = 1.21.6
fabric_version = 0.128.2+1.21.6

# NEW (1.21.10)
minecraft_version = 1.21.10
yarn_mappings = 1.21.10+build.1
mod_minecraft_version = 1.21.10
fabric_version = 0.138.0+1.21.10
```

**fabric.mod.json**:
```json
// OLD
"depends": {
  "minecraft": ">=1.21.6 <=1.21.8",
  "fabric-api": ">=0.127.0"
}

// NEW
"depends": {
  "minecraft": ">=1.21.10 <=1.21.12",
  "fabric-api": ">=0.138.0"
}
```

---

## Compilation Errors

### Error 1: SpriteLoaderMixin.java:99

**File**: `src/main/java/me/pepperbell/continuity/client/mixin/SpriteLoaderMixin.java`  
**Line**: 99  
**Method**: `continuity$onReturnStitch()`

**Error Message**:
```
error: cannot find symbol
    Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();
                                                          ^
  symbol:   method regions()
  location: class StitchResult
```

**Current Code** (BROKEN):
```java
@Inject(method = "stitch(Ljava/util/List;ILjava/util/concurrent/Executor;)Lnet/minecraft/client/texture/SpriteLoader$StitchResult;", 
        at = @At("RETURN"))
private void continuity$onReturnStitch(List<SpriteContents> spriteContentsList, int mipmapLevels, 
                                       Executor executor, CallbackInfoReturnable<SpriteLoader.StitchResult> cir) {
    SpriteLoaderStitchContext context = SpriteLoaderStitchContext.THREAD_LOCAL.get();
    if (context != null) {
        Map<Identifier, Identifier> emissiveIdMap = context.getEmissiveIdMap();
        Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();  // ❌ ERROR HERE
        emissiveIdMap.forEach((id, emissiveId) -> {
            // ... emissive sprite attachment logic
        });
    }
}
```

**Analysis**:
- `SpriteLoader.StitchResult` is a record in Minecraft 1.21.10
- The method name changed from `regions()` to something else between 1.21.6 and 1.21.10
- Need to check Yarn 1.21.10 mappings to find correct method name
- Likely candidates: `sprites()`, `getSpriteMap()`, `getSprites()`, or similar

**Impact**: 
- **CRITICAL** - This affects emissive texture functionality
- Emissive sprites cannot be attached to base sprites without this mapping

---

### Error 2: RenderUtil.java:65

**File**: `src/main/java/me/pepperbell/continuity/client/util/RenderUtil.java`  
**Line**: 65  
**Method**: `getBlockAtlasSpriteFinder()` (static initializer)

**Error Message**:
```
error: cannot find symbol
    blockAtlasSpriteFinder = MODEL_MANAGER.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).spriteFinder();
                                          ^
  symbol:   method getAtlas(Identifier)
  location: variable MODEL_MANAGER of type BakedModelManager
```

**Current Code** (BROKEN):
```java
private static SpriteFinder blockAtlasSpriteFinder;

static {
    // ...
    blockAtlasSpriteFinder = MODEL_MANAGER.getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).spriteFinder();  // ❌ ERROR
}
```

**Analysis**:
- `BakedModelManager.getAtlas(Identifier)` method was removed in Minecraft 1.21.10
- This is consistent with removal of `SpriteAtlasManager` API
- Need to find alternative way to access `SpriteAtlasTexture` instances
- Likely candidates:
  - Direct field access via mixin
  - Alternative getter method
  - Access via `MinecraftClient` or `TextureManager`

**Impact**: 
- **MEDIUM** - This affects runtime sprite lookup for CTM processing
- SpriteFinder is used to locate sprites by UV coordinates during rendering

---

## Resolution Plan

### Priority 1: Fix Error 1 (SpriteLoaderMixin)

**Steps**:
1. Check Yarn 1.21.10 mappings for `SpriteLoader.StitchResult` record components
2. Identify correct method name (likely `sprites()` or similar)
3. Update line 99 in `SpriteLoaderMixin.java`
4. Verify emissive sprite attachment still works

**Expected Change**:
```java
// OLD (1.21.6)
Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();

// NEW (1.21.10) - likely one of these:
Map<Identifier, Sprite> sprites = cir.getReturnValue().sprites();
// OR
Map<Identifier, Sprite> sprites = cir.getReturnValue().spriteMap();
```

---

### Priority 2: Fix Error 2 (RenderUtil)

**Steps**:
1. Research how to access `SpriteAtlasTexture` in Minecraft 1.21.10
2. Check if `TextureManager` has accessor methods
3. Consider creating mixin to access atlas textures if needed
4. Update `RenderUtil.java` with new approach

**Possible Solutions**:

**Option A**: Access via TextureManager
```java
// Pseudo-code
TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
SpriteAtlasTexture atlas = (SpriteAtlasTexture) textureManager.getTexture(BLOCK_ATLAS_TEXTURE);
blockAtlasSpriteFinder = atlas.spriteFinder();
```

**Option B**: Create mixin accessor
```java
@Mixin(BakedModelManager.class)
interface BakedModelManagerAccessor {
    @Accessor
    Map<Identifier, SpriteAtlasTexture> getAtlases();
}
```

**Option C**: Store reference in SpriteAtlasTextureMixin
- Capture atlas reference in our new `SpriteAtlasTextureMixin`
- Provide static accessor method
- Most aligned with our new architecture

---

## Impact Assessment

### Before Fix
- **Build Status**: ❌ FAILED
- **Functionality**: Cannot compile
- **JAR Output**: None (build fails)

### After Fix (Expected)
- **Build Status**: ✅ SUCCESS (anticipated)
- **Functionality**: All features should work (pending runtime testing)
- **JAR Output**: `continuity-3.0.1+1.21.10.jar`

### Risk Level
- **Error 1**: LOW risk - Simple method rename, straightforward fix
- **Error 2**: MEDIUM risk - Requires architectural consideration, may need new mixin

---

## Files to Modify

1. ✅ **gradle.properties** - DONE (updated to 1.21.10)
2. ✅ **fabric.mod.json** - DONE (updated dependencies)
3. ⏳ **SpriteLoaderMixin.java** - TO FIX (line 99, method name change)
4. ⏳ **RenderUtil.java** - TO FIX (line 65, atlas access method)
5. ❓ **Possible new mixin** - TO CREATE (if needed for RenderUtil fix)

---

## Next Steps

1. **Research APIs** (15 min)
   - Check `.lib_src/` for 1.21.10 Yarn mappings
   - Find correct `StitchResult` method name
   - Find correct atlas access method

2. **Fix SpriteLoaderMixin** (5 min)
   - Update method call on line 99
   - Verify compilation

3. **Fix RenderUtil** (20 min)
   - Implement chosen solution
   - Test compilation
   - Verify sprite finder works

4. **Build & Test** (10 min)
   - Run `.\gradlew clean build`
   - Verify BUILD SUCCESSFUL
   - Check JAR filename is `continuity-3.0.1+1.21.10.jar`

**Total Estimated Time**: 50 minutes

---

## Success Criteria

- [ ] `SpriteLoaderMixin.java` compiles without errors
- [ ] `RenderUtil.java` compiles without errors
- [ ] Build completes successfully: `BUILD SUCCESSFUL`
- [ ] JAR file named correctly: `continuity-3.0.1+1.21.10.jar`
- [ ] No new compilation errors introduced
- [ ] All existing tests pass (if any)

---

**Status**: 📋 **DOCUMENTED** - Ready to begin fixes  
**Next Action**: Research correct API methods in Yarn 1.21.10 mappings
