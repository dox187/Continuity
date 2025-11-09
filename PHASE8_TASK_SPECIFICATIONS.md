# Phase 8 Task Specifications: Emissive & Animated Textures

**Date**: November 9, 2025  
**Phase**: Phase 8 - Emissive textures & Animated textures  
**Status**: � **IN PROGRESS** - Emissive rendering fix applied  
**Minecraft Version**: 1.21.10  

> **Background**: Phase 7 successfully fixed CTM texture initial loading. Phase 8 focuses on two remaining issues:
> - ⚠️ **Emissive textures**: Present but not getting rendered with glow effect → **FIX APPLIED**
> - ❓ **Animated textures**: Uncertain if working, needs verification

## Latest Update - Emissive Rendering Fix (November 9, 2025)

**Problem Identified**: Thread-local context missing in fallback mode caused emissive detection to fail.

**Root Cause**:
1. ✅ `AtlasLoaderMixin` detected emissive textures → stored in `EmissiveIdMapStorage`
2. ✅ `SpriteLoaderMixin` attached emissive sprites via fallback mechanism
3. ❌ `SpriteAtlasTextureMixin` only checked `ThreadLocal` context → missed stored mappings
4. ❌ `ModelWrappingHandler` never enabled `wrapEmissive` → no emissive rendering

**Solution Applied**:
- **File 1**: `SpriteLoaderMixin.java`
  - Added fallback to read from `EmissiveIdMapStorage` when `ThreadLocal` context is null
  - Creates `SpriteLoaderStitchContext` from stored mappings
  - Log: "PHASE 8 FALLBACK: Using EmissiveIdMapStorage"

- **File 2**: `SpriteAtlasTextureMixin.java`
  - Added fallback emissive detection via `EmissiveIdMapStorage`
  - Now correctly sets `hasEmissives = true` when stored mappings exist
  - Enables `ModelWrappingHandler.setInstance(wrapCtm, wrapEmissive: true)`

**Expected Result**: 
- Emissive textures should now render with glow effect
- Soul lanterns, glowing ores should shine
- `EmissiveBlockStateModel` wrapping enabled

---

## Executive Summary

Phase 8 addresses the known issues remaining after Phase 7's successful CTM initial load fix:

1. **Emissive Textures Issue** (CRITICAL)
   - Emissive sprite references ARE stored in SpriteMixin
   - Property IS being set during sprite loading
   - BUT: Rendering pipeline NOT using the property
   - Expected: Soul lanterns glow, other emissive blocks shine
   - Actual: All blocks render normally
   - Root: Unknown - needs debugging

2. **Animated Textures Status** (UNCERTAIN)
   - Expected: Should work automatically (Minecraft handles animation)
   - Actual: Not tested yet
   - Risk: Low (animation is Minecraft feature, not CTM-dependent)
   - Action: Verify with test

3. **Overall Phase 8 Goal**
   - Fix emissive rendering system
   - Verify animated textures work
   - Comprehensive testing of all CTM features
   - Complete documentation

---

## Task 1: Debug Emissive Property Flow ✅ **COMPLETE**

### Objective
Find where emissive sprite property is lost during the rendering pipeline.

### Investigation Results

#### Problem Found
Emissive textures were detected and stored but rendering pipeline never activated:

**Data Flow Analysis**:
1. ✅ `AtlasLoaderMixin.continuity$afterLoadSources()`: 19 emissive textures found (diamond_ore, iron_ore, etc.)
2. ✅ Stored in `EmissiveIdMapStorage.put(atlasId, emissiveIdMap)`
3. ✅ `SpriteLoaderMixin.continuity$modifyFunction()`: Fallback reads from storage
4. ✅ `SpriteLoaderMixin.continuity$onReturnStitch()`: Sprites attached successfully
5. ❌ `SpriteAtlasTextureMixin.continuity$onUpload()`: Checked only `ThreadLocal` context → returned `hasEmissives = false`
6. ❌ `ModelWrappingHandler.setInstance(wrapCtm: true, wrapEmissive: false)` → No wrapping!
7. ❌ `EmissiveBlockStateModel` never used → No glow rendering

