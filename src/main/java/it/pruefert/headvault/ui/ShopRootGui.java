package it.pruefert.headvault.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.compat.HeadStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The root shop menu: one button per populated category, plus a search button. */
public final class ShopRootGui extends SimpleGui {

    private static final int PLAYER_SLOT = 25;
    private static final int SEARCH_SLOT = 26;

    public ShopRootGui(HeadVaultRuntime runtime, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, player, false);
        setTitle(GuiText.colored(runtime.config().ui.title, ChatFormatting.DARK_AQUA));

        List<HeadCategory> categories = runtime.catalog().populatedCategories();
        if (categories.isEmpty()) {
            setSlot(13, GuiElementBuilder.from(HeadStacks.icon("minecraft:barrier",
                    GuiText.colored("Catalog still loading", ChatFormatting.RED),
                    List.of(GuiText.lore("Try again in a moment")))).build());
        } else {
            int slot = 0;
            for (HeadCategory category : categories) {
                while (slot == PLAYER_SLOT || slot == SEARCH_SLOT) {
                    slot++;
                }
                setSlot(slot++, GuiElementBuilder.from(categoryIcon(runtime, category))
                        .setCallback((index, type, action, gui) -> runtime.openCategory(player, category))
                        .build());
            }
        }

        setSlot(PLAYER_SLOT, GuiElementBuilder.from(HeadStacks.icon("minecraft:player_head",
                        GuiText.name("Player Head"), List.of(GuiText.lore("Get a player's head by username"))))
                .setCallback((index, type, action, gui) -> runtime.openPlayerSearch(player))
                .build());

        setSlot(SEARCH_SLOT, GuiElementBuilder.from(HeadStacks.icon("minecraft:compass",
                        GuiText.name("Search"), List.of(GuiText.lore("Search all heads by name or tag"))))
                .setCallback((index, type, action, gui) -> runtime.openSearchInput(player))
                .build());
    }

    private static ItemStack categoryIcon(HeadVaultRuntime runtime, HeadCategory category) {
        List<Head> heads = runtime.catalog().heads(category);
        List<Component> lore = List.of(GuiText.lore(heads.size() + " heads"));
        if (!heads.isEmpty()) {
            return HeadStacks.playerHead(heads.get(0).value(), GuiText.name(category.displayName()), lore);
        }
        return HeadStacks.icon(category.fallbackIcon(), GuiText.name(category.displayName()), lore);
    }
}
