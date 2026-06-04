package it.pruefert.headvault.catalog;

import java.util.Optional;

/**
 * The ten head categories exposed by the minecraft-heads.com catalog. Pure data.
 *
 * <p>{@code slug} is the value passed to the API ({@code cat=<slug>}) and used as the cache
 * filename. {@code fallbackIcon} is a vanilla item id used for the category button only if no
 * head from the category is available to use as the icon (resolution happens in the UI/compat
 * layer, so no Minecraft type leaks in here).
 */
public enum HeadCategory {
    ALPHABET("alphabet", "Alphabet", "minecraft:oak_sign"),
    ANIMALS("animals", "Animals", "minecraft:wolf_spawn_egg"),
    BLOCKS("blocks", "Blocks", "minecraft:bricks"),
    DECORATION("decoration", "Decoration", "minecraft:flower_pot"),
    FOOD_DRINKS("food-drinks", "Food & Drinks", "minecraft:apple"),
    HUMANOID("humanoid", "Humanoid", "minecraft:armor_stand"),
    HUMANS("humans", "Humans", "minecraft:player_head"),
    MISCELLANEOUS("miscellaneous", "Miscellaneous", "minecraft:chest"),
    MONSTERS("monsters", "Monsters", "minecraft:zombie_head"),
    PLANTS("plants", "Plants", "minecraft:oak_sapling");

    private final String slug;
    private final String displayName;
    private final String fallbackIcon;

    HeadCategory(String slug, String displayName, String fallbackIcon) {
        this.slug = slug;
        this.displayName = displayName;
        this.fallbackIcon = fallbackIcon;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public String fallbackIcon() {
        return fallbackIcon;
    }

    public static Optional<HeadCategory> fromSlug(String slug) {
        for (HeadCategory category : values()) {
            if (category.slug.equalsIgnoreCase(slug)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
