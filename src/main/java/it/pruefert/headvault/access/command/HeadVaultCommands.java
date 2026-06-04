package it.pruefert.headvault.access.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.pruefert.headvault.HeadVaultRuntime;
import it.pruefert.headvault.access.npc.NpcManager;
import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.compat.GuiText;
import it.pruefert.headvault.permission.Perms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Registers {@code /heads} (player-facing) and {@code /headvault} (admin) commands. */
public final class HeadVaultCommands {

    private static final int ADMIN_OP_LEVEL = 2;

    private HeadVaultCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                HeadVaultRuntime runtime, NpcManager npcManager) {
        Predicate<CommandSourceStack> canUse = src ->
                Perms.check(src, Perms.COMMAND_USE, runtime.config().access.command.permissionLevel);
        Predicate<CommandSourceStack> canSearch = src ->
                Perms.check(src, Perms.COMMAND_SEARCH, runtime.config().access.command.permissionLevel);
        Predicate<CommandSourceStack> canGive = src ->
                Perms.check(src, Perms.COMMAND_GIVE, ADMIN_OP_LEVEL) || Perms.check(src, Perms.ADMIN, ADMIN_OP_LEVEL);

        dispatcher.register(Commands.literal("heads")
                .requires(src -> canUse.test(src) || canGive.test(src))
                .executes(ctx -> openShop(ctx.getSource(), runtime))
                .then(Commands.literal("search")
                        .requires(canSearch)
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(ctx -> search(ctx.getSource(), runtime,
                                        StringArgumentType.getString(ctx, "query")))))
                .then(Commands.literal("player")
                        .requires(canUse)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> playerHead(ctx.getSource(), runtime,
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("give")
                        .requires(canGive)
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("head", StringArgumentType.string())
                                        .executes(ctx -> give(ctx.getSource(), runtime,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "head")))))));

        dispatcher.register(Commands.literal("headvault")
                .then(Commands.literal("reload")
                        .requires(Perms.require(Perms.ADMIN, ADMIN_OP_LEVEL))
                        .executes(ctx -> reload(ctx.getSource(), runtime)))
                .then(Commands.literal("npc")
                        .requires(Perms.require(Perms.NPC_MANAGE, ADMIN_OP_LEVEL))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> npcSpawn(ctx.getSource(), npcManager,
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("remove")
                                .executes(ctx -> npcRemove(ctx.getSource(), npcManager)))
                        .then(Commands.literal("list")
                                .executes(ctx -> npcList(ctx.getSource(), npcManager)))));
    }

    // ── /heads ─────────────────────────────────────────────────────────────────

    private static int openShop(CommandSourceStack source, HeadVaultRuntime runtime) throws CommandSyntaxException {
        if (!runtime.config().access.command.enabled) {
            source.sendFailure(GuiText.colored("Command access to the head shop is disabled.", ChatFormatting.RED));
            return 0;
        }
        runtime.openShop(source.getPlayerOrException());
        return 1;
    }

    private static int search(CommandSourceStack source, HeadVaultRuntime runtime, String query)
            throws CommandSyntaxException {
        if (!runtime.config().access.command.enabled) {
            source.sendFailure(GuiText.colored("Command access to the head shop is disabled.", ChatFormatting.RED));
            return 0;
        }
        runtime.openSearchResults(source.getPlayerOrException(), query);
        return 1;
    }

    private static int playerHead(CommandSourceStack source, HeadVaultRuntime runtime, String name)
            throws CommandSyntaxException {
        if (!runtime.config().access.command.enabled) {
            source.sendFailure(GuiText.colored("Command access to the head shop is disabled.", ChatFormatting.RED));
            return 0;
        }
        runtime.searchPlayer(source.getPlayerOrException(), name);
        return 1;
    }

    private static int give(CommandSourceStack source, HeadVaultRuntime runtime, ServerPlayer target, String headId) {
        Optional<Head> head = runtime.catalog().byId(headId);
        if (head.isEmpty()) {
            source.sendFailure(GuiText.colored("No head with id: " + headId, ChatFormatting.RED));
            return 0;
        }
        runtime.giveHead(target, head.get());
        source.sendSuccess(() -> GuiText.colored(
                "Gave " + runtime.displayName(head.get()) + " to " + target.getName().getString() + ".",
                ChatFormatting.GREEN), true);
        return 1;
    }

    // ── /headvault ───────────────────────────────────────────────────────────

    private static int reload(CommandSourceStack source, HeadVaultRuntime runtime) {
        runtime.reload();
        source.sendSuccess(() -> GuiText.colored("HeadVault config reloaded.", ChatFormatting.GREEN), true);
        return 1;
    }

    private static int npcSpawn(CommandSourceStack source, NpcManager npcManager, String name)
            throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        if (npcManager.spawn(admin, name)) {
            source.sendSuccess(() -> GuiText.colored("Spawned shop NPC '" + name + "'.", ChatFormatting.GREEN), true);
            return 1;
        }
        source.sendFailure(GuiText.colored("Could not spawn the NPC here.", ChatFormatting.RED));
        return 0;
    }

    private static int npcRemove(CommandSourceStack source, NpcManager npcManager) throws CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        Optional<Villager> target = npcManager.findLookedAt(admin, 6.0);
        if (target.isEmpty()) {
            source.sendFailure(GuiText.colored("Look at a HeadVault NPC within 6 blocks to remove it.", ChatFormatting.RED));
            return 0;
        }
        target.get().discard();
        source.sendSuccess(() -> GuiText.colored("Removed shop NPC.", ChatFormatting.GREEN), true);
        return 1;
    }

    private static int npcList(CommandSourceStack source, NpcManager npcManager) {
        List<Villager> npcs = npcManager.listAll(source.getServer());
        if (npcs.isEmpty()) {
            source.sendSuccess(() -> GuiText.colored("No loaded HeadVault NPCs.", ChatFormatting.YELLOW), false);
            return 0;
        }
        source.sendSuccess(() -> GuiText.colored(npcs.size() + " loaded HeadVault NPC(s):", ChatFormatting.AQUA), false);
        for (Villager npc : npcs) {
            String name = npc.getCustomName() != null ? npc.getCustomName().getString() : "(unnamed)";
            String pos = String.format("%.0f, %.0f, %.0f", npc.getX(), npc.getY(), npc.getZ());
            source.sendSuccess(() -> GuiText.lore(" - " + name + " @ " + pos), false);
        }
        return npcs.size();
    }
}
