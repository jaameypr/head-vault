package it.pruefert.headvault.catalog;

import java.util.List;
import java.util.UUID;

/**
 * A single custom head from the catalog. Pure data — no Minecraft types, so it is
 * fully unit-testable without a game runtime.
 *
 * @param name  display name (may be empty; never the unique key — duplicate names exist)
 * @param uuid  texture/profile UUID (the stable identifier)
 * @param value base64-encoded {@code textures} property used to build the player-head item
 * @param tags  search tags (the source's comma-separated string, split into a list; never null)
 */
public record Head(String name, UUID uuid, String value, List<String> tags) {

    public Head {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** A stable, human-usable id derived from the texture UUID (used by {@code /heads give}). */
    public String id() {
        return uuid.toString();
    }

    /** Lowercased haystack of name + tags for substring search. */
    public boolean matches(String lowerQuery) {
        if (name != null && name.toLowerCase().contains(lowerQuery)) {
            return true;
        }
        for (String tag : tags) {
            if (tag.toLowerCase().contains(lowerQuery)) {
                return true;
            }
        }
        return false;
    }
}
