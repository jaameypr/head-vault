package it.pruefert.headvault;

import it.pruefert.headvault.catalog.CategorizedHead;
import it.pruefert.headvault.catalog.CatalogStore;
import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCatalog;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.catalog.refresh.CatalogRefreshService;
import it.pruefert.headvault.catalog.source.BundledSource;
import it.pruefert.headvault.catalog.source.HeadSource;
import it.pruefert.headvault.catalog.source.MinecraftHeadsV1Source;
import it.pruefert.headvault.catalog.source.MinecraftHeadsV2Source;
import com.mojang.authlib.GameProfile;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.compat.HeadStacks;
import it.pruefert.headvault.compat.PlayerEconomy;
import it.pruefert.headvault.compat.PlayerProfiles;
import it.pruefert.headvault.config.ConfigManager;
import it.pruefert.headvault.config.HeadVaultConfig;
import it.pruefert.headvault.economy.Cost;
import it.pruefert.headvault.economy.CostFormat;
import it.pruefert.headvault.economy.EconomyService;
import it.pruefert.headvault.economy.PriceResolver;
import it.pruefert.headvault.economy.PurchaseResult;
import it.pruefert.headvault.event.PurchaseEvent;
import it.pruefert.headvault.permission.Perms;
import it.pruefert.headvault.ui.HeadGridGui;
import it.pruefert.headvault.ui.PlayerHeadResultGui;
import it.pruefert.headvault.ui.PlayerSearchGui;
import it.pruefert.headvault.ui.SearchInputGui;
import it.pruefert.headvault.ui.ShopRootGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds HeadVault's live, swappable state (config, price resolver, catalog snapshot) and the
 * central shop operations used by every access mode. A {@code /headvault reload} rebuilds config
 * and the refresh service in place; the catalog reference is swapped atomically by the refresh
 * service off-thread.
 */
public final class HeadVaultRuntime {

    private static final int SEARCH_LIMIT = 1000;

    private final Logger log;
    private final ConfigManager configManager;
    private final CatalogStore store;

    private volatile HeadVaultConfig config;
    private volatile PriceResolver prices;
    private volatile HeadCatalog catalog = HeadCatalog.empty();

    private final EconomyService economy = new EconomyService();
    private CatalogRefreshService refresh;
    private MinecraftServer server;

    public HeadVaultRuntime(Path configDir, Logger log) {
        this.log = log;
        this.configManager = new ConfigManager(configDir, log);
        this.store = new CatalogStore(configDir.resolve("cache"));
        this.config = configManager.loadOrCreate();
        this.prices = config.priceResolver();
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        startRefresh();
    }

    public void onServerStopping() {
        if (refresh != null) {
            refresh.close();
            refresh = null;
        }
    }

    /** Reload config from disk and restart the refresh service in place. */
    public void reload() {
        this.config = configManager.loadOrCreate();
        this.prices = config.priceResolver();
        if (server != null) {
            if (refresh != null) {
                refresh.close();
            }
            startRefresh();
        }
        log.info("[HeadVault] Reloaded config (source={}, economy={})",
                config.catalog.source, config.economy.mode);
    }

    private void startRefresh() {
        Duration perCategoryTimeout = config.requestTimeout()
                .multipliedBy(Math.max(1, config.catalog.maxRetries + 1))
                .plusSeconds(5);
        refresh = new CatalogRefreshService(buildSource(), store, config.refreshInterval(),
                perCategoryTimeout, this::setCatalog, System::currentTimeMillis, log);
        refresh.start();
    }

    private HeadSource buildSource() {
        String userAgent = config.catalog.userAgent;
        Duration timeout = config.requestTimeout();
        int retries = config.catalog.maxRetries;
        return switch (config.catalog.source == null ? "bundled" : config.catalog.source.toLowerCase()) {
            case "v1" -> new MinecraftHeadsV1Source(timeout, retries, userAgent);
            case "v2" -> new MinecraftHeadsV2Source(timeout, retries, userAgent,
                    config.catalog.v2UrlTemplate, config.catalog.v2AppUuid);
            default -> new BundledSource();
        };
    }

