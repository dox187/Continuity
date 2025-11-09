# Mixin Rules and Gotchas - Minecraft Modding Guide

**Date**: November 9, 2025  
**Status**: 📋 **Documentation from Phase 4 Runtime Testing**  
**Framework**: Sponge Mixin 0.16.5+mixin.0.8.7  
**Minecraft**: 1.21.10  

---

## 🔴 CRITICAL RULE: Static Method Visibility

### THE RULE
```
Static methods in mixins MUST be PRIVATE.
Public/Protected static methods are FORBIDDEN.
```

### WHY THIS MATTERS

Static methods in a mixin become part of the **target class** after mixin transformation:

```java
@Mixin(MyTargetClass.class)
public class MyMixin {
    public static void myMethod() { }  // ❌ PROBLEM
}
```

**After mixin transformation:**
```java
public class MyTargetClass {
    public static void myMethod() { }  // ← Now part of Minecraft's class!
}
```

**Consequences**:
- ❌ Exposes internal mod methods in Minecraft's public API
- ❌ Creates namespace pollution
- ❌ Breaks mixin contract
- ❌ Runtime: `InvalidMixinException`

---

## GOTCHA #1: Public Static Methods - Runtime Error

### The Problem
```java
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    public static SpriteAtlasTexture continuity$getBlockAtlas() {  // ❌ ERROR
        return blockAtlas;
    }
}
```

### The Error
```
org.spongepowered.asm.mixin.transformer.throwables.InvalidMixinException: 
Mixin continuity.mixins.json:SpriteAtlasTextureMixin from mod continuity contains 
non-private static method continuity$getBlockAtlas()Lnet/minecraft/class_1059;
```

