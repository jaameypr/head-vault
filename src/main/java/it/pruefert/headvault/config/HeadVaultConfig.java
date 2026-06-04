package it.pruefert.headvault.config;

import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.economy.CategoryPricing;
import it.pruefert.headvault.economy.EconomyMode;
import it.pruefert.headvault.economy.PriceResolver;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The full HeadVault configuration, mapped 1:1 to {@code config/headvault/config.json} via Gson.
 * Public fields with defaults double as the documented schema; see the README for the reference.
 * Pure — no Minecraft types, so config parsing is unit-testable.
 */
public final class HeadVaultConfig {

    public String _comment = "HeadVault config. Full reference: https://github.com/jaameypr/head-vault#configuration";
    public int _schemaVersion = 1;

    public Catalog catalog = new Catalog();
    public Economy economy = new Economy();
    public Access access = new Access();
    public Ui ui = new Ui();
    public Logging logging = new Logging();

    // ── sections ────────────────────────────────────────────────────────────

    public static final class Catalog {
        /**
         * Source of head data: "v1" | "v2" | "bundled".
         * Default "v1": the no-token minecraft-heads.com endpoint that serves the full catalog.
         * Switch to "v2" if/when v1 is retired (requires a registered App UUID below).
         */
        public String source = "v1";
        public int refreshIntervalHours = 24;
        public int requestTimeoutSeconds = 15;
        public int maxRetries = 2;
        /** v2 only: your registered App UUID (required for licensed v2 access; blank = demo/none). */
        public String v2AppUuid = "";
        /** v2 only: override the endpoint URL. Blank uses the built-in default. Placeholders: {category}, {appUuid}. */
        public String v2UrlTemplate = "";
        /**
         * Sent as the User-Agent. minecraft-heads.com blocks non-browser agents (returns 403), so
         * the default mimics a browser; change only if you know the API accepts your agent.
         */
        public String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    }

    public static final class Economy {
        /** "FREE" | "ITEM" | "XP_LEVELS" | "XP_POINTS". */
        public String mode = "FREE";
        public Item item = new Item();
        public Xp xp = new Xp();
        /** Optional per-category overrides, keyed by category slug (e.g. "monsters"). */
        public Map<String, Override> categoryOverrides = new LinkedHashMap<>();

        public static final class Item {
            public String id = "minecraft:diamond";
            public int amountPerHead = 1;
        }

        public static final class Xp {
            public int amountPerHead = 1;
        }

        public static final class Override {
            public String mode;        // null = inherit global mode
            public Item item;          // null = inherit global item
            public Xp xp;              // null = inherit global xp
        }
    }

    public static final class Access {
        public Command command = new Command();
        public Npc npc = new Npc();
        public Villager villager = new Villager();

        public static final class Command {
            public boolean enabled = true;
            /** Vanilla OP level fallback for /heads when no permissions mod is installed. */
            public int permissionLevel = 2;
        }

        public static final class Npc {
            public boolean enabled = true;
        }

        public static final class Villager {
            public boolean enabled = true;
            public String name = "Head Trader";
            public boolean caseInsensitive = true;
        }
    }

    public static final class Ui {
        public String title = "HeadVault";
        public boolean showPriceInLore = true;
        public int headsPerPage = 45;
    }

    public static final class Logging {
        /** "OFF" | "INFO" | "DEBUG". */
        public String purchaseVerbosity = "INFO";
    }

    // ── derived (pure) views used by the rest of the mod ──────────────────────

    public EconomyMode economyMode() {
        return parseMode(economy.mode, EconomyMode.FREE);
    }

    public Duration refreshInterval() {
        return Duration.ofHours(Math.max(1, catalog.refreshIntervalHours));
    }

    public Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(1, catalog.requestTimeoutSeconds));
    }

    /** Build the {@link PriceResolver} from global pricing + per-category overrides. */
    public PriceResolver priceResolver() {
        CategoryPricing global = new CategoryPricing(
                economyMode(), economy.item.id, economy.item.amountPerHead, economy.xp.amountPerHead);

        Map<HeadCategory, CategoryPricing> overrides = new EnumMap<>(HeadCategory.class);
        for (Map.Entry<String, Economy.Override> entry : economy.categoryOverrides.entrySet()) {
            HeadCategory.fromSlug(entry.getKey()).ifPresent(category -> {
                Economy.Override ov = entry.getValue();
                EconomyMode mode = ov.mode != null ? parseMode(ov.mode, global.mode()) : global.mode();
                String itemId = ov.item != null ? ov.item.id : global.itemId();
                int itemAmount = ov.item != null ? ov.item.amountPerHead : global.itemAmount();
                int xpAmount = ov.xp != null ? ov.xp.amountPerHead : global.xpAmount();
                overrides.put(category, new CategoryPricing(mode, itemId, itemAmount, xpAmount));
            });
        }
        return new PriceResolver(global, overrides);
    }

    private static EconomyMode parseMode(String raw, EconomyMode fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return EconomyMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
