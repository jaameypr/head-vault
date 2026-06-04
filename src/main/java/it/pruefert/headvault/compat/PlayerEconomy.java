package it.pruefert.headvault.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Minecraft-touching economy primitives (inventory + experience). The server runs single
 * threaded and purchases re-check cost at click time, so the count-then-remove sequence here is
 * effectively atomic. Isolated in {@code compat} because inventory/xp APIs are version-sensitive.
 */
public final class PlayerEconomy {

    private PlayerEconomy() {
    }

    public static int countItems(ServerPlayer player, Item item) {
        Inventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Remove {@code amount} of {@code item}; returns false (removing nothing) if too few. */
    public static boolean takeItems(ServerPlayer player, Item item, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (countItems(player, item) < amount) {
            return false;
        }
        Inventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(remaining, stack.getCount());
                inv.removeItem(i, take);
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    public static boolean takeLevels(ServerPlayer player, int levels) {
        if (levels <= 0) {
            return true;
        }
        if (player.experienceLevel < levels) {
            return false;
        }
        player.giveExperienceLevels(-levels);
        return true;
    }

    public static boolean takePoints(ServerPlayer player, int points) {
        if (points <= 0) {
            return true;
        }
        if (player.totalExperience < points) {
            return false;
        }
        player.giveExperiencePoints(-points);
        return true;
    }

    /** Give a stack, dropping the overflow at the player's feet if the inventory is full. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        // add() mutates the stack, leaving whatever did not fit. Drop the remainder so a full
        // inventory never silently swallows the purchase.
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
}
