package it.pruefert.headvault.economy;

import it.pruefert.headvault.compat.McItems;
import it.pruefert.headvault.compat.PlayerEconomy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

/**
 * Performs the actual payment for an already-resolved {@link Cost}. The deduction is atomic per the
 * {@link PlayerEconomy} primitives — nothing is taken unless the full price can be paid.
 */
public final class EconomyService {

    /**
     * Attempt to pay {@code cost}.
     *
     * @param freeBypass true if the player has the free-bypass permission (always free)
     */
    public PurchaseResult charge(ServerPlayer player, Cost cost, boolean freeBypass) {
        if (freeBypass || cost.isFree()) {
            return PurchaseResult.free();
        }
        return switch (cost.mode()) {
            case ITEM -> {
                Item item = McItems.resolve(cost.itemId()).orElse(null);
                if (item == null) {
                    yield PurchaseResult.error("Unknown cost item: " + cost.itemId());
                }
                yield PlayerEconomy.takeItems(player, item, cost.amount())
                        ? PurchaseResult.success(cost) : PurchaseResult.insufficient(cost);
            }
            case XP_LEVELS -> PlayerEconomy.takeLevels(player, cost.amount())
                    ? PurchaseResult.success(cost) : PurchaseResult.insufficient(cost);
            case XP_POINTS -> PlayerEconomy.takePoints(player, cost.amount())
                    ? PurchaseResult.success(cost) : PurchaseResult.insufficient(cost);
            case FREE -> PurchaseResult.free();
        };
    }
}
