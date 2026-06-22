package it.pruefert.headvault.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/**
 * Version-sensitive entity-registry lookups. Part of the {@code compat} package — the only place
 * that should need editing when Minecraft moves registry/identifier APIs.
 */
public final class McEntities {

    private McEntities() {
    }

    /** The entity's registered type id, e.g. {@code "minecraft:villager"}. */
    public static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }
}
