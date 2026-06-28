package it.pruefert.headvault.access.villager;

import it.pruefert.headvault.compat.McEntities;
import it.pruefert.headvault.compat.McItems;
import it.pruefert.headvault.config.HeadVaultConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Freezes a mob in place the moment a name tag turns it into a named trader, like a spawned NPC,
 * when {@code access.villager.freeze} is enabled. "Freeze" disables the mob's AI so it stops
 * wandering. The freeze is marked with a scoreboard tag so it can be cleanly reverted when the mob
 * is renamed to something that no longer qualifies — and so mobs we never froze are left untouched.
 */
public final class TraderFreeze {

    /** Scoreboard tag marking a trader frozen by HeadVault (persists in entity NBT). */
    public static final String FROZEN_TAG = "headvault.frozen";

    private TraderFreeze() {
    }

    /**
     * React to a name tag being applied to {@code mob}. Vanilla performs the actual rename; this
     * predicts the resulting name from the held item and freezes or unfreezes accordingly. Does
     * nothing when the held item carries no custom name (vanilla won't rename in that case).
     */
    public static void onNameTag(ServerPlayer player, Mob mob, HeadVaultConfig config) {
        String newName = McItems.mainHandCustomName(player).orElse(null);
        if (newName == null) {
            return;
        }
        boolean becomesTrader = NamedVillagerListener.matchesName(newName, McEntities.typeId(mob), config);
        if (becomesTrader && config.access.villager.freeze) {
            apply(mob);
        } else {
            clear(mob);
        }
    }

    /** Disable AI so the trader stays put, and tag the mob as frozen. */
    public static void apply(Mob mob) {
        mob.setNoAi(true);
        mob.addTag(FROZEN_TAG);
    }

    /** Revert a freeze applied by HeadVault. No-op on mobs we never froze. */
    public static void clear(Mob mob) {
        if (!mob.entityTags().contains(FROZEN_TAG)) {
            return;
        }
        mob.setNoAi(false);
        mob.removeTag(FROZEN_TAG);
    }
}
