package it.pruefert.headvault.access.villager;

import java.util.List;
import java.util.Locale;

/**
 * Decides which entity types may act as a named HeadVault trader. Pure — no Minecraft types — so
 * the mode logic stays unit-testable. Entity ids are normalized (trimmed, lower-cased, default
 * {@code minecraft:} namespace) before comparison.
 */
public enum TraderMode {
    ONLY_VILLAGER,
    ALL,
    MOB_WHITELIST,
    MOB_BLACKLIST;

    private static final String VILLAGER_ID = "minecraft:villager";

    /** Case-insensitive parse; null or unknown values fall back to {@link #ONLY_VILLAGER}. */
    public static TraderMode parse(String raw) {
        if (raw == null) {
            return ONLY_VILLAGER;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ONLY_VILLAGER;
        }
    }

    /** True if an entity of the given type id may be a named trader under this mode. */
    public boolean allows(String entityTypeId, List<String> whitelist, List<String> blacklist) {
        String id = normalize(entityTypeId);
        if (id == null) {
            return false;
        }
        return switch (this) {
            case ONLY_VILLAGER -> VILLAGER_ID.equals(id);
            case ALL -> true;
            case MOB_WHITELIST -> contains(whitelist, id);
            case MOB_BLACKLIST -> !contains(blacklist, id);
        };
    }

    private static boolean contains(List<String> ids, String normalizedId) {
        if (ids == null) {
            return false;
        }
        for (String entry : ids) {
            if (normalizedId.equals(normalize(entry))) {
                return true;
            }
        }
        return false;
    }

    /** Trim, lower-case, and prepend the default {@code minecraft:} namespace if none is present. */
    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return null;
        }
        return s.contains(":") ? s : "minecraft:" + s;
    }
}
