package me.pepperbell.continuity.client.resource;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

public interface AtlasLoaderLoadContext {
	ThreadLocal<AtlasLoaderLoadContext> THREAD_LOCAL = new ThreadLocal<>();

	void setEmissiveIdMap(@Nullable Map<Identifier, Identifier> map);

	@Nullable
	Map<Identifier, Identifier> getEmissiveIdMap();
}
