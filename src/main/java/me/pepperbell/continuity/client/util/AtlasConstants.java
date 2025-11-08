package me.pepperbell.continuity.client.util;

import net.minecraft.util.Identifier;

/**
 * Constants for texture atlas identifiers used throughout Continuity.
 */
public final class AtlasConstants {

    // Standard Minecraft atlases
    public static final Identifier BLOCKS = Identifier.of("minecraft", "blocks");
    public static final Identifier PARTICLES = Identifier.of("minecraft", "particles");
    public static final Identifier GUI = Identifier.of("minecraft", "gui");
    public static final Identifier PAINTINGS = Identifier.of("minecraft", "paintings");
    public static final Identifier CHESTS = Identifier.of("minecraft", "chests");
    public static final Identifier SIGNS = Identifier.of("minecraft", "signs");
    public static final Identifier BEDS = Identifier.of("minecraft", "beds");
    public static final Identifier SHULKER_BOXES = Identifier.of("minecraft", "shulker_boxes");
    public static final Identifier BANNER_PATTERNS = Identifier.of("minecraft", "banner_patterns");
    public static final Identifier SHIELD_PATTERNS = Identifier.of("minecraft", "shield_patterns");
    public static final Identifier ARMOR_TRIMS = Identifier.of("minecraft", "armor_trims");
    public static final Identifier MAP_DECORATIONS = Identifier.of("minecraft", "map_decorations");
    public static final Identifier DECORATED_POT = Identifier.of("minecraft", "decorated_pot");

    private AtlasConstants() {
        // Utility class - no instantiation
    }
}
