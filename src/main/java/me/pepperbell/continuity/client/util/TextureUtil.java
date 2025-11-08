package me.pepperbell.continuity.client.util;

import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

public final class TextureUtil {
	public static final SpriteIdentifier MISSING_SPRITE_ID =
			toSpriteId(MissingSprite.getMissingSpriteId());

	public static SpriteIdentifier toSpriteId(Identifier id) {
		Identifier atlasId = determineAtlasForTexture(id);
		return new SpriteIdentifier(atlasId, id);
	}

	/**
	 * Determines which atlas a texture should belong to based on its path. Follows Minecraft's
	 * vanilla atlas assignment logic.
	 */
	private static Identifier determineAtlasForTexture(Identifier textureId) {
		String path = textureId.getPath();

		// Block and item textures go to blocks atlas
		if (path.startsWith("textures/block/") || path.startsWith("textures/item/")
				|| path.startsWith("block/") || path.startsWith("item/")) {
			return AtlasConstants.BLOCKS;
		}

		// Particle textures go to particles atlas
		if (path.startsWith("textures/particle/") || path.startsWith("particle/")) {
			return AtlasConstants.PARTICLES;
		}

		// GUI textures go to gui atlas
		if (path.startsWith("textures/gui/") || path.startsWith("gui/")) {
			return AtlasConstants.GUI;
		}

		// Painting textures go to paintings atlas
		if (path.startsWith("textures/painting/") || path.startsWith("painting/")) {
			return AtlasConstants.PAINTINGS;
		}

		// Entity textures go to various atlases based on entity type
		if (path.startsWith("textures/entity/chest/") || path.startsWith("entity/chest/")) {
			return AtlasConstants.CHESTS;
		}

		if (path.startsWith("textures/entity/signs/") || path.startsWith("entity/signs/")) {
			return AtlasConstants.SIGNS;
		}

		if (path.startsWith("textures/entity/bed/") || path.startsWith("entity/bed/")) {
			return AtlasConstants.BEDS;
		}

		if (path.startsWith("textures/entity/shulker/") || path.startsWith("entity/shulker/")) {
			return AtlasConstants.SHULKER_BOXES;
		}

		if (path.startsWith("textures/entity/banner/") || path.startsWith("entity/banner/")) {
			return AtlasConstants.BANNER_PATTERNS;
		}

		if (path.startsWith("textures/entity/shield/") || path.startsWith("entity/shield/")) {
			return AtlasConstants.SHIELD_PATTERNS;
		}

		// Armor trims go to armor_trims atlas
		if (path.startsWith("textures/trims/") || path.startsWith("trims/")) {
			return AtlasConstants.ARMOR_TRIMS;
		}

		// Map decorations go to map_decorations atlas
		if (path.startsWith("textures/map/") || path.startsWith("map/")) {
			return AtlasConstants.MAP_DECORATIONS;
		}

		// Decorated pot textures go to decorated_pot atlas
		if (path.startsWith("textures/entity/decorated_pot/")
				|| path.startsWith("entity/decorated_pot/")) {
			return AtlasConstants.DECORATED_POT;
		}

		// CTM and OptiFine textures typically go to blocks atlas
		if (path.contains("/ctm/") || path.startsWith("optifine/ctm/")
				|| path.contains("/connected_textures/") || path.startsWith("ctm/")) {
			return AtlasConstants.BLOCKS;
		}

		// Default fallback: blocks atlas (most textures are block-related)
		return AtlasConstants.BLOCKS;
	}

	public static boolean isMissingSprite(Sprite sprite) {
		return sprite.getContents().getId().equals(MissingSprite.getMissingSpriteId());
	}
}
