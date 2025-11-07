# Comprehensive Analysis & Solution Brainstorm

**Date:** November 7, 2025

This document provides an exhaustive analysis of the rendering issues in the Continuity mod and presents a wide range of potential solutions, from API-intended paths to experimental alternatives.

---

## 1. Core Problem Analysis

My deep analysis, including a review of the mixin configurations and model loading workarounds, has confirmed two independent problems:

1.  **CTM Failure:** The `ThreadLocal` context strategy fails because the injection point (`BlockModelRenderer.render`) is bypassed by the optimized chunk builder. A race condition in the resource loader vs. model baker has also been identified and worked around in `BlockModelsMixin`, confirming the complexity of this issue.
2.  **Emissive Texture Failure:** The mechanism for loading emissive sprites into the texture atlas (`SpriteLoaderMixin`) is disabled, and no replacement has been registered.

---

## 2. Brainstorming: CTM World Context Solutions

Here are several potential solutions to the CTM world context problem, ordered from most to least recommended.

### Idea #1: The "Correct" Fabric API Path (Highly Recommended)

**Concept:** Stop fighting the vanilla render pipeline and fully embrace the Fabric Rendering API. The `ThreadLocal` hack is unnecessary if the API is used as intended.

**Implementation:**
1.  Modify `CtmBlockStateModel` to implement `net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel`.
2.  In `CtmBlockStateModel`, implement the `isVanillaAdapter()` method and have it return `false`. This is critical, as it tells Fabric's renderer that this model is dynamic and its quads should not be cached globally.
3.  Move all CTM logic into the `emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context)` method. This method provides the **full world context** needed for CTM, directly as parameters.
4.  Inside `emitBlockQuads`, get the base quads from the wrapped vanilla model and use the provided `context.emitter()` to process and emit the CTM-modified quads.

**Pros:**
*   This is the officially supported, intended way to create dynamic models.
*   Eliminates the fragile `ThreadLocal` hack entirely.
*   Robust and less likely to break with future Minecraft updates.

**Cons:**
*   May have a slight performance cost because `emitBlockQuads` is called for every single block, but this is the necessary trade-off for context-awareness.

### Idea #2: The Model Provider Path

**Concept:** A cleaner way to inject custom models than the current `ModelWrappingHandler` workaround. This is complementary to Idea #1.

**Implementation:**
1.  Create a `ModelResourceProvider` that listens for model requests.
2.  When a block that needs CTM is requested (e.g., `minecraft:glass`), this provider returns a custom `UnbakedModel`.
3.  This custom `UnbakedModel`'s `bake()` method is responsible for creating the `CtmBlockStateModel` (which should also be a `FabricBakedModel` as per Idea #1).

**Pros:**
*   Integrates into the model loading system at an earlier, more official stage.
*   Removes the need for the `BlockModelsMixin` workaround.

### Idea #3: The `BlockRenderManager` Hook (Fallback)

**Concept:** If Idea #1 proves unworkable for some reason, this is a more robust version of the current `ThreadLocal` strategy.

**Implementation:**
1.  Create a mixin for `BlockRenderManager`.
2.  Inject code at the `HEAD` of `renderBlock()` to set the `CtmRenderContext` `ThreadLocal`.
3.  Inject code at the `RETURN` of `renderBlock()` to clear the `ThreadLocal`.

**Pros:**
*   `BlockRenderManager.renderBlock()` is a more general method that is more likely to be on the chunk building path.

**Cons:**
*   It is still a workaround that relies on implementation details of the vanilla renderer.

### Idea #4: The Post-Processing Path (Experimental)

**Concept:** Let the chunk mesh build normally, then modify the resulting vertex data before it's sent to the GPU.

**Implementation:**
1.  Find a hook after the chunk vertex buffer is generated (e.g., in `ChunkBuilder.BuiltChunk.upload`).
2.  Read the vertex data from the buffer (this can be slow).
3.  Identify which vertices belong to which blocks (highly complex).
4.  Calculate new UV coordinates based on CTM logic.
5.  Write the modified data back to the buffer.

**Pros:**
*   Avoids all model system complexities.

**Cons:**
*   Extremely complex, likely very slow, and exceptionally fragile.