    // ── accessors ───────────────────────────────────────────────────────────

    public HeadVaultConfig config() {
        return config;
    }

    public HeadCatalog catalog() {
        return catalog;
    }

    public PriceResolver prices() {
        return prices;
    }

    public MinecraftServer server() {
        return server;
    }

    public Logger log() {
        return log;
    }

    private void setCatalog(HeadCatalog catalog) {
        this.catalog = catalog;
    }

    /** Trigger an immediate off-thread catalog refresh (used by /headvault reload feedback). */
    public void refreshNow() {
        if (refresh != null) {
            refresh.refreshNow();
        }
    }

    // ── shop navigation ───────────────────────────────────────────────────────

    public void openShop(ServerPlayer player) {
        new ShopRootGui(this, player).open();
    }

    public void openCategory(ServerPlayer player, HeadCategory category) {
        List<CategorizedHead> heads = new ArrayList<>();
        for (Head head : catalog.heads(category)) {
            heads.add(new CategorizedHead(category, head));
        }
        new HeadGridGui(this, player, GuiText.colored(category.displayName(), ChatFormatting.DARK_AQUA),
                heads, () -> openShop(player)).open();
    }

    public void openSearchInput(ServerPlayer player) {
        new SearchInputGui(this, player).open();
    }

    public void openPlayerSearch(ServerPlayer player) {
        new PlayerSearchGui(this, player).open();
    }

    /** Resolve a username off-thread, then show its head as a clickable element in a result GUI. */
    public void searchPlayer(ServerPlayer player, String rawName) {
        String name = rawName.trim();
        if (name.isEmpty() || server == null) {
            return;
        }
        player.sendSystemMessage(GuiText.colored("Looking up '" + name + "'...", ChatFormatting.GRAY));
        PlayerProfiles.resolveAsync(server, name, profile -> {
            if (profile.isEmpty()) {
                player.sendSystemMessage(GuiText.colored("No player named '" + name + "'.", ChatFormatting.RED));
                return;
            }
            GameProfile gameProfile = profile.get();
            String display = gameProfile.name() != null && !gameProfile.name().isBlank()
                    ? gameProfile.name() : name;
            ItemStack head = HeadStacks.playerHead(gameProfile, GuiText.name(display + "'s Head"),
                    playerHeadLore());
            new PlayerHeadResultGui(this, player, display, head).open();
        });
    }

    /** Charge the global price (free-bypass aware) and give a copy of the player head on click. */
    public void deliverPlayerHead(ServerPlayer player, ItemStack head, String display) {
        boolean bypass = Perms.hasFreeBypass(player);
        PurchaseResult result = economy.charge(player, prices.resolveGlobal(1), bypass);
        if (!result.isSuccess()) {
            if (result.status() == PurchaseResult.Status.INSUFFICIENT_FUNDS) {
                player.sendSystemMessage(GuiText.colored("You can't afford " + display + "'s head ("
                        + CostFormat.describe(result.cost()) + ").", ChatFormatting.RED));
            } else {
                player.sendSystemMessage(GuiText.colored("Purchase failed: " + result.detail(), ChatFormatting.RED));
            }
            return;
        }
        PlayerEconomy.give(player, head.copy());
        String suffix = result.status() == PurchaseResult.Status.FREE
                ? "" : " for " + CostFormat.describe(result.cost());
        player.sendSystemMessage(GuiText.colored("Got " + display + "'s head" + suffix + ".", ChatFormatting.GREEN));
    }

    public void openSearchResults(ServerPlayer player, String query) {
        List<CategorizedHead> results = catalog.search(query, SEARCH_LIMIT);
        Component title = GuiText.colored("Search: " + query, ChatFormatting.DARK_AQUA);
        new HeadGridGui(this, player, title, results, () -> openShop(player)).open();
    }