**Log Evidence**:
```
[22:36:05] EMISSIVE ASSIGNMENT:
  Base sprite: minecraft:block/deepslate_iron_ore
  Emissive sprite: minecraft:block/deepslate_iron_ore_e
  Attachment successful: true

[22:36:06] ModelWrappingHandler instance created - wrapCtm: true, wrapEmissive: false
[22:36:06] Enabling ModelWrappingHandler - shouldWrapCtm: true, hasEmissives: false
```

### Root Cause
The `SpriteAtlasTextureMixin` only checked `SpriteLoaderStitchContext.THREAD_LOCAL.get()` for emissive detection, but in fallback mode (Phase 7+8 multi-threaded loading), the context doesn't exist at upload time. Mappings were stored globally in `EmissiveIdMapStorage` but never checked during model wrapping decision.

### Success Criteria
- [x] Emissive data flow traced and documented
- [x] Bug location identified (SpriteAtlasTextureMixin.continuity$onUpload)
- [x] Root cause understood (ThreadLocal context vs global storage mismatch)
- [x] Fix approach determined (Add fallback check)

---

## Task 2: Fix Emissive Rendering ✅ **COMPLETE**

### Objective
Implement missing emissive property usage to make glowing blocks render correctly.

### Solution Applied: Approach C - Model Wrapping Not Enabled

**Root Problem**: `SpriteAtlasTextureMixin` only checked `ThreadLocal` context for emissive detection, missing globally stored mappings.

### Implementation Details

#### Fix 1: SpriteLoaderMixin Fallback (continuity$modifyFunction)
```java
// PHASE 8: Fallback - try EmissiveIdMapStorage if no context
Map<Identifier, Identifier> storedEmissiveMap = EmissiveIdMapStorage.get(id);
if (storedEmissiveMap != null && !storedEmissiveMap.isEmpty()) {
    LOGGER.info("[Continuity] PHASE 8 FALLBACK: Using EmissiveIdMapStorage for atlas: {} ({} mappings)",
            id, storedEmissiveMap.size());
    return spriteContentsList -> {
        SpriteLoaderStitchContext.THREAD_LOCAL.set(new SpriteLoaderStitchContext() {
            @Override
            public Map<Identifier, Identifier> getEmissiveIdMap() {
                return storedEmissiveMap;
            }
            @Override
            public void markHasEmissives() {
                // No-op: no emissive control in fallback mode
            }
        });
        SpriteLoader.StitchResult result = function.apply(spriteContentsList);
        SpriteLoaderStitchContext.THREAD_LOCAL.set(null);
        return result;
    };
}
```

**Result**: Emissive sprites successfully attached to base sprites (verified in logs).

#### Fix 2: SpriteAtlasTextureMixin Fallback (continuity$onUpload)
```java
// Check if we have emissive textures for this atlas
SpriteLoaderStitchContext context = SpriteLoaderStitchContext.THREAD_LOCAL.get();
boolean hasEmissives = (context != null);

// PHASE 8: Fallback - check EmissiveIdMapStorage if no context
if (!hasEmissives) {
    java.util.Map<Identifier, Identifier> storedMap = EmissiveIdMapStorage.get(id);
    hasEmissives = (storedMap != null && !storedMap.isEmpty());
    if (hasEmissives) {
        LOGGER.info("[Continuity] PHASE 8: Emissive textures detected via EmissiveIdMapStorage for atlas: {} ({} mappings)",
                id, storedMap.size());
    }
}
```

**Result**: `ModelWrappingHandler.setInstance(wrapCtm: true, wrapEmissive: true)` now correctly enabled.

### Fix Verification

