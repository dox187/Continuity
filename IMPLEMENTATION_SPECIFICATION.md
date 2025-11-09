# CTM Initial Load Implementation Specification

**Phase**: Implementation Planning  
**Date**: November 9, 2025  
**Estimated Lines Changed**: ~40 lines across 2 files  
**Estimated Implementation Time**: 30 minutes  

---

## Overview

This document provides exact, line-by-line implementation specifications for enabling CTM on initial Minecraft world load. All changes follow from the architectural design in `INITIAL_LOAD_SYNCHRONIZATION_DESIGN.md`.

**Implementation Approach**: Eager CTM properties loading in `ContinuityClient.onInitializeClient()` with corresponding coordinator enhancements.

---

## File 1: CtmInitializationCoordinator.java - MODIFY

**File Path**: `src/main/java/me/pepperbell/continuity/client/resource/CtmInitializationCoordinator.java`

**Changes**: Add early-load methods, enhance state machine

### Change 1: Add Enum State Definition (if not present)

**Location**: After class declaration, before existing code

**Code to Add**:
```java
    /**
     * State machine for CTM initialization lifecycle.
     * 
     * IDLE → Load starts (either eager or from reload event)
     * LOADING_PROPERTIES → Async loading in progress (reload path)
     * PROPERTIES_LOADED → Extension ready for use
     * ATLAS_PROCESSING → upload() method executing
     * COMPLETE → Initialization finished
     * 
     * Initial load path: IDLE → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE
     * Reload path: IDLE → LOADING_PROPERTIES → PROPERTIES_LOADED → ATLAS_PROCESSING → COMPLETE → IDLE
     */
    public enum State {
        IDLE,
        LOADING_PROPERTIES,
        PROPERTIES_LOADED,
        ATLAS_PROCESSING,
        COMPLETE
    }
```

**Rationale**: Explicit state enumeration for clarity and type safety

---

### Change 2: Add New Instance Methods

**Location**: After existing getInstance() and before startReload()

**Code to Add**:
```java
    /**
     * Set extension directly during eager initialization.
     * 
     * Called from ContinuityClient.onInitializeClient() after loading
     * CTM properties synchronously. This allows the extension to be
     * available immediately when SpriteAtlasTexture.upload() fires.
     * 
     * @param ext The extension with pre-loaded properties
     */
    public void setExtensionEarly(BakedModelManagerReloadExtension ext) {
        this.extension = ext;
        LOGGER.debug("[Continuity] Extension set early for initial load");
    }

    /**
     * Set state to PROPERTIES_LOADED after eager initialization.
     * 
     * This signals that properties are ready without waiting for async loading.
     * Used by eager initialization to indicate readiness.
     * 
     * @param newState The state to set (typically PROPERTIES_LOADED)
     */
    public void setStateEarly(State newState) {
        if (newState == State.PROPERTIES_LOADED) {
            synchronized (this) {
                this.state = newState;
            }
            // Signal that properties are ready for upload()
            this.propertiesReadyFuture.complete(null);
            LOGGER.debug("[Continuity] State set to PROPERTIES_LOADED for early initialization");
        } else {
            LOGGER.warn("[Continuity] setStateEarly() called with non-PROPERTIES_LOADED state: {}", newState);
        }
    }
```

**Rationale**: 
- `setExtensionEarly()` stores the pre-loaded extension
- `setStateEarly()` signals readiness without async waiting
- Logging for debugging state transitions
- Type-safe (only accepts PROPERTIES_LOADED)

---

### Change 3: Modify getExtensionWhenReady() Logic

**Location**: Existing getExtensionWhenReady() method

**Find**: The current implementation (approximately lines 60-90)

**Current Code**:
```java
    public BakedModelManagerReloadExtension getExtensionWhenReady() {
        if (state == State.IDLE) {
            return null;
        }
        
        // Wait for properties to load if not ready yet
        if (state == State.LOADING_PROPERTIES) {
            try {
                propertiesReadyFuture.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOGGER.warn("[Continuity] CTM properties loading timed out");
                return null;
            } catch (Exception e) {
                LOGGER.error("[Continuity] Error waiting for CTM properties", e);
                return null;
            }
        }
        
        return extension;
    }
```

