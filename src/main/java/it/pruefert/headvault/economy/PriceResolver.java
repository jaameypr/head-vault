package it.pruefert.headvault.economy;

import it.pruefert.headvault.catalog.HeadCategory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Resolves the {@link Cost} for buying a quantity of heads in a category, applying the global
 * pricing with optional per-category overrides. Pure — unit-tested without a Minecraft runtime.
 */
public final class PriceResolver {

    private final CategoryPricing global;
    private final Map<HeadCategory, CategoryPricing> overrides;

    public PriceResolver(CategoryPricing global, Map<HeadCategory, CategoryPricing> overrides) {
        this.global = global;
        this.overrides = new EnumMap<>(HeadCategory.class);
        if (overrides != null) {
            this.overrides.putAll(overrides);
        }
    }

    /** The effective pricing for a category (override if present, else global). */
    public CategoryPricing pricingFor(HeadCategory category) {
        return overrides.getOrDefault(category, global);
    }

    /** The cost to buy {@code quantity} heads from {@code category}. */
    public Cost resolve(HeadCategory category, int quantity) {
        return cost(pricingFor(category), quantity);
    }

    /** The cost using the global pricing (no category) — used for player-name heads. */
    public Cost resolveGlobal(int quantity) {
        return cost(global, quantity);
    }

    private static Cost cost(CategoryPricing pricing, int quantity) {
        if (pricing.mode() == EconomyMode.FREE) {
            return Cost.free();
        }
        int total = Math.multiplyExact(pricing.perHeadAmount(), Math.max(0, quantity));
        return new Cost(pricing.mode(), pricing.itemId(), total);
    }
}
