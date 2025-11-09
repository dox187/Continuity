# PHASE 2B: Implementation Strategy Update

**Date**: November 9, 2025  
**Status**: 🟢 BREAKTHROUGH - New injection point discovered  
**Impact**: Significantly simplifies implementation

---

## Major Discovery: New Injection Point

### Old Strategy (OBSOLETE)
```
BakedModelManager.bake(Map<Identifier, AtlasPreparation> preparations)
    ↓
Modify preparations directly
```

### New Strategy (CURRENT)
```
SpriteAtlasTexture.upload(SpriteLoader.StitchResult stitchResult)
    ↓
Access/modify sprites via stitchResult.regions() / sprites()
    ↓
Simpler + more direct approach
```

---

## Why This is Better

| Aspect | Old Approach | New Approach |
|--------|--------------|-------------|
| **Injection Point** | `BakedModelManager.bake()` (nested inside) | `SpriteAtlasTexture.upload()` (dedicated method) |
| **Data Access** | Via `AtlasPreparation` wrapper objects | Direct `SpriteLoader.StitchResult` |
| **Timing** | During model baking (mixed concerns) | During sprite atlas upload (focused) |
| **Stability** | Depends on removed `SpriteAtlasManager` | Uses stable `SpriteLoader.StitchResult` |
| **Per-Atlas** | All at once via method param | One per call (cleaner) |

---

## Implementation Roadmap (UPDATED)

### Phase 2B-1: Create SpriteAtlasTexture Mixin
**File to Create**: `src/main/java/me/pepperbell/continuity/client/mixin/SpriteAtlasTextureMixin.java`

```java
@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V")
    private void continuity$onUpload(SpriteLoader.StitchResult stitchResult, CallbackInfo ci) {
        // Intercept sprite upload
        // Access sprites via stitchResult.regions() or stitchResult.sprites()
        // Attach emissive references as needed
    }
}
```

### Phase 2B-2: Update BakedModelManagerMixin
**Status**: Simplify/remove old patterns, rely on new mixin above

### Phase 2B-3: Simplify BakedModelManagerBakeContext
**Status**: May no longer be needed, or becomes simpler interface

---

## Modified File Priority

### CRITICAL (Must complete first):
1. **SpriteAtlasTextureMixin.java** (NEW FILE)
   - Implement `upload()` interception
   - Learn `SpriteLoader.StitchResult` API

2. **FILE #6: AtlasLoaderMixin.java**
   - Update method descriptors
   - Verify injection points still valid

### HIGH (Depends on above):
3. **FILE #3: SpriteLoaderMixin.java**
   - Verify `.regions()` compatibility
   - Likely minimal changes needed

4. **FILE #1: BakedModelManagerMixin.java**
   - Simplify or potentially remove
   - May become obsolete

### MEDIUM (Follow-on work):
5. **FILE #2: BakedModelManagerBakeContext.java**
   - Redesign with new pattern
   - Possible removal if not needed

6. **FILE #5: BakedModelManagerReloadExtension.java**
   - Update based on new context interface
   - Simplify with new approach

---

## Next Immediate Actions

### MUST DO IMMEDIATELY:
1. [ ] Verify `SpriteAtlasTexture.upload()` is called per-atlas during reload
2. [ ] Check `SpriteLoader.StitchResult` method names in Yarn 1.21.10
3. [ ] Look for any texture modification opportunities in the new flow

### Investigation Commands:
```bash
# Check StitchResult methods
cat '.lib_src/yarn-1.21.10/mappings/net/minecraft/client/texture/SpriteLoader.mapping' | grep -A 30 'class_7767 StitchResult'

# Check SpriteAtlasTexture in context
grep -r "SpriteAtlasTexture" '.lib_src/fabric-1.21.10/src/' 2>/dev/null | head -10

# Find upload call sites
find '.lib_src/yarn-1.21.10/mappings' -name '*.mapping' -exec grep -l "upload" {} \;
```

---

## Estimated Implementation Complexity

| Task | Old Estimate | New Estimate | Change |
|------|--------------|--------------|--------|
| Understand API changes | 4 hours | 2 hours | ✅ -50% |
| Create new mixin | N/A | 1 hour | ✅ NEW (easier) |
| Update existing mixins | 6 hours | 3 hours | ✅ -50% |
| Test & verify | 2 hours | 1.5 hours | ✅ -25% |
| **TOTAL** | 12 hours | 7.5 hours | ✅ **-37%** |

---

## Risk Assessment

| Risk | Old Approach | New Approach |
|------|--------------|-------------|
| Finding correct injection point | 🔴 HIGH | 🟢 LOW |
| API stability | 🔴 HIGH (removed class) | 🟢 LOW (stable StitchResult) |
| Code maintainability | 🟡 MEDIUM | 🟢 HIGH |
| Parallel modification safety | 🟡 MEDIUM | 🟢 HIGH |
| Performance | 🟡 MEDIUM | 🟢 HIGH (focused mixin) |

---

## Decision: ADOPT NEW STRATEGY

**Recommendation**: Use the new `SpriteAtlasTexture.upload()` injection point

**Rationale**:
- Direct access to sprite data
- Cleaner separation of concerns
- Uses stable APIs
- Simpler to understand and maintain
- Lower risk implementation

**Next Step**: Proceed to detailed implementation planning with new strategy.

