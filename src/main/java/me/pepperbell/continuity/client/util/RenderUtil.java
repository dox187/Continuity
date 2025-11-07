package me.pepperbell.continuity.client.util;

import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import me.pepperbell.continuity.client.ContinuityClient;
import me.pepperbell.continuity.client.mixinterface.AtlasManagerAccess;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.minecraft.client.render.BlockRenderLayer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;

public final class RenderUtil {
	private static final BlockColors BLOCK_COLORS = MinecraftClient.getInstance().getBlockColors();
	private static final BakedModelManager MODEL_MANAGER = MinecraftClient.getInstance().getBakedModelManager();

	private static SpriteFinder blockAtlasSpriteFinder;

	public static int getTintColor(@Nullable BlockState state, BlockRenderView blockView, BlockPos pos, int tintIndex) {
		if (state == null || tintIndex == -1) {
			return -1;
		}
		return 0xFF000000 | BLOCK_COLORS.getColor(state, blockView, pos, tintIndex);
	}

	/**
	 * Determines whether a block state can have ambient occlusion.
	 * Blocks with luminance (light-emitting) don't have AO.
	 */
	public static boolean canHaveAO(BlockState state) {
		return state.getLuminance() == 0;
	}

	public static SpriteFinder getSpriteFinder() {
		return blockAtlasSpriteFinder;
	}

	public static class ReloadListener implements SimpleSynchronousResourceReloadListener {
		public static final Identifier ID = ContinuityClient.asId("render_util");
		public static final List<Identifier> DEPENDENCIES = List.of(ResourceReloadListenerKeys.MODELS);
		private static final ReloadListener INSTANCE = new ReloadListener();

		public static void init() {
			ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(INSTANCE);
		}

		@Override
		public void reload(ResourceManager manager) {
			// Access AtlasManager through mixin interface
			AtlasManager atlasManager = ((AtlasManagerAccess) MODEL_MANAGER).continuity$getAtlasManager();
			
			// Get the block atlas texture
			// In 1.21.10, atlas IDs changed from "minecraft:textures/atlas/blocks.png" to "minecraft:blocks"
			SpriteAtlasTexture blockAtlas = atlasManager.getAtlasTexture(Identifier.of("minecraft", "blocks"));
			
			// Create sprite finder from the atlas
			if (blockAtlas != null) {
				blockAtlasSpriteFinder = SpriteFinder.get(blockAtlas);
			} else {
				blockAtlasSpriteFinder = null;
			}
		}

		@Override
		public Identifier getFabricId() {
			return ID;
		}

		@Override
		public Collection<Identifier> getFabricDependencies() {
			return DEPENDENCIES;
		}
	}
}