    // ── purchase ───────────────────────────────────────────────────────────────

    /** Attempt to buy one head; sends feedback and returns whether it succeeded. */
    public boolean buy(ServerPlayer player, CategorizedHead ch) {
        boolean bypass = Perms.hasFreeBypass(player);
        PurchaseResult result = economy.charge(player, prices.resolve(ch.category(), 1), bypass);
        String name = displayName(ch.head());

        switch (result.status()) {
            case SUCCESS, FREE -> {
                try {
                    deliver(player, ch.head());
                } catch (RuntimeException e) {
                    // Payment was already taken; surface the failure instead of letting the GUI
                    // packet handler swallow it silently.
                    log.error("[HeadVault] Delivery FAILED after charging {} for '{}': {}",
                            player.getName().getString(), name, e.toString(), e);
                    player.sendSystemMessage(GuiText.colored(
                            "Purchase failed during delivery — contact an admin.", ChatFormatting.RED));
                    return false;
                }
                PurchaseEvent.EVENT.invoker().onPurchase(player, ch.category(), ch.head(), result.cost());
                logPurchase(player, ch, result);
                String suffix = result.status() == PurchaseResult.Status.FREE
                        ? "" : " for " + CostFormat.describe(result.cost());
                player.sendSystemMessage(GuiText.colored("Purchased " + name + suffix + ".", ChatFormatting.GREEN));
                return true;
            }
            case INSUFFICIENT_FUNDS -> {
                player.sendSystemMessage(GuiText.colored(
                        "You can't afford " + name + " (" + CostFormat.describe(result.cost()) + ").",
                        ChatFormatting.RED));
                return false;
            }
            default -> {
                player.sendSystemMessage(GuiText.colored(
                        "Purchase failed: " + result.detail(), ChatFormatting.RED));
                log.warn("[HeadVault] Purchase error for {}: {}", player.getName().getString(), result.detail());
                return false;
            }
        }
    }

    /** Admin give: deliver a head for free (used by /heads give). */
    public void giveHead(ServerPlayer target, Head head) {
        deliver(target, head);
    }

    private void deliver(ServerPlayer player, Head head) {
        ItemStack stack = HeadStacks.playerHead(head.value(), GuiText.name(displayName(head)), List.of());
        PlayerEconomy.give(player, stack);
    }

    private void logPurchase(ServerPlayer player, CategorizedHead ch, PurchaseResult result) {
        String verbosity = config.logging.purchaseVerbosity;
        if (verbosity == null || verbosity.equalsIgnoreCase("OFF")) {
            return;
        }
        log.info("[HeadVault] {} bought '{}' [{}] cost={}",
                player.getName().getString(), displayName(ch.head()), ch.category().slug(),
                result.status() == PurchaseResult.Status.FREE ? "free" : CostFormat.describe(result.cost()));
    }

    // ── helpers used by GUIs ────────────────────────────────────────────────────

    public String displayName(Head head) {
        return head.name() == null || head.name().isBlank() ? "Custom Head" : head.name();
    }

    /** Lore lines for a catalog head element: the per-category price and a click hint. */
    public List<Component> headLore(HeadCategory category) {
        return loreForCost(prices.resolve(category, 1), "Click to buy");
    }

    /** Lore lines for a player-name head element: the global price and a click hint. */
    public List<Component> playerHeadLore() {
        return loreForCost(prices.resolveGlobal(1), "Click to get");
    }

    private List<Component> loreForCost(Cost cost, String action) {
        List<Component> lore = new ArrayList<>();
        if (config.ui.showPriceInLore) {
            lore.add(GuiText.colored("Price: " + CostFormat.describe(cost), ChatFormatting.YELLOW));
        }
        lore.add(GuiText.colored(action, ChatFormatting.DARK_GRAY));
        return lore;
    }
}
