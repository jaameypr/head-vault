package it.pruefert.headvault.economy;

/**
 * How heads are paid for. Selected globally in config and optionally overridden per category.
 */
public enum EconomyMode {
    /** Heads are free. */
    FREE,
    /** Pay a configurable item id × amount per head. */
    ITEM,
    /** Pay experience levels per head. */
    XP_LEVELS,
    /** Pay total experience points per head. */
    XP_POINTS
}
