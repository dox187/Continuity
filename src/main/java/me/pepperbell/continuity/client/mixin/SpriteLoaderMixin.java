// TODO: MIGRATION BLOCKER - SpriteLoader.load() method signature changed in 1.21.10
//
// PROBLEM: The load() method no longer exists with the signature:
//   load(ResourceManager, Identifier, int, Executor, Collection) -> CompletableFuture
//
// This mixin is CRITICAL for emissive texture functionality. It hooks into sprite loading to:
//   1. Inject extra sprite IDs from CTM properties
//   2. Set up emissive texture mapping
//   3. Associate emissive sprites with their base sprites
//
// IMPACT: Without this mixin, emissive textures DO NOT WORK.
//
// INVESTIGATION NEEDED:
//   1. Find new SpriteLoader.load() method signature in 1.21.10
//   2. Check if sprite loading was moved to different class
//   3. Look for new sprite loading lifecycle hooks
//   4. Update all injection points to match new API
//
package me.pepperbell.continuity.client.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.texture.SpriteLoader;

@Mixin(SpriteLoader.class)
abstract class SpriteLoaderMixin {
// STUB: All injection points disabled - load() method signature changed
// This mixin is kept to preserve compilation but does nothing at runtime
// See TODO comment at top of file for details
}
