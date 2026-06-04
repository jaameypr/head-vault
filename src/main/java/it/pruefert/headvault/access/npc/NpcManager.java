package it.pruefert.headvault.access.npc;

import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.compat.GuiText;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages villager-based shop NPCs. NPCs are real villagers (so they persist in chunk NBT across
 * restarts automatically) identified by a scoreboard tag — no volatile world-storage API needed.
 * They are invulnerable, AI-less, and persistence-required; right-click opens the shop and the
 * vanilla trade GUI is suppressed by the interaction handler.
 */
public final class NpcManager {

    /** Scoreboard tag marking a villager as a HeadVault NPC (persists in entity NBT). */
    public static final String NPC_TAG = "headvault.npc";

    private final HeadVaultRuntime runtime;

    public NpcManager(HeadVaultRuntime runtime) {
        this.runtime = runtime;
    }

    public boolean isNpc(Entity entity) {
        return entity instanceof Villager && entity.entityTags().contains(NPC_TAG);
    }

    /** Right-click hook: open the shop if the entity is a HeadVault NPC. */
    public boolean tryOpen(ServerPlayer player, Entity entity) {
        if (isNpc(entity)) {
            runtime.openShop(player);
            return true;
        }
        return false;
    }

    /** Spawn a shop NPC at the admin's position. */
    public boolean spawn(ServerPlayer admin, String name) {
        ServerLevel level = admin.level();
        BlockPos pos = admin.blockPosition();
        Villager villager = EntityType.VILLAGER.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (villager == null) {
            return false;
        }
        villager.snapTo(admin.position(), admin.getYRot(), 0.0f);
        villager.setCustomName(GuiText.name(name));
        villager.setCustomNameVisible(true);
        villager.setInvulnerable(true);
        villager.setNoAi(true);
        villager.setPersistenceRequired();
        villager.addTag(NPC_TAG);
        return true;
    }

    /** All HeadVault NPCs currently loaded across all dimensions. */
    public List<Villager> listAll(MinecraftServer server) {
        List<Villager> out = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isNpc(entity)) {
                    out.add((Villager) entity);
                }
            }
        }
        return out;
    }

    /** The loaded NPC whose hitbox the admin's look ray hits first within {@code range}. */
    public Optional<Villager> findLookedAt(ServerPlayer admin, double range) {
        ServerLevel level = admin.level();
        Vec3 eye = admin.getEyePosition();
        Vec3 end = eye.add(admin.getLookAngle().scale(range));
        Villager best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity entity : level.getAllEntities()) {
            if (!isNpc(entity)) {
                continue;
            }
            // Inflate the hitbox slightly so casual aim still selects the NPC.
            AABB box = entity.getBoundingBox().inflate(0.3);
            Vec3 hit = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
            if (hit == null) {
                continue;
            }
            double distSq = eye.distanceToSqr(hit);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = (Villager) entity;
            }
        }
        return Optional.ofNullable(best);
    }
}
