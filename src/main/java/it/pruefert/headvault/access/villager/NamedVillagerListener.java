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
        Component customName = entity.getCustomName();
        if (customName == null) {
            return false;
        }
        return matchesName(customName.getString(), McEntities.typeId(entity), config);
    }

    /**
     * Pure trader-match decision: does a candidate name on the given entity type qualify as a trader?
     * Used both at right-click time ({@link #matches}) and when a name tag is applied, to decide
     * whether the freeze hook should fire. Kept free of Minecraft types so it stays unit-testable.
     */
    public static boolean matchesName(String candidateName, String entityTypeId, HeadVaultConfig config) {
        HeadVaultConfig.Access.Villager v = config.access.villager;

        // 1. Name match first — cheap, rejects the common case before any registry lookup.
        if (candidateName == null || v.name == null || v.name.isBlank()) {
            return false;
        }
        boolean nameOk = v.caseInsensitive
                ? candidateName.equalsIgnoreCase(v.name)
                : candidateName.equals(v.name);
        if (!nameOk) {
            return false;
        }

        // 2. Entity-type eligibility per configured mode.
        return TraderMode.parse(v.mode).allows(entityTypeId, v.entityWhitelist, v.entityBlacklist);
    }
}
