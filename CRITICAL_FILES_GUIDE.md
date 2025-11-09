# Critical File Fix Guide - Minecraft 1.21.10 Migration

**Purpose**: Detailed migration guide for the 6 CRITICAL files  
**Status**: Research Phase  
**Updated**: November 9, 2025

---

## 📍 CRITICAL FILE #1: `BakedModelManagerBakeContext.java`

### Current Code
```java
import net.minecraft.client.render.model.SpriteAtlasManager;

public interface BakedModelManagerBakeContext {
    ThreadLocal<BakedModelManagerBakeContext> THREAD_LOCAL = new ThreadLocal<>();
    void beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlases);
}
```

### Problem
- `SpriteAtlasManager` class **does not exist** in Minecraft 1.21.10
- `AtlasPreparation` nested class is gone
- New API structure for atlas preparation unknown

### Research Needed
- [ ] Find what replaces `SpriteAtlasManager` in 1.21.10
- [ ] Locate new `AtlasPreparation` equivalent
- [ ] Check if Fabric provides wrapper/compatibility layer
- [ ] Look in `.lib_src/fabric-1.21.10/` for render API changes

### Potential Solutions
1. **Option A**: Use new atlas preparation API directly
2. **Option B**: Use Fabric Rendering API wrapper
3. **Option C**: Migrate to Fabric Resource Loader v1 pattern
4. **Option D**: Use BlockRenderView context instead

### Action Required
```
Status: 🔴 BLOCKED - Needs API research
Next: Search in fabric-1.21.10 sources for atlas preparation
```

---

## 📍 CRITICAL FILE #2: `BakedModelManagerReloadExtension.java`

### Current Code
```java
public class BakedModelManagerReloadExtension implements BakedModelManagerBakeContext {
    public void beforeBake(Map<Identifier, SpriteAtlasManager.AtlasPreparation> preparations) {
        // Uses SpriteAtlasManager.AtlasPreparation
        SpriteAtlasManager.AtlasPreparation preparation = preparations.get(spriteId.getAtlasId());
        Sprite sprite = preparation.getSprite(spriteId.getTextureId());
    }
}
```

### Problem
- **Depends on CRITICAL FILE #1**
- `SpriteAtlasManager.AtlasPreparation` doesn't exist
- `getSprite()` method signature unknown
- Cannot proceed until #1 is resolved

### Dependency Chain
```
BakedModelManagerReloadExtension.java
    ↓ depends on
BakedModelManagerBakeContext.java  (FILE #1)
    ↓ depends on
SpriteAtlasManager API (MISSING in 1.21.10)
```

### Action Required
```
Status: 🔴 BLOCKED - Depends on FILE #1
Next: After FILE #1 is researched, update this file
```

---

## 📍 CRITICAL FILE #3: `SpriteLoaderMixin.java`

### Current Code
```java
@Inject(method = "stitch(...)", at = @At("RETURN"))
private void continuity$onReturnStitch(..., CallbackInfoReturnable<SpriteLoader.StitchResult> cir) {
    Map<Identifier, Sprite> sprites = cir.getReturnValue().regions();  // ← PROBLEM HERE
    // ...
}
```

### Problem
- `.regions()` method **likely doesn't exist or signature changed** in 1.21.10
- `StitchResult` class structure might be different
- Mixin descriptor might not match new obfuscated names

### Research Needed
- [ ] Check `SpriteLoader.StitchResult` in Yarn 1.21.10 mappings
- [ ] Verify method exists: `.regions()` or new name like `.getRegions()`
- [ ] Find alternative way to get sprite map if method removed
- [ ] Check Yarn mapping descriptors

### Potential Solutions
1. **Option A**: Rename method call to new signature
2. **Option B**: Use different field/accessor in StitchResult
3. **Option C**: Hook into different method for sprite access
4. **Option D**: Use new Fabric API for sprite access

### Mixin Descriptor Update Needed
```
Current: method = "stitch(...)"
Might need update to match 1.21.10 obfuscated names
```

### Action Required
```
Status: 🟡 RESEARCH - Check Yarn mappings
Next: Open .lib_src/yarn-1.21.10/mappings/ and search for StitchResult
```

---

## 📍 CRITICAL FILE #4: `SpriteLoaderLoadContext.java`

### Current Code
```java
public interface SpriteLoaderLoadContext {
    ThreadLocal<SpriteLoaderLoadContext> THREAD_LOCAL = new ThreadLocal<>();
    
    CompletableFuture<@Nullable Set<Identifier>> getExtraIdsFuture(Identifier atlasId);
    
    @Nullable
    EmissiveControl getEmissiveControl(Identifier atlasId);
    
    interface EmissiveControl {
        @Nullable
        Map<Identifier, Identifier> getEmissiveIdMap();
        void setEmissiveIdMap(Map<Identifier, Identifier> map);
        void markHasEmissives();
    }
}
```

### Problem
- **Depends on CRITICAL FILE #3**
- Sprite loader context might have different structure in 1.21.10
- Extra IDs mechanism might have changed
- Emissive control pattern might be incompatible

### Dependency
```
SpriteLoaderLoadContext.java
    ↓ depends on
SpriteLoaderMixin.java  (FILE #3)
    ↓ depends on
SpriteLoader API changes
```

### Action Required
```
Status: 🔴 BLOCKED - Depends on FILE #3
Next: After FILE #3 is resolved, verify context still works
```

---

## 📍 CRITICAL FILE #5: `BakedModelManagerMixin.java`

