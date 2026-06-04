package it.pruefert.headvault.economy;

/** Human-readable rendering of a {@link Cost} for lore and chat. Pure — unit-testable. */
public final class CostFormat {

    private CostFormat() {
    }

    public static String describe(Cost cost) {
        if (cost.isFree()) {
            return "Free";
        }
        return switch (cost.mode()) {
            case ITEM -> cost.amount() + "x " + prettyItem(cost.itemId());
            case XP_LEVELS -> cost.amount() + " level" + plural(cost.amount());
            case XP_POINTS -> cost.amount() + " xp";
            case FREE -> "Free";
        };
    }

    /** "minecraft:netherite_ingot" -> "netherite ingot". */
    static String prettyItem(String id) {
        int colon = id.indexOf(':');
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return path.replace('_', ' ');
    }

    private static String plural(int n) {
        return n == 1 ? "" : "s";
    }
}
