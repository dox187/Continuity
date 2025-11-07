package me.pepperbell.continuity.client.util;

import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;

public final class TextureUtil {
	public static final SpriteIdentifier MISSING_SPRITE_ID = toSpriteId(MissingSprite.getMissingSpriteId());

	public static SpriteIdentifier toSpriteId(Identifier id) {
		// In 1.21.10, atlas IDs changed from "minecraft:textures/atlas/blocks.png" to "minecraft:blocks"
		return new SpriteIdentifier(Identifier.of("minecraft", "blocks"), id);
	}

	public static boolean isMissingSprite(Sprite sprite) {
		return sprite.getContents().getId().equals(MissingSprite.getMissingSpriteId());
	}
}
