package it.pruefert.headvault.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.compat.HeadStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Shows a resolved player's head as a clickable element, mirroring the catalog search results. */
public final class PlayerHeadResultGui extends SimpleGui {

    public PlayerHeadResultGui(HeadVaultRuntime runtime, ServerPlayer player, String display, ItemStack head) {
        super(MenuType.GENERIC_9x3, player, false);
        setTitle(GuiText.colored("Player: " + display, ChatFormatting.DARK_AQUA));

        setSlot(13, GuiElementBuilder.from(head)
                .setCallback((index, type, action, gui) -> runtime.deliverPlayerHead(player, head, display))
                .build());

        setSlot(18, GuiElementBuilder.from(HeadStacks.icon("minecraft:arrow",
                        GuiText.name("Back"), List.of(GuiText.lore("Search another player"))))
                .setCallback((index, type, action, gui) -> runtime.openPlayerSearch(player))
                .build());
    }
}
