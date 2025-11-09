# Critical Files - Phase 3 Implementation Tasks

**Updated**: November 9, 2025  
**Status**: ⏳ **PHASE 3 IN PROGRESS - ACTIONABLE TASKS**  
**Strategy**: NEW approach (SpriteAtlasTexture.upload()) per PHASE2B_IMPLEMENTATION_STRATEGY.md

---

## 📌 IMPORTANT UPDATE

This guide has been updated to reflect Phase 3 learnings. The **OLD approach** (BakedModelManager.bake with removed SpriteAtlasManager) is being replaced by the **NEW strategy** using SpriteAtlasTexture.upload().

**Complexity Improvement**: 37% simpler architecture  
**Files Affected**: 6 critical files identified below as actionable tasks

---

## TASK 1: Delete BakedModelManagerBakeContext.java

**File**: `src/main/java/me/pepperbell/continuity/client/resource/BakedModelManagerBakeContext.java`  
**Action**: ⏳ **DELETE**  
**Priority**: 🔴 CRITICAL  

**Why**:
- Uses removed `SpriteAtlasManager` class (does not exist in 1.21.10)
- Uses removed nested class `SpriteAtlasManager.AtlasPreparation`
- Entire interface obsolete with new injection strategy
- Only referenced by `BakedModelManagerReloadExtension.java` (also marked for deletion)

**Checklist**:
- [ ] Search codebase for any references to `BakedModelManagerBakeContext`
- [ ] Verify only `BakedModelManagerReloadExtension` references it
- [ ] Delete the file
- [ ] Run `.\gradlew clean build` to verify no compilation errors

**Completion Criteria**:
- [ ] File deleted
- [ ] No compilation errors
- [ ] Build successful

---

## TASK 2: Delete BakedModelManagerReloadExtension.java

**File**: `src/main/java/me/pepperbell/continuity/client/resource/BakedModelManagerReloadExtension.java`  
**Action**: ⏳ **DELETE**  
**Priority**: 🔴 CRITICAL  
**Dependency**: Depends on Task 1 (BakedModelManagerBakeContext)

**Why**:
- Implements removed interface `BakedModelManagerBakeContext` (Task 1)
- Uses removed class `SpriteAtlasManager.AtlasPreparation`
- Calls non-existent method `getSprite()` on removed class
- All functionality replaced by new `SpriteAtlasTextureMixin`

**Code Issues**:
```java
public class BakedModelManagerReloadExtension implements BakedModelManagerBakeContext {  // ❌ INTERFACE REMOVED
    public void beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations) {  // ❌ CLASS REMOVED
        Sprite sprite = preparation.getSprite(spriteId.getTextureId());  // ❌ METHOD REMOVED
    }
}
```

**Checklist**:
- [ ] Verify Task 1 is complete (BakedModelManagerBakeContext deleted)
- [ ] Search codebase for references to `BakedModelManagerReloadExtension`
- [ ] Verify only `BakedModelManagerMixin` references it
- [ ] Note any usage patterns before deleting (for simplification of BakedModelManagerMixin)
- [ ] Delete the file
- [ ] Run build to verify

**Completion Criteria**:
- [ ] File deleted
- [ ] No compilation errors
- [ ] Build successful
- [ ] BakedModelManagerMixin simplified or converted to placeholder

---

## TASK 3: Fix SpriteLoaderMixin - Hidden API Change

**File**: `src/main/java/me/pepperbell/continuity/client/mixin/SpriteLoaderMixin.java`  
**Problem Line**: Line 119  
**Action**: ⏳ **CREATE SUPPORT FILES + UPDATE**  
**Priority**: 🔴 CRITICAL  

**The Problem**:
```java
// Line 119 - BROKEN IN 1.21.10
Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();  // ❌ METHOD NOT FOUND
```

**Why It's Broken**:
- Method `SpriteLoader.StitchResult.regions()` does not exist in Minecraft 1.21.10
- Record component renamed: `regions` → `sprites`
- **Hidden issue**: Compiles against old dependencies, breaks with 1.21.10

**Solution Pattern** (using existing SpriteMixin as template):

**Step 3A: Create StitchResultExtension.java**
```java
package me.pepperbell.continuity.client.mixin;

public interface StitchResultExtension {
    Map<Identifier, Sprite> continuity$getSprites();
}
```

**Step 3B: Create StitchResultMixin.java**
```java
package me.pepperbell.continuity.client.mixin;

@Mixin(SpriteLoader.StitchResult.class)
abstract class StitchResultMixin implements StitchResultExtension {
    @Shadow
    @Final
    private Map<Identifier, Sprite> sprites;
    
    @Override
    public Map<Identifier, Sprite> continuity$getSprites() {
        return this.sprites;
    }
}
```