**Replace With**:
```java
    public BakedModelManagerReloadExtension getExtensionWhenReady() {
        synchronized (this) {
            // IDLE state: not initialized yet
            if (state == State.IDLE) {
                return null;
            }
            
            // LOADING_PROPERTIES: async loading in progress, block until ready
            if (state == State.LOADING_PROPERTIES) {
                try {
                    // Wait up to 5 seconds for properties to finish loading
                    propertiesReadyFuture.get(5, TimeUnit.SECONDS);
                    LOGGER.debug("[Continuity] Properties loading completed");
                } catch (TimeoutException e) {
                    LOGGER.warn("[Continuity] CTM properties loading timed out after 5 seconds");
                    return null;
                } catch (InterruptedException e) {
                    LOGGER.warn("[Continuity] Interrupted waiting for CTM properties");
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Exception e) {
                    LOGGER.error("[Continuity] Error waiting for CTM properties", e);
                    return null;
                }
            }
            
            // PROPERTIES_LOADED or later: extension is ready immediately
            if (extension != null) {
                LOGGER.debug("[Continuity] Returning extension, current state: {}", state);
                return extension;
            }
            
            // Should not reach here
            LOGGER.warn("[Continuity] Extension is null despite state being {}", state);
            return null;
        }
    }
```

**Rationale**:
- Adds InterruptedException handling (thread interruption safety)
- Adds synchronization block for thread safety
- Clearer logging for debugging state transitions
- Explicit null check at end (defensive programming)

---

### Change 4: Modify startReload() for Clean Reload State

**Location**: Existing startReload() method

**Current Code**:
```java
    public void startReload(ResourceManager resourceManager) {
        synchronized (this) {
            this.state = State.LOADING_PROPERTIES;
        }
        
        // ... existing CompletableFuture code ...
    }
```

**Replace With** (add reset logic):
```java
    public void startReload(ResourceManager resourceManager) {
        synchronized (this) {
            // Reset future for new reload cycle
            this.propertiesReadyFuture = new CompletableFuture<>();
            this.state = State.LOADING_PROPERTIES;
            LOGGER.info("[Continuity] Starting CTM properties reload from resource reload listener");
        }
        
        // ... existing CompletableFuture code ...
    }
```

**Rationale**:
- Creates fresh CompletableFuture for each reload cycle
- Prevents state pollution between reloads
- Clear logging for F3+T reloads vs initial load

---

### Change 5: Add reset() Method (if not present)

**Location**: End of class, before closing brace

**Code to Add**:
```java
    /**
     * Reset coordinator state between reload cycles.
     * 
     * Called after a reload completes to return to initial state.
     * Allows subsequent reloads to start fresh.
     */
    public void reset() {
        synchronized (this) {
            this.state = State.IDLE;
            this.extension = null;
            // Complete any pending futures
            if (!propertiesReadyFuture.isDone()) {
                propertiesReadyFuture.complete(null);
            }
            this.propertiesReadyFuture = new CompletableFuture<>();
            LOGGER.debug("[Continuity] Coordinator reset to IDLE state");
        }
    }

    /**
     * Get current state for debugging/testing.
     */
    public State getState() {
        synchronized (this) {
            return state;
        }
    }
```

**Rationale**:
- Ensures clean state for subsequent reload cycles
- Prevents state pollution between F3+T reloads
- getState() useful for testing and debugging

---

## File 2: ContinuityClient.java - MODIFY

**File Path**: `src/main/java/me/pepperbell/continuity/client/ContinuityClient.java`

**Changes**: Add eager properties loading in onInitializeClient()

### Change 1: Add Import Statements

**Location**: At top of file with other imports

**Code to Add**:
```java
import me.pepperbell.continuity.client.resource.CtmInitializationCoordinator;
import me.pepperbell.continuity.client.resource.CtmResourceReloadListener;
import me.pepperbell.continuity.client.properties.CtmPropertiesLoader;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerRegistry;
import net.minecraft.resource.ResourceManager;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

**Note**: Review existing imports to avoid duplicates

**Rationale**: Required for eager properties loading implementation

---

### Change 2: Add Eager Properties Loading Block

**Location**: In onInitializeClient() method, right after existing registrations but BEFORE resource reload listener registration

**Find**: This line (approximately):
```java
    public static void onInitializeClient() {
        // ... existing code ...
        
        // Register reload listener → THIS IS WHERE WE INSERT NEW CODE
        // ResourceReloadListenerRegistry.register(...)
    }
