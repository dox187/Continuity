# Phase 4 Runtime Error - Mixin Invalid Method

**Date**: November 9, 2025  
**Phase**: Phase 4 - Runtime Testing  
**Status**: ❌ Runtime crash during mod loading  
**Error Type**: `InvalidMixinException`  
**Build Status**: ✅ Compiles successfully (Phase 3 complete)  
**Runtime Status**: ❌ Crashes on launch (Phase 4 blocker)  

---

## Error Details

### Primary Error
```
[12:01:23] [main/ERROR]: Mixin apply for mod continuity failed 
continuity.mixins.json:SpriteAtlasTextureMixin from mod continuity -> net.minecraft.class_1059: 
org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException 
Mixin continuity.mixins.json:SpriteAtlasTextureMixin from mod continuity contains 
non-private static method continuity$getBlockAtlas()Lnet/minecraft/class_1059;
```

### Error Location
- **Mixin**: `SpriteAtlasTextureMixin`
- **Method**: `continuity$getBlockAtlas()`
- **Problem**: Non-private static method in mixin
- **Phase**: MAIN Applicator Phase → Apply Methods

### Stack Trace
```java
org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException: 
Mixin continuity.mixins.json:SpriteAtlasTextureMixin from mod continuity 
contains non-private static method continuity$getBlockAtlas()Lnet/minecraft/class_1059;

at org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.checkMethodVisibility(MixinApplicatorStandard.java:781)
at org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.applyNormalMethod(MixinApplicatorStandard.java:466)
at org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard.applyMethods(MixinApplicatorStandard.java:449)
...
```

---

## Root Cause Analysis

### Problem
**Mixin Rule Violation**: Mixins cannot contain public static methods.

From Mixin specification:
- Static methods in mixins **MUST** be private
- Public/protected static methods are not allowed in target classes
- This prevents namespace pollution and ensures mixin safety

### Current Code (BROKEN)
```java
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    @Unique
    private static volatile SpriteAtlasTexture continuity$blockAtlas;
    
    // ❌ ERROR: Public static method in mixin!
    public static SpriteAtlasTexture continuity$getBlockAtlas() {
        return continuity$blockAtlas;
    }
}
```

### Why This Fails
1. **Mixin applies to target class**: `SpriteAtlasTexture`
2. **Public static method would be added**: To Minecraft's class
3. **Mixin framework rejects this**: Prevents API pollution
4. **Result**: `InvalidMixinException` at runtime

---

## Impact Assessment