### Idea #5: The Cache-Busting Path (Theoretical)

**Concept:** Force Minecraft to use a different model for each block position by making the model cache key position-dependent.

**Implementation:**
1.  Use heavy-handed mixins to replace the `BlockModels.modelMap` with a custom, position-aware cache (e.g., `Map<BlockState, Map<BlockPos, BlockStateModel>>`).
2.  This would trigger a new model bake for every unique block position.

**Pros:**
*   Guarantees context is available at bake time.

**Cons:**
*   Would cause an enormous explosion in memory usage and baking time, making it completely impractical.

---

## 3. Brainstorming: Emissive Texture Solutions

### Idea #6: The "Correct" Fabric API Path (Highly Recommended)

**Concept:** Use the modern, event-based Fabric API for registering extra sprites.

**Implementation:**
1.  Create a manager class (`EmissiveTextureManager`) that implements `SimpleSynchronousResourceReloadListener`.
2.  In its `reload()` method, scan all resources, call `EmissiveSuffixLoader.load()`, and find all emissive texture variants (`_e.png`), storing their `Identifier`s in a `Set`.
3.  In `ContinuityClient.onInitializeClient()`, register this listener. Also, register a callback for `ClientSpriteRegistryCallback.event(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)`.
4.  The callback's code will simply call a method on the manager to loop through the stored `Set` and register each `Identifier`.

**Pros:**
*   The intended, modern, and robust way to add sprites to the atlas.
*   Integrates perfectly with existing `EmissiveSuffixLoader` logic.

### Idea #7: The Runtime Resource Pack Path

**Concept:** Programmatically create and register a virtual resource pack that contains the emissive textures.

**Implementation:**
1.  Create a class that implements `ResourcePack`.
2.  This class would need to hold an in-memory map of virtual file paths to the actual resource data for all `_e.png` files.
3.  In `onInitializeClient`, use `ResourceManagerHelper.registerBuiltinResourcePack` to add this virtual pack to the game.

**Pros:**
*   A clean, self-contained solution that uses a stable API.

**Cons:**
*   Significantly more complex to implement than the simple callback in Idea #6.

### Idea #8: The Core Shader Path

**Concept:** Handle emissiveness in the GPU shader, avoiding the need to load separate textures into the atlas.

**Implementation:**
1.  Create a custom block `RenderLayer` and an associated GLSL shader program.
2.  The shader would sample from the main block texture, and also a second "emissive mask" texture (e.g., `_m.png`).
3.  Where the mask is white, the shader would output the texture color at full brightness; otherwise, it would perform standard lighting calculations.
4.  Use mixins to force blocks onto this custom render layer.

**Pros:**
*   Extremely powerful and flexible.
*   Decouples emissiveness from the texture atlas entirely.

**Cons:**
*   Requires knowledge of GLSL and Minecraft's shader system. A very large undertaking.

### Idea #9: The Vanilla Glow Effect Path

**Concept:** A variation of the shader idea that attempts to reuse vanilla's glowing entity effect.

**Implementation:**
1.  Investigate the `RenderLayer.getOutline()` and the shaders used for the Glowing status effect.
2.  Attempt to create a custom `RenderLayer` for blocks that replicates this effect, but instead of rendering an outline, it renders the full texture at max brightness.

**Pros:**
*   May be simpler than writing a shader from scratch if the vanilla effect is reusable.

**Cons:**
*   The glowing effect is designed for entity outlines and may not be easily adaptable for full-block emissiveness.

### Idea #10: The Direct Atlas Manipulation Path (Not Recommended)

**Concept:** After the atlas is created, forcibly inject new texture data into it.

**Implementation:**
1.  Use a mixin on `SpriteAtlasTexture.upload()`.
2.  Get the `NativeImage` object representing the atlas texture.
3.  For each emissive sprite, load its `NativeImage` and manually copy its pixel data into an empty area of the main atlas image.
4.  Update the sprite's UV coordinates to point to this new location.

**Pros:**
*   Gives absolute low-level control.

**Cons:**
*   Extremely fragile, complex, and prone to breaking with any minor Minecraft update. This is fighting the system at the lowest level.

---

# Conversation Archive

(Previous conversation archive remains here)