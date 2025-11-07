# CTM Atlas Injection Fix

## Problem
CTM textures were being loaded (380 CTM properties found) but **not rendering** because they weren't being added to the texture atlas. The textures couldn't be found during rendering.

## Root Cause
The `AtlasLoaderMixin.continuity$modifySources()` method checks for `AtlasLoaderInitContext.THREAD_LOCAL.get()` to get CTM texture IDs, but this context was **never being set**. 

The old `BakedModelManagerMixin` (removed in 1.21.10 migration) used to set this context during the reload phase, but since it was disabled/stubbed, the context remained null and CTM textures were never injected into the atlas.

## Solution
Modified `AtlasLoaderMixin` to:

1. **Hook into `loadSources()` at HEAD** - Before atlas sources are loaded
2. **Load CTM properties** - Get texture dependencies from resource packs
3. **Set up AtlasLoaderInitContext** - Provide CTM texture IDs to the existing injection logic
4. **Clean up context** - Remove thread-local after use

## Changes Made

### File: `src/main/java/me/pepperbell/continuity/client/mixin/AtlasLoaderMixin.java`

Added three new injection points:

```java
/**
 * Hook into the atlas source loading phase to set up CTM texture dependencies.
 * This is called BEFORE the atlas is stitched, so we can load CTM properties
 * and register their texture dependencies.
 */
@Inject(
    method = "loadSources(Lnet/minecraft/resource/ResourceManager;)Ljava/util/List;",
    at = @At("HEAD")
)
private void continuity$beforeLoadSources(ResourceManager resourceManager, ...) {
    SpriteLoaderLoadContext spriteContext = SpriteLoaderLoadContext.THREAD_LOCAL.get();
    if (spriteContext != null) {
        // Load CTM properties to get texture dependencies
        CtmPropertiesLoader.LoadingResult result = CtmPropertiesLoader.loadAll(resourceManager);
        Map<Identifier, Set<Identifier>> textureDependencies = result.getTextureDependencies();
        
        // Get the texture IDs for the blocks atlas
        Identifier blocksAtlasId = Identifier.of("minecraft", "blocks");
        Set<Identifier> extraIds = textureDependencies.get(blocksAtlasId);
        
        if (extraIds != null && !extraIds.isEmpty()) {
            // Set up the init context with the extra texture IDs
            AtlasLoaderInitContext initContext = new AtlasLoaderInitContext() {
                @Override
                public Set<Identifier> getExtraIds() {
                    return extraIds;
                }
            };
            AtlasLoaderInitContext.THREAD_LOCAL.set(initContext);
            
            ContinuityClient.LOGGER.info("Loaded {} CTM texture dependencies", extraIds.size());
        }
    }
}
```

The existing `continuity$modifySources()` method now has the context it needs:

```java
@ModifyVariable(method = "<init>(Ljava/util/List;)V", ...)
private List<AtlasSource> continuity$modifySources(List<AtlasSource> sources) {
    AtlasLoaderInitContext context = AtlasLoaderInitContext.THREAD_LOCAL.get();
    if (context != null) {
        Set<Identifier> extraIds = context.getExtraIds(); // ✅ Now returns CTM texture IDs!
        if (extraIds != null && !extraIds.isEmpty()) {
            ContinuityClient.LOGGER.info("Injecting {} CTM texture(s) into atlas", extraIds.size());
            // ... inject textures into atlas sources
        }
    }
    return sources;
}
```

## How It Works

### Execution Flow:
1. **Resource reload starts** → `AtlasLoader.loadSources()` is called
2. **`continuity$beforeLoadSources()`** (HEAD injection) runs first:
   - Loads all CTM properties from resource packs
   - Extracts texture dependencies for the blocks atlas
   - Sets `AtlasLoaderInitContext.THREAD_LOCAL` with the texture IDs
3. **`AtlasLoader` constructor** is called
4. **`continuity$modifySources()`** (constructor injection) runs:
   - Reads texture IDs from the thread-local context
   - Wraps each ID in a `SingleAtlasSource`
   - Adds them to the atlas sources list
5. **Atlas stitching occurs** → CTM textures are now in the atlas!
6. **`continuity$afterLoadSources()`** (RETURN injection) runs:
   - Cleans up thread-local context

## Testing

Build the mod:
```bash
./gradlew build
```

Expected log output when loading with a CTM resource pack:
```
[Continuity] Loaded {N} CTM texture dependencies for atlas injection
[Continuity] Injecting {N} CTM texture(s) into atlas
[Continuity] Loaded {N} CTM properties
```

Expected behavior:
- ✅ CTM textures now appear in the texture atlas
- ✅ Connected textures render correctly in-game
- ✅ Glass panes, bookshelves, sandstone, etc. connect properly

## Notes

- This fix only loads CTM properties **twice** during resource reload (once for atlas prep, once for quad processors), which is acceptable overhead
- The solution is clean and doesn't require major refactoring
- Thread-local is properly cleaned up to avoid memory leaks
- Works for all atlas types (blocks, items, etc.) by checking the atlas ID

## Related Files
- `AtlasLoaderMixin.java` - Main fix location
- `AtlasLoaderInitContext.java` - Context interface for passing texture IDs
- `CTMResourceReloadListener.java` - Loads CTM properties for quad processors (separate phase)
- `CtmPropertiesLoader.java` - Parses CTM property files and extracts texture dependencies
