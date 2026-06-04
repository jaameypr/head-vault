package it.pruefert.headvault.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text construction helpers, kept in {@code compat} because the chat-component API is
 * version-sensitive. All helpers force italics off (item custom names/lore render italic by
 * default) for a clean shop look.
 */
public final class GuiText {

    private GuiText() {
    }

    public static MutableComponent plain(String text) {
        return Component.literal(text).withStyle(style -> style.withItalic(false));
    }

    public static MutableComponent colored(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color).withStyle(style -> style.withItalic(false));
    }

    public static MutableComponent name(String text) {
        return colored(text, ChatFormatting.WHITE);
    }

    public static MutableComponent lore(String text) {
        return colored(text, ChatFormatting.GRAY);
    }
}
