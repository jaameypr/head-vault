package it.pruefert.headvault.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/**
 * Version-sensitive entity-registry lookups and entity-flag setters. Part of the {@code compat}
 * package — the only place that should need editing when Minecraft moves registry/identifier or
 * entity APIs.
 */
public final class McEntities {

    private McEntities() {
    }

    /** The entity's registered type id, e.g. {@code "minecraft:villager"}. */
    public static String typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    /**
     * Mark an entity (in)vulnerable for good. 26.3 split invulnerability into a permanent flag and a
     * temporary hurt-cooldown, dropping {@code setInvulnerable}; shop NPCs need the permanent one.
     */
    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        //? if <26.3 {
        entity.setInvulnerable(invulnerable);
        //?} else {
        /*entity.setPermanentlyInvulnerable(invulnerable);
        *///?}
    }
}