### Current Code
```java
@Inject(method = "reload(...)", at = @At("HEAD"))
private void continuity$onHeadReload(...) {
    continuity$reloadExtension = new BakedModelManagerReloadExtension(resourceManager, prepareExecutor);
}

private static void continuity$onHeadBake(final Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlases, ...) {
    // Uses SpriteAtlasManager.AtlasPreparation
}
```

### Problem
- **Depends on CRITICAL FILES #1 & #2**
- `SpriteAtlasManager.AtlasPreparation` reference is invalid
- Method `reload()` descriptor might not match new obfuscated names
- Method `bake()` might have different signature

### Dependency Chain
```
BakedModelManagerMixin.java
    ↓ depends on
BakedModelManagerReloadExtension.java  (FILE #2)
    ↓ depends on
SpriteAtlasManager API  (Missing)
```

### Action Required
```
Status: 🔴 BLOCKED - Depends on FILES #1 & #2
Next: After FILES #1 & #2 resolved, regenerate mixin descriptors
```

---

## 📍 CRITICAL FILE #6: `AtlasLoaderMixin.java`

### Current Code
```java
@ModifyVariable(method = "<init>(...)", ...)
private List<AtlasSource> continuity$modifySources(List<AtlasSource> sources) {
    // Adds CTM atlas sources
}

@Inject(method = "loadSources(...)", at = @At(...))
private void continuity$afterLoadSources(ResourceManager resourceManager, ...) {
    // Modifies loaded sources
}
```

### Problem
- `AtlasSource` class structure might have changed
- `loadSources()` method signature might differ
- Mixin injection points might not match new descriptors
- Atlas loading pipeline might be restructured

### Research Needed
- [ ] Check `AtlasSource` in Yarn 1.21.10
- [ ] Verify `AtlasLoader` class structure
- [ ] Find new `loadSources()` method signature
- [ ] Check if atlas loading pattern changed

### Mixin Descriptor Issues
```
Method: "<init>(...)" descriptor might not match
Method: "loadSources(...)" descriptor might not match
Need to regenerate with Yarn 1.21.10
```

### Action Required
```
Status: 🟡 RESEARCH - Needs descriptor verification
Next: Generate new method descriptors with Yarn 1.21.10 mappings
```

---

## 🔬 Research Commands

### To Research These Files:

1. **Check Atlas/Sprite API in Fabric 1.21.10**
```bash
ls -la .lib_src/fabric-1.21.10/ | grep -i render
ls -la .lib_src/fabric-1.21.10/fabric-renderer-api-v1/
```

2. **Find SpriteAtlasManager replacement**
```bash
grep -r "class.*Atlas" .lib_src/fabric-1.21.10/fabric-rendering-v1/src/
grep -r "SpriteAtlas" .lib_src/fabric-1.21.10/
```

3. **Check StitchResult in Yarn**
```bash
grep -r "StitchResult" .lib_src/yarn-1.21.10/
grep -r "regions" .lib_src/yarn-1.21.10/ | grep -i sprite
```

4. **Verify Mixin Descriptors**
```bash
# Need to use Yarn 1.21.10 to regenerate descriptors
# Check mapping format in .lib_src/yarn-1.21.10/mappings/
```

---

## 📊 Dependency Resolution Order

```
MUST RESEARCH FIRST (independent):
1. FILE #3: SpriteLoaderMixin.java
   - Check StitchResult.regions() 
   - Verify Yarn descriptors

2. FILE #1: BakedModelManagerBakeContext.java
   - Find SpriteAtlasManager replacement
   - Locate AtlasPreparation equivalent

3. FILE #6: AtlasLoaderMixin.java
   - Verify AtlasSource compatibility
   - Check descriptor updates

THEN IMPLEMENT (depends on above):
4. FILE #4: SpriteLoaderLoadContext.java
   - Depends on FILE #3 results

5. FILE #2: BakedModelManagerReloadExtension.java
   - Depends on FILE #1 results

6. FILE #5: BakedModelManagerMixin.java
   - Depends on FILES #1, #2, #3, #6 results
```

---

## 🎯 Next Steps (Priority Order)

### IMMEDIATE
- [ ] Research FILE #3: Check Yarn 1.21.10 for `StitchResult` methods
- [ ] Research FILE #1: Find Fabric API replacements for `SpriteAtlasManager`
- [ ] Research FILE #6: Verify `AtlasLoader` changes

### AFTER RESEARCH
- [ ] Implement FILE #1 & #6 based on findings
- [ ] Update FILE #2 based on FILE #1
- [ ] Verify FILE #3 works with new signatures
- [ ] Update FILE #4 based on FILE #3
- [ ] Rewrite FILE #5 with new mixin pattern

### TESTING
- [ ] Full build verification
- [ ] Mixin injection verification
- [ ] Runtime testing with CTM textures

---

## 📝 Status Tracker

| File # | Name | Status | Blocker | Next Action |
|--------|------|--------|---------|------------|
| 1 | BakedModelManagerBakeContext | 🔴 RESEARCH | None | Find new Atlas API |
| 2 | BakedModelManagerReloadExtension | 🔴 BLOCKED | FILE #1 | Wait for FILE #1 |
| 3 | SpriteLoaderMixin | 🟡 RESEARCH | None | Check Yarn mappings |
| 4 | SpriteLoaderLoadContext | 🔴 BLOCKED | FILE #3 | Wait for FILE #3 |
| 5 | BakedModelManagerMixin | 🔴 BLOCKED | FILES #1-3, #6 | Wait for others |
| 6 | AtlasLoaderMixin | 🟡 RESEARCH | None | Verify descriptors |

---

**This document will be updated as research progresses.**
