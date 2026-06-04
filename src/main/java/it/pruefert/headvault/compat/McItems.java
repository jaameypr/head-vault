package it.pruefert.headvault.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Version-sensitive registry lookups. Part of the {@code compat} package — the only place that
 * should need editing when Minecraft moves registry/identifier APIs.
 */
public final class McItems {

    private McItems() {
    }

    /** Resolve an item id like {@code "minecraft:diamond"}; empty if unknown/malformed. */
    public static Optional<Item> resolve(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Identifier rl = Identifier.tryParse(id.trim());
        if (rl == null) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.getValue(rl);
        return item == Items.AIR ? Optional.empty() : Optional.of(item);
    }

    /** True if the player's main hand holds a name tag (used to let vanilla renaming take precedence). */
    public static boolean isHoldingNameTag(ServerPlayer player) {
        return player.getMainHandItem().getItem() == Items.NAME_TAG;
    }
}