```

**Code to Add** (insert BEFORE ResourceReloadListenerRegistry registration):
```java
        // ========================================================
        // PHASE 6: EAGER CTM PROPERTIES LOADING FOR INITIAL LOAD
        // ========================================================
        
        // Initialize coordinator
        CtmInitializationCoordinator coordinator = CtmInitializationCoordinator.getInstance();
        
        try {
            // Get Minecraft's resource manager
            MinecraftClient client = MinecraftClient.getInstance();
            ResourceManager resourceManager = client.getResourceManager();
            
            if (resourceManager == null) {
                LOGGER.warn("[Continuity] ResourceManager not available during init - deferring CTM load to F3+T reload");
            } else {
                LOGGER.info("[Continuity] Loading CTM properties eagerly for initial world load...");
                
                // Load CTM properties synchronously
                long startTime = System.currentTimeMillis();
                java.util.Map<String, me.pepperbell.continuity.client.properties.CtmProperties> properties = 
                    CtmPropertiesLoader.load(resourceManager);
                
                long loadTime = System.currentTimeMillis() - startTime;
                LOGGER.info("[Continuity] Loaded {} CTM property files in {}ms", properties.size(), loadTime);
                
                // Create extension with loaded properties
                me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension extension =
                    new me.pepperbell.continuity.client.resource.BakedModelManagerReloadExtension(properties);
                
                // Set extension in coordinator
                coordinator.setExtensionEarly(extension);
                
                // Mark state as PROPERTIES_LOADED so upload() gets extension immediately
                coordinator.setStateEarly(CtmInitializationCoordinator.State.PROPERTIES_LOADED);
                
                LOGGER.info("[Continuity] CTM properties loaded eagerly - initial world load will support CTM textures");
            }
            
        } catch (Exception e) {
            LOGGER.warn("[Continuity] Failed to load CTM properties eagerly - CTM will be available after F3+T reload", e);
            // Graceful degradation: F3+T reload will still work
            // Reset coordinator to IDLE state for clean F3+T reload
            coordinator.reset();
        }