**Expected After Fix**:
```
[INFO] PHASE 8 FALLBACK: Using EmissiveIdMapStorage for atlas: blocks.png (19 mappings)
[INFO] EMISSIVE ASSIGNMENT: Attachment successful: true (×19 times)
[INFO] PHASE 8: Emissive textures detected via EmissiveIdMapStorage for atlas: blocks.png (19 mappings)
[INFO] ModelWrappingHandler instance created - wrapCtm: true, wrapEmissive: true
```

**In-Game Expected**:
- Soul Lantern: Glowing texture with emissive effect ✨
- Diamond Ore (CTM): Glowing ore veins
- Iron Ore (CTM): Glowing ore spots
- All emissive CTM textures render with glow

### Success Criteria
- [x] Emissive detection fixed with fallback mechanism
- [x] Model wrapping enabled for emissive textures
- [x] Sprite attachment verified in logs
- [x] Build successful
- [ ] In-game glow effect verified (needs testing)

---

## Task 3: Verify Animated Textures ❓ **MEDIUM - PRIORITY 3**

### Objective
Confirm animated CTM textures work correctly and animation system is not broken.

### Background
- Animation is Minecraft feature, not CTM-specific
- CTM only selects which sprite to display
- Animation should happen automatically on selected sprite
- Expected: Should work without special handling

### Test Plan

#### Step 1: Setup Test Environment
```
1. Create simple animated CTM configuration
2. Apply to cobblestone blocks (or similar)
3. Add test area in creative world
```

#### Step 2: Visual Inspection
```
Expected Behavior:
- Animation frames cycle smoothly
- Animation speed matches configuration
- All connection states cycle correctly
- No texture glitches or artifacts
```

#### Step 3: Verify Connection Logic
```
- Test stone CTM with connections
- Verify connected blocks show animation
- Check non-connected blocks show different animation
- Confirm animation independent of connection state
```

#### Step 4: Debug If Broken
```java
// If animation not working, add logging:
@Inject(method = "animationDone", ...)
private void traceAnimation(CallbackInfo ci) {
    LOGGER.debug("[Continuity] Animation frame update");
    LOGGER.debug("  Sprite: {}", spriteName);
    LOGGER.debug("  Frame: {}", currentFrame);
}
```

### Success Criteria
- [ ] Animation cycles smoothly
- [ ] Animation speed correct
- [ ] All frames display correctly
- [ ] No visual glitches
- [ ] Connection logic works with animation
- [ ] No crashes or errors

### Expected Outcome
✅ **Likely working** - Minecraft handles animation internally

### Estimated Time
**30 minutes** (mostly visual testing)

---

## Task 4: Comprehensive Testing & Documentation

### Objective
Verify all CTM features work together and document Phase 8 results.

### Testing Coverage

#### CTM Feature Testing
```
Feature                 | Test Case           | Expected | Status
Connected Textures      | Stone CTM           | Works    | [ ]
Glass Panes             | Glass CTM           | Works    | [ ]
Overlay                 | Overlay CTM         | Works    | [ ]
Random Texture          | Random CTM          | Works    | [ ]
Repeat Patterns         | Repeat CTM          | Works    | [ ]
Emissive (after fix)    | Soul Lanterns       | Glow     | [ ]
Animated                | Cobblestone CTM     | Animate  | [ ]
Custom Layers           | Block-specific      | Works    | [ ]
```

#### Resource Management Testing
```
Scenario                        | Expected      | Status
Initial World Load              | CTM visible   | [ ]
Manual Reload (F3+T)            | CTM visible   | [ ]
Resource Pack Switch            | Updates CTM   | [ ]
Missing Property Files          | No crash      | [ ]
Corrupted Property Files        | Handles error | [ ]
Multiple Resource Packs         | All load      | [ ]
Large CTM Project               | Loads OK      | [ ]
```

#### Performance Testing
```
Metric                  | Threshold   | Actual | Status
Startup Time            | <30s        | ?      | [ ]
Resource Reload Time    | <5s         | ?      | [ ]
Frame Rate (with CTM)   | No drop     | ?      | [ ]
Memory Usage            | Reasonable  | ?      | [ ]
CPU Usage               | Normal      | ?      | [ ]
```

