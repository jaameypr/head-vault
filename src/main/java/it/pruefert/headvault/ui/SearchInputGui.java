package it.pruefert.headvault.ui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.compat.HeadStacks;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Anvil text input for searching the catalog. Type a name/tag, click the confirm button. */
public final class SearchInputGui extends AnvilInputGui {

    private final HeadVaultRuntime runtime;
    private final ServerPlayer player;

    public SearchInputGui(HeadVaultRuntime runtime, ServerPlayer player) {
        super(player, false);
        this.runtime = runtime;
        this.player = player;
        setTitle(GuiText.colored("Search heads", ChatFormatting.DARK_AQUA));
        setDefaultInputValue("");

        setSlot(0, GuiElementBuilder.from(HeadStacks.icon("minecraft:paper",
                GuiText.name("Type a name or tag"), List.of())).build());
        setSlot(2, GuiElementBuilder.from(HeadStacks.icon("minecraft:lime_dye",
                        GuiText.name("Search"), List.of(GuiText.lore("Click to search"))))
                .setCallback((index, type, action, gui) -> runSearch())
                .build());
    }

    private void runSearch() {
        String query = getInput();
        if (query != null && !query.isBlank()) {
            runtime.openSearchResults(player, query.trim());
        }
    }

    @Override
    public void onInput(String input) {
        // No live filtering; search runs on confirm.
    }
}
