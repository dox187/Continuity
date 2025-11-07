package me.pepperbell.continuity.client.resource;

import java.util.Map;

import net.minecraft.client.texture.AtlasManager;
import net.minecraft.util.Identifier;

public interface BakedModelManagerBakeContext {
	ThreadLocal<BakedModelManagerBakeContext> THREAD_LOCAL = new ThreadLocal<>();

	void beforeBake(Map<Identifier, AtlasManager.Metadata> atlases);
}
