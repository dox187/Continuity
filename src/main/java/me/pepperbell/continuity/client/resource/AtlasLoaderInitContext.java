package me.pepperbell.continuity.client.resource;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

public interface AtlasLoaderInitContext {
	ThreadLocal<AtlasLoaderInitContext> THREAD_LOCAL = new ThreadLocal<>();

	/**
	 * PHASE 7: Global context for cross-thread access during initial load. Set by SpriteLoaderMixin
	 * when supplier runs on Worker thread. Read by AtlasLoaderMixin.modifySources() on Render
	 * thread.
	 */
	AtomicReference<AtlasLoaderInitContext> GLOBAL_CONTEXT = new AtomicReference<>();

	@Nullable
	Set<Identifier> getExtraIds();
}