**Step 3C: Register in continuity.mixins.json**
```json
{
  "client": [
    "... existing mixins ...",
    "StitchResultMixin"
  ]
}
```

**Step 3D: Update SpriteLoaderMixin.java Line 119**
```java
// OLD (BROKEN)
Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();

// NEW (FIXED)
Map<Identifier, Sprite> sprites = 
    ((StitchResultExtension) (Object) cir.getReturnValue()).continuity$getSprites();
```

**Checklist**:
- [ ] Create `StitchResultExtension.java` (interface)
- [ ] Create `StitchResultMixin.java` (mixin implementation)
- [ ] Add to `continuity.mixins.json`
- [ ] Update `SpriteLoaderMixin.java` line 119
- [ ] Run build test
- [ ] Verify emissive sprite functionality works

**Completion Criteria**:
- [ ] No compilation errors
- [ ] Build successful
- [ ] Emissive texture attachment still works

---

## TASK 4: Simplify BakedModelManagerMixin.java

**File**: `src/main/java/me/pepperbell/continuity/client/mixin/BakedModelManagerMixin.java`  
**Action**: ⏳ **REMOVE OBSOLETE INJECTIONS**  
**Priority**: 🟡 HIGH  
**Dependencies**: After Tasks 1 & 2 (file deletions)

**Why**:
- References removed `BakedModelManagerBakeContext` (Task 1)
- References removed `BakedModelManagerReloadExtension` (Task 2)
- Contains 6 obsolete injections that depend on removed APIs
- New injection point (`SpriteAtlasTexture.upload()`) replaces all functionality

**Expected Current State**:
```java
@Mixin(BakedModelManager.class)
abstract class BakedModelManagerMixin {
    @Inject(method = "reload(...)") { ... }       // ❌ OBSOLETE
    @Inject(method = "bake(...)") { ... }         // ❌ OBSOLETE
    @Inject(method = "upload(...)") { ... }       // ❌ OBSOLETE
    // ... 3 more obsolete injections
}
```

**Action Plan**:
- [ ] Remove all 6 `@Inject` annotations
- [ ] Remove method bodies that reference removed classes
- [ ] Either delete file entirely OR leave as empty/placeholder mixin
- [ ] Update `continuity.mixins.json` if removing
- [ ] Run build

**Possible Final State** (option 1 - delete):
- Remove from `continuity.mixins.json`
- Delete the file

**Possible Final State** (option 2 - placeholder):
```java
@Mixin(BakedModelManager.class)
abstract class BakedModelManagerMixin {
    // Placeholder - functionality moved to SpriteAtlasTextureMixin
}
```

**Checklist**:
- [ ] Identify all 6 obsolete injections
- [ ] Remove injection code
- [ ] Test: can file be deleted?
- [ ] If deleting: remove from mixins.json
- [ ] Run build
- [ ] Verify no references remain

**Completion Criteria**:
- [ ] File simplified or deleted
- [ ] No compilation errors
- [ ] Build successful

---

## TASK 5: Create SpriteAtlasTextureMixin.java - NEW MIXIN

**File**: `src/main/java/me/pepperbell/continuity/client/mixin/SpriteAtlasTextureMixin.java`  
**Action**: ⏳ **CREATE NEW**  
**Priority**: 🔴 CRITICAL  
**Replaces**: Tasks 1, 2, 4 functionality