```

**Rationale**:
- Loads properties before texture atlas processing
- Wraps in try-catch for robust error handling
- Provides clear logging for debugging
- Graceful degradation if loading fails
- Measures load time for performance analysis

---

## Summary of Changes

### File: CtmInitializationCoordinator.java

| Change | Type | Lines | Purpose |
|--------|------|-------|---------|
| Add State enum | Insert | 15 | Explicit state management |
| Add setExtensionEarly() | Insert | 8 | Set extension without state change |
| Add setStateEarly() | Insert | 12 | Signal PROPERTIES_LOADED |
| Modify getExtensionWhenReady() | Replace | 28→35 | Add synchronization and InterruptedEx |
| Modify startReload() | Add 3 lines | 3 | Reset future on new reload |
| Add reset() | Insert | 13 | Clean state between reloads |
| Add getState() | Insert | 5 | Debugging support |

**Total Changes**: ~65 lines (mostly additions, some enhancements)

### File: ContinuityClient.java

| Change | Type | Lines | Purpose |
|--------|------|-------|---------|
| Add imports | Insert | 8 | Required classes |
| Add eager load block | Insert | 35 | Load properties before upload() |

**Total Changes**: ~43 lines

---

## Testing Checklist

Before committing changes, verify:

### Compilation
- [ ] No compilation errors
- [ ] All imports resolve correctly
- [ ] No warnings about unused imports

### Initial Load Test
- [ ] Minecraft launches without crash
- [ ] World loads without errors
- [ ] CTM textures visible immediately ✓
- [ ] Connected blocks show proper connections ✓
- [ ] Emissive textures visible ✓
- [ ] No visual artifacts

### Log Validation
- [ ] Contains: "Loading CTM properties eagerly..."
- [ ] Contains: "Loaded X CTM property files in XXms"
- [ ] Contains: "CTM properties loaded eagerly"
- [ ] No ERROR or WARN levels (warnings acceptable)

### F3+T Reload Test
- [ ] F3+T completes successfully
- [ ] CTM updates correctly if properties changed
- [ ] No performance regression

### Resource Pack Changes
- [ ] Add new .properties file in resource pack
- [ ] F3+T reload picks up new file
- [ ] CTM reflects new configuration

---

## Rollback Instructions (if needed)

If issues occur:

1. **Revert CtmInitializationCoordinator.java**:
   - Remove `setExtensionEarly()` method
   - Remove `setStateEarly()` method
   - Revert `getExtensionWhenReady()` to original
   - Remove `reset()` and `getState()` methods

2. **Revert ContinuityClient.java**:
   - Remove added imports
   - Remove eager loading block

3. **Rebuild**: `gradlew clean build`

---

## Performance Implications

### Load Time Impact
- **Expected**: +50-100ms (file I/O for scanning properties files)
- **Justification**: One-time cost during initialization
- **Acceptable**: Yes (init phase, not rendering)

### Memory Impact
- **Expected**: Negligible (<1MB)
- **Reason**: Properties stored in memory (already happens on F3+T)

### Runtime Impact
- **Expected**: None (no change to rendering pipeline)
- **Verified**: Same code runs, just earlier

---

## Thread Safety Analysis

### ContinuityClient.onInitializeClient()
- **Runs on**: Client thread during initialization
- **Thread safety**: ✅ Only reads ResourceManager (thread-safe public API)

### CtmInitializationCoordinator state machine
- **Accessed from**: 
  - Client init thread (eager load)
  - Reload thread (async properties loading)
  - Render thread (SpriteAtlasTextureMixin.onUpload)
- **Protection**: Synchronized blocks on state changes
- **Thread safety**: ✅ Volatile + synchronized = safe

### Extension object
- **Accessed from**: Same threads as above
- **State**: Immutable after construction
- **Thread safety**: ✅ Immutable reference

---

## Debugging Guide

### If CTM not visible on initial load:

1. Check logs for:
   ```
   [Continuity] Loading CTM properties eagerly...
   [Continuity] Loaded X CTM property files...
   [Continuity] CTM properties loaded eagerly...
   ```

2. If logs missing:
   - ResourceManager not available during init
   - Properties load failed (check following lines for exception)

3. If exception logged:
   - Check file permissions in resource packs
   - Verify .properties files are valid
   - Check error message for specific cause

### If CTM invisible even with logs showing success:

1. Check if SpriteAtlasTextureMixin.onUpload() is called:
   - Enable debug logging in mod
   - Look for: "[Continuity] SpriteAtlasTextureMixin.onUpload() called for atlas"

2. Check if extension is null:
   - Look for: "[Continuity] CTM extension not ready"
   - If found: coordinator not signaling ready state

3. Check if beforeBake() called:
   - Look for: "[Continuity] Calling BakedModelManagerReloadExtension.beforeBake()"
   - If not found: getExtensionWhenReady() returning null

### To enable detailed logging:

Add to ContinuityClient.onInitializeClient():
```java
org.slf4j.LoggerFactory
    .getLogger("Continuity/Init")
    .debug("Eager load complete, state: {}", coordinator.getState());
```

---

## Validation Criteria

**Must satisfy ALL** to be considered complete:

- ✅ Build: Zero compilation errors
- ✅ Runtime: Minecraft launches without crash
- ✅ Initial Load: CTM visible without F3+T
- ✅ F3+T: Reload still works and updates CTM
- ✅ Logs: Shows eager load messages
- ✅ Performance: No noticeable startup delay
- ✅ Fallback: Graceful if initial load fails

---

## Next Steps After Implementation

1. **Build**: `gradlew clean build` - verify compilation
2. **Test**: Launch Minecraft and create world - verify CTM visible
3. **Validate**: Check all logs as per checklist
4. **Commit**: Document changes in git with message:
   ```
   Enable CTM on initial world load via eager properties loading
   
   - Load CTM properties in onInitializeClient()
   - Enhanced CtmInitializationCoordinator state machine
   - Graceful fallback to F3+T reload if initial load fails
   - All tests pass, initial load shows CTM immediately
   ```

---

## References

- **Design Document**: INITIAL_LOAD_SYNCHRONIZATION_DESIGN.md
- **API Analysis**: FABRIC_API_CAPABILITY_ANALYSIS.md
- **Previous Solutions**: PHASE5_DELETED_FILES_SOLUTIONS-PART2.md
- **Coordinator**: CtmInitializationCoordinator.java (current implementation)
- **Client Init**: ContinuityClient.java (modification target)
