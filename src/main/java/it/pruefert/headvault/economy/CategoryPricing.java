package it.pruefert.headvault.economy;

/**
 * Per-unit pricing for one scope (the global default or a single category override). Pure data.
 *
 * @param mode       payment mode for this scope
 * @param itemId     item id used when {@code mode == ITEM}
 * @param itemAmount item count per head when {@code mode == ITEM}
 * @param xpAmount   levels or points per head when {@code mode == XP_LEVELS / XP_POINTS}
 */
public record CategoryPricing(EconomyMode mode, String itemId, int itemAmount, int xpAmount) {

    /** Cost per single head for this scope. */
    public int perHeadAmount() {
        return switch (mode) {
            case FREE -> 0;
            case ITEM -> itemAmount;
            case XP_LEVELS, XP_POINTS -> xpAmount;
        };
    }
}