### Documentation Required

#### 1. Phase 8 Completion Report
- [ ] Executive summary
- [ ] Issues fixed
- [ ] Issues remaining
- [ ] Test results
- [ ] Performance metrics
- [ ] Recommendations

#### 2. Status Updates
- [ ] Update QUICK START section (top of README/copilot-instructions.md)
- [ ] Update Phase status to "Phase 8: Runtime Polish (✅ COMPLETE)"
- [ ] Update current focus section

#### 3. Known Issues Document
- [ ] Create KNOWN_ISSUES.md if issues found
- [ ] List any remaining problems
- [ ] Provide workarounds if available
- [ ] Mark for future phases

#### 4. Architecture Summary
- [ ] Update ARCHITECTURE.md
- [ ] Document emissive rendering system
- [ ] Document animated texture handling
- [ ] Note any design decisions

### Success Criteria
- [ ] All CTM features tested and working
- [ ] Emissive textures fixed and verified
- [ ] Animated textures verified
- [ ] No critical bugs found
- [ ] Performance acceptable
- [ ] Documentation complete and accurate

### Estimated Time
**1 hour** (testing and documentation)

---

## Overall Phase 8 Summary

| Task | Priority | Complexity | Est. Time | Status |
|---|---|---|---|---|
| Debug emissive flow | 🔴 CRITICAL | HIGH | 1-2h | ✅ **Complete** |
| Fix emissive rendering | 🔴 CRITICAL | HIGH | 1-3h | ✅ **Complete** |
| Verify animated textures | 🟡 MEDIUM | LOW | 0.5h | ⏳ Pending |
| Test & document | 🟡 MEDIUM | MEDIUM | 1h | ⏳ Pending |
| **TOTAL** | | | **3.5-7h** | **50% Complete** |

### Changes Made

**Files Modified**:
1. `SpriteLoaderMixin.java` - Added fallback to read from `EmissiveIdMapStorage`
2. `SpriteAtlasTextureMixin.java` - Added fallback emissive detection for model wrapping
3. `EmissiveIdMapStorage.java` - (Already existed from Phase 7, no changes needed)

**Technical Details**:
- Emissive detection now works in both ThreadLocal and global storage modes
- Handles multi-threaded atlas loading gracefully
- Falls back to global storage when ThreadLocal context is unavailable
- Enables model wrapping when emissive textures detected via fallback

**Testing Status**:
- ✅ Build successful
- ✅ Logs show emissive detection working
- ✅ Sprite attachment verified
- ⏳ In-game glow effect verification pending

---

## Next Steps (After Phase 8)

### If Phase 8 Successful ✅
- [ ] Mark Phase 8 COMPLETE
- [ ] Start Phase 9: Final Documentation & Release Prep
- [ ] Create release notes
- [ ] Prepare for mod release

### If Issues Remain ❌
- [ ] Create Phase 8B for additional fixes
- [ ] Document workarounds
- [ ] Plan Phase 9 for resolution
- [ ] Set priorities for future work

---

## References

- **Phase 7 Report**: PHASE7_COMPLETION_REPORT.md (CTM initial load fix)
- **Phase 5 Analysis**: PHASE5_DELETED_FILES_ANALYSIS.md (deleted files mapping)
- **Critical Files Guide**: CRITICAL_FILES_GUIDE.md (file descriptions)
- **Copilot Instructions**: .github/copilot-instructions.md (project overview)

---

**Status**: 📋 **READY FOR PHASE 8 EXECUTION**  
**Next Action**: Start Task 1 - Debug Emissive Property Flow  
**Current Priority**: Fix emissive texture rendering  
**Timeline**: 3.5-7 hours estimated

---

*Created: November 9, 2025*  
*Phase: 8 - Emissive textures & Animated textures*  
*Following Phase 7 Success: CTM Initial Load SOLVED ✅*  
*Branch: phase3/minecraft-1.21.10-implementation*  
*Minecraft Version: 1.21.10*  
*Java Version: 21 (LTS)*