**Purpose**: 
Capture `SpriteAtlasTexture` block atlas reference for later use by `RenderUtil` (fixes Issue #3: getAtlas() removal)

**Design Requirements**:
- Inject into `SpriteAtlasTexture.upload()` method
- Only capture when ID == `BLOCK_ATLAS_TEXTURE`
- Store reference in external utility (AtlasStorage - see below)
- ⚠️ **CRITICAL**: Keep all static methods PRIVATE (mixin rule violation discovered in Phase 4)

**Template Structure**:
```java
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    @Unique
    private static volatile SpriteAtlasTexture continuity$blockAtlas;
    
    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V", 
            at = @At("HEAD"))
    private void continuity$onUpload(SpriteLoader.StitchResult stitch, CallbackInfo ci) {
        if (/* ID is BLOCK_ATLAS_TEXTURE */) {
            // Store reference via AtlasStorage utility
        }
    }
}
```

**Related Work** (prerequisite):
- Must create `AtlasStorage.java` utility class first (see Task 5B)

**Checklist**:
- [ ] Create `AtlasStorage.java` first (Task 5B)
- [ ] Create `SpriteAtlasTextureMixin.java` with upload injection
- [ ] Register in `continuity.mixins.json`
- [ ] Call AtlasStorage.setBlockAtlas() from injection
- [ ] Test: Minecraft loads without mixin errors
- [ ] Verify sprite finder works

**Mixin Rules to Remember**:
- ✅ Private static fields are OK
- ✅ Private static methods are OK
- ❌ Public static methods cause InvalidMixinException at runtime
- ✅ Use external utility classes for any public access

**Completion Criteria**:
- [ ] No compilation errors
- [ ] Mixin loads without InvalidMixinException
- [ ] Block atlas captured successfully

---

## TASK 5B: Create AtlasStorage.java - UTILITY CLASS

**File**: `src/main/java/me/pepperbell/continuity/client/util/AtlasStorage.java`  
**Action**: ⏳ **CREATE NEW**  
**Priority**: 🔴 CRITICAL  
**Dependency**: Required by Task 5 (SpriteAtlasTextureMixin)

**Purpose**:
External storage class to hold `SpriteAtlasTexture` reference without violating mixin rules (public static methods forbidden)

**Template**:
```java
public final class AtlasStorage {
    private static volatile SpriteAtlasTexture blockAtlas;
    
    static void setBlockAtlas(SpriteAtlasTexture atlas) {
        blockAtlas = atlas;
    }
    
    public static SpriteAtlasTexture getBlockAtlas() {
        return blockAtlas;
    }
    
    static void reset() {
        blockAtlas = null;
    }
}
```

**Checklist**:
- [ ] Create file with above structure
- [ ] Use volatile for thread-safe access
- [ ] Keep setBlockAtlas package-private (only mixin accesses)
- [ ] Keep getBlockAtlas public (RenderUtil accesses)
- [ ] Test compilation

---

## TASK 6: Update RenderUtil.java - FIX BROKEN LINE 65

**File**: `src/main/java/me/pepperbell/continuity/client/util/RenderUtil.java`  
**Problem Line**: Line 65  
**Action**: ⏳ **UPDATE**  
**Priority**: 🔴 CRITICAL  
**Dependencies**: Tasks 5 & 5B (SpriteAtlasTextureMixin + AtlasStorage)

**The Problem**:
```java
// Line 65 - BROKEN IN 1.21.10
blockAtlasSpriteFinder = MODEL_MANAGER
    .getAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)  // ❌ METHOD REMOVED
    .spriteFinder();
```

**Why It's Broken**:
- Method `BakedModelManager.getAtlas(Identifier)` does not exist in 1.21.10
- Part of removed `SpriteAtlasManager` API
- Breaks at compile time when dependencies updated to 1.21.10

**Solution** (using AtlasStorage from Task 5B):
```java
// NEW (FIXED)
blockAtlasSpriteFinder = AtlasStorage.getBlockAtlas().spriteFinder();
```

**Checklist**:
- [ ] Ensure Tasks 5 & 5B complete first
- [ ] Replace line 65 with AtlasStorage call
- [ ] Remove unused MODEL_MANAGER field if possible
- [ ] Run build
- [ ] Verify sprite finder works

**Completion Criteria**:
- [ ] No compilation errors
- [ ] Build successful
- [ ] Sprite finder initialized correctly

---

## Implementation Order (Dependency Chain)

```
1. TASK 1: Delete BakedModelManagerBakeContext.java
2. TASK 2: Delete BakedModelManagerReloadExtension.java
3. TASK 4: Simplify BakedModelManagerMixin.java
4. TASK 3: Fix SpriteLoaderMixin (create StitchResultExtension + Mixin)
5. TASK 5B: Create AtlasStorage.java
6. TASK 5: Create SpriteAtlasTextureMixin.java
7. TASK 6: Update RenderUtil.java line 65
```

**Verification**:
- [ ] All tasks complete
- [ ] `.\gradlew clean build` → **BUILD SUCCESSFUL**
- [ ] No mixin errors in Minecraft launch
- [ ] CTM textures render correctly (Phase 4 testing)

---

## Success Criteria (End State)

- ✅ 2 obsolete files deleted (Tasks 1 & 2)
- ✅ BakedModelManagerMixin simplified (Task 4)
- ✅ SpriteLoaderMixin fixed with interface pattern (Task 3)
- ✅ New SpriteAtlasTextureMixin created (Task 5)
- ✅ AtlasStorage utility created (Task 5B)
- ✅ RenderUtil updated (Task 6)
- ✅ Build successful with no errors
- ✅ Mixin loads without InvalidMixinException
- ✅ All CTM features ready for Phase 4 testing