### What Works
- ✅ Build compiles successfully (compiler doesn't check mixin rules)
- ✅ JAR file created: `continuity-3.0.1+1.21.10.jar`
- ✅ All other code is correct

### What Fails
- ❌ Mod fails to load at runtime
- ❌ Minecraft crashes during initialization
- ❌ Game cannot start with mod installed
- ❌ `RenderUtil` cannot access block atlas

---

## Resolution Strategy

### Option 1: Use Accessor Mixin (RECOMMENDED)
Create a separate accessor interface that doesn't inject into target class.

**Pros**:
- Clean separation of concerns
- No static method in target class
- Standard mixin pattern

**Implementation**:
```java
// 1. Create accessor interface (no @Mixin)
public interface SpriteAtlasTextureAccess {
    static SpriteAtlasTexture getBlockAtlas() {
        return SpriteAtlasTextureMixin.blockAtlas;
    }
}

// 2. Update SpriteAtlasTextureMixin
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    @Unique
    static volatile SpriteAtlasTexture blockAtlas;  // package-private
    
    @Inject(method = "upload(...)V", at = @At("HEAD"))
    private void continuity$onUpload(...) {
        if (id.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)) {
            blockAtlas = (SpriteAtlasTexture) (Object) this;
        }
    }
}

// 3. Update RenderUtil
blockAtlasSpriteFinder = SpriteAtlasTextureAccess.getBlockAtlas().spriteFinder();
```

### Option 2: Use Mixin Interface Extension
Store reference in a separate extension class.

**Pros**:
- Follows existing pattern (like `StitchResultExtension`)
- Type-safe access

**Implementation**:
```java
// 1. Create storage class
public class AtlasStorage {
    private static volatile SpriteAtlasTexture blockAtlas;
    
    static void setBlockAtlas(SpriteAtlasTexture atlas) {
        blockAtlas = atlas;
    }
    
    public static SpriteAtlasTexture getBlockAtlas() {
        return blockAtlas;
    }
}

// 2. Update mixin to use storage
@Inject(method = "upload(...)V", at = @At("HEAD"))
private void continuity$onUpload(...) {
    if (id.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)) {
        AtlasStorage.setBlockAtlas((SpriteAtlasTexture) (Object) this);
    }
}

// 3. Update RenderUtil
blockAtlasSpriteFinder = AtlasStorage.getBlockAtlas().spriteFinder();
```

### Option 3: Store in RenderUtil Directly
Inject directly into `RenderUtil` from mixin.

**Pros**:
- Simplest solution
- No intermediate classes needed

**Implementation**:
```java
// Update SpriteAtlasTextureMixin
@Inject(method = "upload(...)V", at = @At("HEAD"))
private void continuity$onUpload(...) {
    if (id.equals(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)) {
        me.pepperbell.continuity.client.util.RenderUtil
            .continuity$setBlockAtlas((SpriteAtlasTexture) (Object) this);
    }
}

// Update RenderUtil (add package-private setter)
private static SpriteAtlasTexture blockAtlas;

static void continuity$setBlockAtlas(SpriteAtlasTexture atlas) {
    blockAtlas = atlas;
}

@Override
public void reload(ResourceManager manager) {
    blockAtlasSpriteFinder = blockAtlas.spriteFinder();
}
```

---

## Recommended Solution: Option 2 (Mixin Interface Extension)

**Rationale**:
1. Follows existing `StitchResultExtension` pattern
2. Clean separation from mixin class
3. Type-safe and easy to test
4. No mixin rule violations

**Files to Create**:
- `src/main/java/me/pepperbell/continuity/client/util/AtlasStorage.java`

**Files to Modify**:
- `SpriteAtlasTextureMixin.java` - Remove public static method, call storage
- `RenderUtil.java` - Use `AtlasStorage.getBlockAtlas()`

---

## Implementation Steps

1. **Create AtlasStorage class** (5 min)
   - Simple static storage with getter/setter
   - Package: `me.pepperbell.continuity.client.util`

2. **Update SpriteAtlasTextureMixin** (5 min)
   - Remove `continuity$getBlockAtlas()` method
   - Remove `continuity$blockAtlas` field
   - Call `AtlasStorage.setBlockAtlas()` in upload injection

3. **Update RenderUtil** (2 min)
   - Change from `SpriteAtlasTextureMixin.continuity$getBlockAtlas()`
   - To: `AtlasStorage.getBlockAtlas()`

4. **Test** (5 min)
   - Rebuild mod
   - Launch Minecraft
   - Verify no mixin errors
   - Verify sprite finder works

**Total Time**: ~17 minutes

---

## Success Criteria

After fix:
- [ ] Mod loads without mixin errors
- [ ] Minecraft launches successfully
- [ ] No `InvalidMixinException` in logs
- [ ] `RenderUtil.getSpriteFinder()` returns valid SpriteFinder
- [ ] CTM textures render correctly (Phase 4 testing)

---

## Related Files

**Problem Files**:
- `src/main/java/me/pepperbell/continuity/client/mixin/SpriteAtlasTextureMixin.java` (Line 49-51)
- `src/main/java/me/pepperbell/continuity/client/util/RenderUtil.java` (Line 65)

**Solution Files** (to create/modify):
- `src/main/java/me/pepperbell/continuity/client/util/AtlasStorage.java` (NEW)
- `SpriteAtlasTextureMixin.java` (MODIFY)
- `RenderUtil.java` (MODIFY)

---

## Mixin Rules Reference

From Mixin Specification:
```
Static methods in mixins:
✅ ALLOWED: private static methods (internal use only)
❌ FORBIDDEN: public/protected static methods (would pollute target class)
✅ ALLOWED: @Accessor static methods (special case)
❌ FORBIDDEN: @Unique public static methods (still adds to target)
```

**Key Insight**: Static methods in mixins become part of the target class. Public static methods would expose internal mod methods in Minecraft's API, which is forbidden.

---

**Status**: 📋 **DOCUMENTED** - Ready to implement fix  
**Next Action**: Implement Option 2 (AtlasStorage class)  
**Estimated Time**: 17 minutes
