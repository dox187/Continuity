# Logging Prefix Update

## Summary
Added `[Continuity]` prefix to all mod log messages for better identification in Minecraft logs.

## Changes Made

### 1. Added LOG_PREFIX Constant
**File**: `ContinuityClient.java`
- Added: `public static final String LOG_PREFIX = "[Continuity] ";`
- This provides a consistent prefix for all log messages

### 2. Updated All Logger Calls
Updated **16 Java files** to use the new prefix:
- AtlasLoaderMixin.java
- CTMResourceReloadListener.java
- CtmPropertiesLoader.java
- BiomeHolder.java
- CustomBlockLayers.java
- EmissiveSuffixLoader.java
- PropertiesParsingHelper.java
- BaseCtmProperties.java
- BasicConnectingCtmProperties.java
- CompactConnectingCtmProperties.java
- RandomCtmProperties.java
- RepeatCtmProperties.java
- TileAmountValidator.java
- OverlayPropertiesSection.java
- AbstractQuadProcessorFactory.java
- CompactCtmQuadProcessor.java

### 3. Log Message Format
All log messages now appear as:
```
[Continuity] Loading CTM properties for atlas preparation...
[Continuity] Loaded 868 CTM texture dependencies for atlas injection
[Continuity] Injecting 868 CTM texture(s) into atlas
[Continuity] Loaded 42 CTM properties
```

## About the "Unable to read property" Warnings

The warnings you saw:
```
[Render thread/WARN]: Unable to read property: west with value: "false" for blockstate...
```

These are **NOT from Continuity** - they come from Minecraft's own property parsing system when it tries to read blockstate properties from CTM resource packs. These warnings indicate that:

1. A CTM properties file is trying to match blockstates with specific properties
2. The blockstate matching logic might have issues with wall properties
3. This is likely an issue with the CTM resource pack's property files, not with Continuity itself

These warnings will remain without the `[Continuity]` prefix because they originate from Minecraft code, not Continuity code.

## Expected Log Output

With this update, you should now see:
```
[Worker-Main-7/INFO]: [Continuity] Loading CTM properties for atlas preparation...
[Worker-Main-4/INFO]: [Continuity] Loaded 868 CTM texture dependencies for atlas injection
[Worker-Main-2/INFO]: [Continuity] Loaded 868 CTM texture dependencies for atlas injection
[Render thread/INFO]: Created: 2048x1024x4 minecraft:textures/atlas/blocks.png-atlas
[Render thread/INFO]: [Continuity] Loaded 42 CTM properties
```

## Build Status
✅ Build successful - All changes compile correctly
✅ 8 actionable tasks: 5 executed, 3 up-to-date

## Date
November 7, 2025
