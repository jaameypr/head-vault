package it.pruefert.headvault.permission;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * HeadVault permission nodes and the thin checks around fabric-permissions-api. With a permission
 * mod (e.g. LuckPerms) these resolve normally; with none installed, the int-overloads fall back to
 * the vanilla OP level, and the boolean-overloads to the given default — so the mod works sanely
 * out of the box.
 */
public final class Perms {

    /** Open the shop / run /heads. */
    public static final String COMMAND_USE = "headvault.command.use";
    /** Admin actions: /headvault reload, /heads give. */
    public static final String ADMIN = "headvault.admin";
    /** Manage NPCs: /headvault npc spawn|remove|list. */
    public static final String NPC_MANAGE = "headvault.npc.manage";
    /** Bypass economy cost (always free). */
    public static final String FREE_BYPASS = "headvault.free-bypass";
    /** Use /heads search. */
    public static final String COMMAND_SEARCH = "headvault.command.search";
    /** Use /heads give (also implied by {@link #ADMIN}). */
    public static final String COMMAND_GIVE = "headvault.command.give";

    private Perms() {
    }

    /** Brigadier {@code .requires(...)} predicate gated by a node, defaulting to an OP level. */
    public static Predicate<CommandSourceStack> require(String node, int defaultOpLevel) {
        return Permissions.require(node, defaultOpLevel);
    }

    /** Check a node for a command source, defaulting to an OP level when unresolved. */
    public static boolean check(CommandSourceStack source, String node, int defaultOpLevel) {
        return Permissions.check(source, node, defaultOpLevel);
    }

    /** Check a node for a player, defaulting to {@code def} when unresolved (no perms mod). */
    public static boolean check(ServerPlayer player, String node, boolean def) {
        return Permissions.check(player, node, def);
    }

    /** True if the player should always pay nothing. */
    public static boolean hasFreeBypass(ServerPlayer player) {
        return check(player, FREE_BYPASS, false);
    }
}
