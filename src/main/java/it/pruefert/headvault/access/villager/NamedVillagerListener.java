package it.pruefert.headvault.access.villager;

import it.pruefert.headvault.config.HeadVaultConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The "named villager" access mode: any vanilla villager whose custom name matches the configured
 * string opens the shop for everyone on right-click (no permission needed). The interaction handler
 * cancels the vanilla trade GUI when this matches.
 */
public final class NamedVillagerListener {

    private NamedVillagerListener() {
    }

    public static boolean matches(Entity entity, HeadVaultConfig config) {
        if (!(entity instanceof Villager)) {
            return false;
        }
        Component customName = entity.getCustomName();
        if (customName == null) {
            return false;
        }
        String actual = customName.getString();
        String target = config.access.villager.name;
        if (target == null || target.isBlank()) {
            return false;
        }
        return config.access.villager.caseInsensitive
                ? actual.equalsIgnoreCase(target)
                : actual.equals(target);
    }
}
