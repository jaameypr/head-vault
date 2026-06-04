package it.pruefert.headvault.compat;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Isolates right-click-on-entity handling. 26.1 restructured interaction events, so the binding to
 * {@link UseEntityCallback} (and the {@link InteractionResult} return values) lives here rather than
 * being scattered through the access code.
 */
public final class EntityInteractions {

    /** Server-side handler: return true to consume the interaction (cancels the vanilla GUI). */
    @FunctionalInterface
    public interface RightClickHandler {
        boolean onRightClick(ServerPlayer player, Entity entity);
    }

    private EntityInteractions() {
    }

    public static void register(RightClickHandler handler) {
        UseEntityCallback.EVENT.register((Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) -> {
            // Only the main hand, only real server players — avoids the off-hand double fire.
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            return handler.onRightClick(serverPlayer, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
}
