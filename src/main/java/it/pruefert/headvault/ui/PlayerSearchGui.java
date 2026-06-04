package it.pruefert.headvault.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.compat.HeadStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Anvil input to fetch a specific player's head by username. */
public final class PlayerSearchGui extends AnvilInputGui {

    private final HeadVaultRuntime runtime;
    private final ServerPlayer player;

    public PlayerSearchGui(HeadVaultRuntime runtime, ServerPlayer player) {
        super(player, false);
        this.runtime = runtime;
        this.player = player;
        setTitle(GuiText.colored("Player head", ChatFormatting.DARK_AQUA));
        setDefaultInputValue("");

        setSlot(0, GuiElementBuilder.from(HeadStacks.icon("minecraft:player_head",
                GuiText.name("Type a username"), List.of())).build());
        setSlot(2, GuiElementBuilder.from(HeadStacks.icon("minecraft:lime_dye",
                        GuiText.name("Get head"), List.of(GuiText.lore("Click to fetch the player's head"))))
                .setCallback((index, type, action, gui) -> fetch())
                .build());
    }

    private void fetch() {
        String name = getInput();
        if (name != null && !name.isBlank()) {
            // Don't close: searchPlayer opens the result GUI (replacing this one) on success.
            runtime.searchPlayer(player, name.trim());
        }
    }

    @Override
    public void onInput(String input) {
        // Resolution happens on confirm.
    }
}
