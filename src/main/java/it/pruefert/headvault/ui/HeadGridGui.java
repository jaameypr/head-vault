package it.pruefert.headvault.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.catalog.CategorizedHead;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.compat.HeadStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;

import java.util.List;

/**
 * Paginated grid of heads (used for both a single category and search results). A 9×6 chest: the
 * top 45 slots are head buttons, the bottom row is navigation. Pagination is hand-rolled because
 * sgui has no built-in paged gui.
 */
public final class HeadGridGui extends SimpleGui {

    private static final int NAV_ROW = 45;
    private static final int CONTENT_SLOTS = 45;

    private final HeadVaultRuntime runtime;
    private final ServerPlayer player;
    private final List<CategorizedHead> heads;
    private final MutableComponent baseTitle;
    private final Runnable back;
    private final int pageSize;
    private int page;

    public HeadGridGui(HeadVaultRuntime runtime, ServerPlayer player, Component title,
                       List<CategorizedHead> heads, Runnable back) {
        super(MenuType.GENERIC_9x6, player, false);
        this.runtime = runtime;
        this.player = player;
        this.heads = heads;
        this.baseTitle = title.copy();
        this.back = back;
        this.pageSize = Math.max(9, Math.min(CONTENT_SLOTS, runtime.config().ui.headsPerPage));
        this.page = 0;
        render();
    }

    private int pageCount() {
        return Math.max(1, (heads.size() + pageSize - 1) / pageSize);
    }

    private void render() {
        int pages = pageCount();
        page = Math.max(0, Math.min(page, pages - 1));
        setTitle(baseTitle.copy().append(GuiText.colored(
                "  (" + (page + 1) + "/" + pages + ")", ChatFormatting.GRAY)));

        for (int i = 0; i < 54; i++) {
            clearSlot(i);
        }

        int start = page * pageSize;
        for (int i = 0; i < pageSize && i < CONTENT_SLOTS; i++) {
            int idx = start + i;
            if (idx >= heads.size()) {
                break;
            }
            CategorizedHead ch = heads.get(idx);
            var stack = HeadStacks.playerHead(ch.head().value(),
                    GuiText.name(runtime.displayName(ch.head())), runtime.headLore(ch.category()));
            setSlot(i, GuiElementBuilder.from(stack)
                    .setCallback((index, type, action, gui) -> runtime.buy(player, ch))
                    .build());
        }

        setNav(NAV_ROW, "minecraft:arrow", "Back", back);
        if (page > 0) {
            setNav(NAV_ROW + 3, "minecraft:spectral_arrow", "Previous page", () -> {
                page--;
                render();
            });
        }
        setSlot(NAV_ROW + 4, GuiElementBuilder.from(HeadStacks.icon("minecraft:paper",
                GuiText.name("Page " + (page + 1) + " / " + pages),
                List.of(GuiText.lore(heads.size() + " heads")))).build());
        if (page < pages - 1) {
            setNav(NAV_ROW + 5, "minecraft:spectral_arrow", "Next page", () -> {
                page++;
                render();
            });
        }
    }

    private void setNav(int slot, String itemId, String name, Runnable action) {
        setSlot(slot, GuiElementBuilder.from(HeadStacks.icon(itemId, GuiText.name(name), List.of()))
                .setCallback((index, type, action2, gui) -> action.run())
                .build());
    }
}
