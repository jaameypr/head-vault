package it.pruefert.headvault.economy;

import it.pruefert.headvault.catalog.HeadCategory;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyMathTest {

    @Test
    void resolvesGlobalItemPriceWithQuantity() {
        PriceResolver resolver = new PriceResolver(
                new CategoryPricing(EconomyMode.ITEM, "minecraft:diamond", 2, 0), Map.of());
        Cost cost = resolver.resolve(HeadCategory.BLOCKS, 3);
        assertEquals(EconomyMode.ITEM, cost.mode());
        assertEquals("minecraft:diamond", cost.itemId());
        assertEquals(6, cost.amount());
    }

    @Test
    void perCategoryOverrideWins() {
        Map<HeadCategory, CategoryPricing> overrides = new EnumMap<>(HeadCategory.class);
        overrides.put(HeadCategory.MONSTERS, new CategoryPricing(EconomyMode.ITEM, "minecraft:netherite_ingot", 2, 0));
        PriceResolver resolver = new PriceResolver(
                new CategoryPricing(EconomyMode.ITEM, "minecraft:diamond", 1, 0), overrides);

        assertEquals(4, resolver.resolve(HeadCategory.MONSTERS, 2).amount());
        assertEquals("minecraft:netherite_ingot", resolver.resolve(HeadCategory.MONSTERS, 1).itemId());
        assertEquals("minecraft:diamond", resolver.resolve(HeadCategory.BLOCKS, 1).itemId());
    }

    @Test
    void freeModeIsAlwaysFree() {
        PriceResolver resolver = new PriceResolver(
                new CategoryPricing(EconomyMode.FREE, "", 0, 0), Map.of());
        assertEquals(0, resolver.resolve(HeadCategory.BLOCKS, 99).amount());
        org.junit.jupiter.api.Assertions.assertTrue(resolver.resolve(HeadCategory.BLOCKS, 5).isFree());
    }

    @Test
    void xpTotalPointsMatchesVanillaFormula() {
        assertEquals(0, XpMath.totalPointsForLevel(0));
        assertEquals(7, XpMath.totalPointsForLevel(1));
        assertEquals(91, XpMath.totalPointsForLevel(7));
        assertEquals(352, XpMath.totalPointsForLevel(16));
        assertEquals(394, XpMath.totalPointsForLevel(17));
        assertEquals(1628, XpMath.totalPointsForLevel(32));
    }

    @Test
    void xpPointsToNextLevel() {
        assertEquals(7, XpMath.pointsToNextLevel(0));
        assertEquals(37, XpMath.pointsToNextLevel(15));
        assertEquals(42, XpMath.pointsToNextLevel(16));
        assertEquals(121, XpMath.pointsToNextLevel(31));
    }

    @Test
    void xpLevelForTotalPointsIsInverse() {
        assertEquals(0, XpMath.levelForTotalPoints(0));
        assertEquals(1, XpMath.levelForTotalPoints(7));
        assertEquals(7, XpMath.levelForTotalPoints(91));
        assertEquals(16, XpMath.levelForTotalPoints(352));
        assertEquals(17, XpMath.levelForTotalPoints(394));
    }

    @Test
    void costFormatRendersEachMode() {
        assertEquals("Free", CostFormat.describe(Cost.free()));
        assertEquals("1x diamond", CostFormat.describe(new Cost(EconomyMode.ITEM, "minecraft:diamond", 1)));
        assertEquals("2x netherite ingot", CostFormat.describe(new Cost(EconomyMode.ITEM, "minecraft:netherite_ingot", 2)));
        assertEquals("3 levels", CostFormat.describe(new Cost(EconomyMode.XP_LEVELS, "", 3)));
        assertEquals("1 level", CostFormat.describe(new Cost(EconomyMode.XP_LEVELS, "", 1)));
        assertEquals("50 xp", CostFormat.describe(new Cost(EconomyMode.XP_POINTS, "", 50)));
    }
}