### When It Happens
- ✅ Compiles successfully (compiler doesn't check mixin rules)
- ❌ Crashes at runtime during mixin loading
- 📍 Discovered in Phase 4 (Runtime Testing) when launching Minecraft

### The Solution
Use an **external utility class** instead:

```java
// GOOD: Separate utility class
public final class AtlasStorage {
    private static volatile SpriteAtlasTexture blockAtlas;
    
    // Public accessor - safe because NOT in a mixin
    public static SpriteAtlasTexture getBlockAtlas() {
        return blockAtlas;
    }
    
    // Package-private setter - only called from mixin
    static void setBlockAtlas(SpriteAtlasTexture atlas) {
        blockAtlas = atlas;
    }
}

// GOOD: Mixin calls external utility
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    @Inject(method = "upload(...)")
    private void continuity$onUpload(...) {
        AtlasStorage.setBlockAtlas((SpriteAtlasTexture) (Object) this);
    }
}
```

### Key Points
- ✅ Private static methods in mixins are OK (internal only)
- ✅ Public static methods in utility classes are OK (not in mixin)
- ❌ Public static methods in mixins cause runtime errors
- ✅ Use external utilities to expose data from mixins

---

## BEST PRACTICE: Mixin Visibility Pattern

### Pattern 1: No Public Exposure Needed
```java
@Mixin(SomeClass.class)
abstract class MyMixin {
    @Unique
    private static int myCounter;
    
    // ✅ GOOD: Private static method
    private static void updateCounter() {
        myCounter++;
    }
}
```

### Pattern 2: Public Exposure Via Interface
```java
// Step 1: Create interface (no @Mixin)
public interface MyShadowedData {
    int continuity$getData();
}

// Step 2: Mixin implements it with @Shadow
@Mixin(TargetClass.class)
abstract class MyMixin implements MyShadowedData {
    @Shadow
    private int data;
    
    @Override
    public int continuity$getData() {
        return this.data;  // Safe: returns target's data
    }
}

// Step 3: Cast and use
MyShadowedData casted = (MyShadowedData) (Object) targetInstance;
int data = casted.continuity$getData();  // ✅ Safe
```

### Pattern 3: Static Storage in Utility Class
```java
// Step 1: Create utility class
public final class StaticDataStorage {
    private static volatile MyData data;
    
    // ✅ GOOD: Public static in utility, not mixin
    public static MyData getData() {
        return data;
    }
    
    // ✅ GOOD: Package-private setter
    static void setData(MyData newData) {
        data = newData;
    }
}

// Step 2: Mixin populates it
@Mixin(Producer.class)
abstract class ProducerMixin {
    @Inject(method = "produce(...)")
    private void continuity$onProduce(..., CallbackInfo ci) {
        StaticDataStorage.setData(/* extract data */);
    }
}

// Step 3: Other code reads it
MyData result = StaticDataStorage.getData();  // ✅ Safe
```

---

## GOTCHA #2: Hidden API Changes - Discovered Late

### The Problem
Some API changes are **invisible** until you actually build with the new dependencies:

```java
// Looks fine with old dependencies (1.21.6):
Map<Identifier, Sprite> sprites = stitch.regions();  // ✅ Works

// Broken with new dependencies (1.21.10):
Map<Identifier, Sprite> sprites = stitch.regions();  // ❌ Method not found!
// (Was renamed to sprites())
```

### When It Happens
- ✅ Old dependency: Method exists, code compiles
- ❌ New dependency: Method removed/renamed, compilation fails
- 📍 Discovered during Phase 3 Step 10 (dependency update)
- **Key**: Only revealed AFTER updating gradle.properties

### Solutions

**Option A: Use @Shadow to Access Fields**
```java
public interface StitchResultExtension {
    Map<Identifier, Sprite> continuity$getSprites();
}

@Mixin(SpriteLoader.StitchResult.class)
abstract class StitchResultMixin implements StitchResultExtension {
    @Shadow
    @Final
    private Map<Identifier, Sprite> sprites;  // ← Safe field access
    
    @Override
    public Map<Identifier, Sprite> continuity$getSprites() {
        return this.sprites;
    }
}
```

**Option B: Check Yarn Mappings**
- Always verify method names in target Yarn mappings before upgrade
- Check for record component renames
- Document any discovered API changes

### Prevention Checklist
- [ ] Update dependencies FIRST
- [ ] Attempt build immediately
- [ ] Document any compilation errors
- [ ] Research why in target version
- [ ] Design fixes using safe patterns

---

## GOTCHA #3: Method Descriptor Changes

### The Problem
Mixin method descriptors depend on obfuscated class names that change between versions:

```java
// 1.21.6 descriptor
@Inject(method = "stitch(Ljava/util/List;ILjava/util/concurrent/Executor;)Lnet/minecraft/client/texture/SpriteLoader$StitchResult;")

// 1.21.10 descriptors - might be different!
// Class names remapped by Fabric Loom
```

### When It Happens
- Minecraft class names are obfuscated (e.g., `class_1234`)
- Each version gets different obfuscation names
- Loom automatically remaps Yarn → Intermediary
- Mixins must use correct remapped descriptors

### Solution
- ✅ Fabric Loom handles remapping automatically
- ✅ Use human-readable names in `@Mixin` and `method=""` parameters
- ✅ Loom converts to obfuscated names during compilation
- ✅ No manual descriptor changes needed (usually)

### When You Need to Fix
If a method descriptor breaks after upgrade:
1. Find the method in Yarn 1.21.10 mappings
2. Note the exact signature
3. Update the `method=""` parameter
4. Let Loom handle obfuscation

---

## GOTCHA #4: Removed Classes Cascade

### The Problem
When a class is removed, all files using it fail to compile:

```
SpriteAtlasManager (REMOVED)
    ↓ used by
SpriteAtlasManager.AtlasPreparation (REMOVED)
    ↓ used by
BakedModelManagerReloadExtension.java (BROKEN)
    ↓ used by
BakedModelManagerMixin.java (BROKEN)
    ↓ used by
BakedModelManagerBakeContext.java (BROKEN)
```

### When It Happens
- Multiple files depend on a single removed API
- Removing one class cascades breakage through files
- **Symptom**: One removed class breaks 3+ compilation targets

### Prevention
- [ ] Analyze dependency chains before upgrade
- [ ] Identify cascade risks early
- [ ] Plan deletion/refactoring order
- [ ] Test each deletion independently

### Solution
```
1. Identify all files using removed class
2. Delete leaf files first (no dependents)
3. Move up dependency chain
4. Replace functionality with new approach
```

**Example Order** (from real Phase 3):
```
1. Delete BakedModelManagerReloadExtension.java
2. Delete BakedModelManagerBakeContext.java
3. Simplify BakedModelManagerMixin.java
4. Create SpriteAtlasTextureMixin.java (replacement)
```

---

## GOTCHA #5: Volatile Field Access in Static Contexts

### The Problem
When storing references in static fields, thread safety matters:

```java
// ❌ RISKY: Not volatile, could have visibility issues
public static SpriteAtlasTexture blockAtlas;

// ✅ SAFE: Volatile ensures visibility across threads
public static volatile SpriteAtlasTexture blockAtlas;
```

### Why It Matters
- Minecraft renders on multiple threads
- Mods load on initialization thread
- Atlas reference set during loading, read during rendering
- Without `volatile`: Render thread might see stale cache

### Solution
```java
public final class AtlasStorage {
    private static volatile SpriteAtlasTexture blockAtlas;  // ✅ volatile
    
    public static SpriteAtlasTexture getBlockAtlas() {
        return blockAtlas;
    }
    
    static void setBlockAtlas(SpriteAtlasTexture atlas) {
        blockAtlas = atlas;
    }
}
```

---

## Mixin Development Checklist

When creating new mixins for version upgrades:

### Design Phase
- [ ] Identify target class and method
- [ ] Check if method exists in new version
- [ ] Research any signature changes
- [ ] Plan visibility: private vs exposed via utility
- [ ] Identify any cascade dependencies

### Implementation Phase
- [ ] Never use public static methods in mixins
- [ ] Use @Shadow for field access patterns
- [ ] Use interfaces for exposing mixin features
- [ ] Use external utilities for static storage
- [ ] Keep mixin methods private when possible

### Testing Phase
- [ ] Compile: `.\gradlew clean build`
- [ ] Check: No compilation errors
- [ ] Launch: `Minecraft` without mixin errors
- [ ] Verify: InvalidMixinException does not occur
- [ ] Test: Feature actually works

### Documentation
- [ ] Document why mixin was created
- [ ] Document any hidden API issues found
- [ ] Document visibility decisions
- [ ] Document dependency chains

---

## Reference: Sponge Mixin Rules Summary

```
✅ ALLOWED:
- Private static fields in mixins
- Private static methods in mixins
- Public static fields in non-mixin classes
- Public static methods in non-mixin classes
- @Shadow to access target's fields
- Interface pattern for exposing mixin features

❌ FORBIDDEN:
- Public static fields in mixins (rarely)
- Public static methods in mixins (ALWAYS - InvalidMixinException)
- Protected static methods in mixins (ALWAYS)
- Direct method calls between unrelated mixins
- Circular mixin dependencies
```

---

## Real-World Example: Phase 4 Error

**Error**: `InvalidMixinException` with public static method  
**File**: `SpriteAtlasTextureMixin.java`  
**Method**: `continuity$getBlockAtlas()`  
**Solution**: Moved to external `AtlasStorage` utility class  
**Result**: ✅ Minecraft loads, feature works  

This discovery informed all patterns above.

---

## Further Learning

- Sponge Mixin Documentation: https://docs.spongepowered.org/
- Fabric Mixin Wiki: https://fabricmc.net/
- Phase 4 Runtime Error: See `PHASE4_RUNTIME_ERROR.md` in this project
- Analysis Reports: See `ANALYSIS_MIXINS_LAYER.md` for detailed layer analysis

