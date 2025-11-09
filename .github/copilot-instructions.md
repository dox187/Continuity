# GitHub Copilot Instructions

## Project Overview
**Continuity** is a Fabric mod that enables OptiFine-compatible connected textures, emissive textures, and custom block layers without requiring OptiFine. It's a client-side Minecraft mod targeting Java 21.

### Build & Environment
- **Build Command**: `.\gradlew clean build` (Windows PowerShell 7.5)
- **Java Version**: 21 (configured in build.gradle)
- **Minecraft Version**: 1.21.10 (see `gradle.properties`)
- **Target Loader**: Fabric (works on NeoForge via Connector & Forgified Fabric API)
- **Changelog Location**: `.github/changelog/` - create markdown files for all code changes
- **Required Library sources**: Fabric api (1.21.6 & 1.21.10) and Yarn (1.21.6 & 1.21.10) are in the `.lib_src/`

## Architecture: Three-Layer Design

### 1. API Layer (`src/main/java/me/pepperbell/continuity/api/`)
Core interfaces that define extensibility points:
- **`CtmLoader<T>`** - Factory pattern for loading specific CTM methods (e.g., "ctm", "glass", "random")
- **`CtmProperties`** - Represents parsed `.properties` file data (tile amounts, connection rules)
- **`QuadProcessor`** - Processes Minecraft quads (mesh faces) to apply textures based on properties
- **`CachingPredicates`** - Optimizes per-block connection checks (e.g., "should this block connect to neighbor?")
- **`ProcessingDataProvider`** - Context for custom data sharing during quad processing

### 2. Implementation Layer (`src/main/java/me/pepperbell/continuity/impl/`)
Singleton implementations registered at startup:
- `CtmLoaderRegistryImpl` - Maps method names ("ctm", "overlay", etc.) to loaders
- `ProcessingDataKeyRegistryImpl` - Manages custom data keys for quad context
- Called during `ContinuityClient.onInitializeClient()` in initialization order

### 3. Client Layer (`src/main/java/me/pepperbell/continuity/client/`)
Runtime implementation & Minecraft integration:

**Core Initialization** (`ContinuityClient.java`):
- Registers 20+ CTM method loaders (standard, overlay, custom variants)
- Initializes biome system, model wrapping, and resource reloading
- Registers built-in resource packs ("default", "glass_pane_culling_fix")

**Properties Parsing** (`properties/`):
- `BaseCtmProperties` - Parses common properties (matchBlocks, texture, etc.)
- Subclasses: `ConnectingCtmProperties`, `RandomCtmProperties`, `RepeatCtmProperties`
- Validators: tile amount checks, dimension validation

**Quad Processing** (`processor/`):
- `SimpleQuadProcessor` - Base processor for tile selection logic
- `CompactCtmQuadProcessor` - Efficient 5-tile compact CTM algorithm
- Sprite providers: `CtmSpriteProvider` (47-tile CTM), `RandomSpriteProvider`, etc.
- **Data Flow**: BlockRenderView → connection predicates → sprite selection → quad texture replacement

**Resource Loading** (`resource/`):
- `CtmPropertiesLoader` - Loads `.properties` files from resource packs
- `ModelWrappingHandler` - Intercepts baked models during atlas loading

**Mixin Integration** (`mixin/`):
- `SpriteMixin` - Adds emissive sprite reference to Minecraft `Sprite`
- `SpriteLoaderMixin` - Injects CTM processing into texture atlas loading
- `RenderLayersMixin` - Applies custom block layers for emissive rendering

## Key Data Flows

### Resource Pack Loading
1. `ResourceManagerHelper` listens for resource reload events
2. `CtmPropertiesLoader` scans `optifine/ctm/` for `.properties` files
3. Parser creates appropriate `CtmProperties` subclass based on method name
4. `CtmLoader` registry retrieves corresponding processor factory
5. Processor instances cache predicates for fast per-block evaluation

### Quad Processing Pipeline
1. `SpriteLoaderMixin` intercepts sprite loading during atlas creation
2. For each quad in model render: `QuadProcessor.processQuad()` called
3. Processor evaluates neighbor connections using `CachingPredicates`
4. Based on connection pattern, selects appropriate texture sprite
5. Returns `NEXT_PROCESSOR` (chain next), `NEXT_PASS` (overlay), or `STOP` (done)

## Extension Points

### Adding a New CTM Method
1. Create properties class in `client/properties/` extending `BaseCtmProperties`
2. Create processor class in `client/processor/` implementing `QuadProcessor.Factory`
3. Create sprite provider in `client/processor/simple/` if needed
4. Register in `ContinuityClient.onInitializeClient()`:
```java
loader = createLoader(
    MyCtmProperties::new,
    new TileAmountValidator.Exactly<>(12),
    new MyQuadProcessor.Factory<>()
);
registry.registerLoader("mymethod", loader);
```

### Adding Custom Processing Data
1. Create key in `client/processor/ProcessingDataKeys`
2. Register via `ProcessingDataKeyRegistryImpl`
3. Access during processing: `context.get(myKey)`

## Dependencies & External APIs
- **Fabric API 0.138.0+** - Rendering hooks, resource manager, mixin support
- **Minecraft 1.21.10** - `Sprite`, `BlockRenderView`, `QuadView`, texture atlas APIs
- **Fabric Loom** - Remapping Yarn mappings to Intermediary, generating source jars
- **ModMenu** - Optional config screen integration via `ModMenuApiImpl`

## Testing & Validation
- Verify with `.\gradlew clean build` (compiles + creates JAR)
- Manual testing: place mod JAR in `.minecraft/mods/` with compatible Fabric setup
- Built-in resource packs for validating glass & bookshelf CTM
- Check mixin injection success in client log for "Loaded x mixins" message

## Common Patterns
- **Factory Pattern**: `CtmLoader<T>`, `QuadProcessor.Factory<T>` for pluggability
- **Mixin Interfaces**: `SpriteExtension` adds fields without bytecode complexity
- **Caching Predicates**: Connection checks cached per block to avoid redundant neighbor lookups
- **Multi-pass Rendering**: Overlay methods use `NEXT_PASS` result for secondary texture layer