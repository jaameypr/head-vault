package it.pruefert.headvault.economy;

/**
 * The resolved price for a concrete purchase (already multiplied by quantity). Pure data.
 *
 * @param mode   payment mode
 * @param itemId item id when {@code mode == ITEM} (else ignored)
 * @param amount item count, levels, or points depending on {@code mode}
 */
public record Cost(EconomyMode mode, String itemId, int amount) {

    public static Cost free() {
        return new Cost(EconomyMode.FREE, "", 0);
    }

    public boolean isFree() {
        return mode == EconomyMode.FREE || amount <= 0;
    }
}
