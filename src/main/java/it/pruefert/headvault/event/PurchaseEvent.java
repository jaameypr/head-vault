package it.pruefert.headvault.event;

import it.pruefert.headvault.catalog.Head;
import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.economy.Cost;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired after a head is successfully purchased and delivered. Other mods/datapack-driven mods can
 * listen to react to purchases (logging, rewards, analytics). Server-side, no packets.
 */
public final class PurchaseEvent {

    @FunctionalInterface
    public interface Listener {
        void onPurchase(ServerPlayer player, HeadCategory category, Head head, Cost cost);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> (player, category, head, cost) -> {
                for (Listener listener : listeners) {
                    listener.onPurchase(player, category, head, cost);
                }
            });

    private PurchaseEvent() {
    }
}
