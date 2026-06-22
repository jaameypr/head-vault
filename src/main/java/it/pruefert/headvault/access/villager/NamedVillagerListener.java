package it.pruefert.headvault.access.villager;

import it.pruefert.headvault.compat.McEntities;
import it.pruefert.headvault.config.HeadVaultConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * The "named trader" access mode: any entity whose custom name matches the configured string opens
 * the shop for everyone on right-click (no permission needed). Which entity types qualify is
 * controlled by {@code access.villager.mode} (default ONLY_VILLAGER — vanilla villagers only). The
 * interaction handler cancels the vanilla GUI when this matches.
 */
public final class NamedVillagerListener {

    private NamedVillagerListener() {
    }

    public static boolean matches(Entity entity, HeadVaultConfig config) {
        HeadVaultConfig.Access.Villager v = config.access.villager;

        // 1. Name match first — cheap, rejects the common case before any registry lookup.
        Component customName = entity.getCustomName();
        if (customName == null) {
            return false;
        }
        if (v.name == null || v.name.isBlank()) {
            return false;
        }
        String actual = customName.getString();
        boolean nameOk = v.caseInsensitive ? actual.equalsIgnoreCase(v.name) : actual.equals(v.name);
        if (!nameOk) {
            return false;
        }

        // 2. Entity-type eligibility per configured mode.
        return TraderMode.parse(v.mode)
                .allows(McEntities.typeId(entity), v.entityWhitelist, v.entityBlacklist);
    }
}
