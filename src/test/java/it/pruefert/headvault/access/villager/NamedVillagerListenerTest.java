package it.pruefert.headvault.access.villager;

import it.pruefert.headvault.config.HeadVaultConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pure trader-match decision used both at right-click time and when a name tag is applied
 * (the freeze hook). Entity-bound paths are exercised in-game; this verifies the name + type logic.
 */
class NamedVillagerListenerTest {

    private static HeadVaultConfig config() {
        return new HeadVaultConfig();
    }

    @Test
    void matchesDefaultNameOnVillager() {
        assertTrue(NamedVillagerListener.matchesName("Head Trader", "minecraft:villager", config()));
    }

    @Test
    void rejectsWrongName() {
        assertFalse(NamedVillagerListener.matchesName("Bob", "minecraft:villager", config()));
    }

    @Test
    void rejectsNullName() {
        assertFalse(NamedVillagerListener.matchesName(null, "minecraft:villager", config()));
    }

    @Test
    void caseInsensitiveByDefault() {
        assertTrue(NamedVillagerListener.matchesName("head trader", "minecraft:villager", config()));
    }

    @Test
    void caseSensitiveWhenConfigured() {
        HeadVaultConfig c = config();
        c.access.villager.caseInsensitive = false;
        assertFalse(NamedVillagerListener.matchesName("head trader", "minecraft:villager", c));
        assertTrue(NamedVillagerListener.matchesName("Head Trader", "minecraft:villager", c));
    }

    @Test
    void rejectsNonVillagerInDefaultMode() {
        assertFalse(NamedVillagerListener.matchesName("Head Trader", "minecraft:zombie", config()));
    }

    @Test
    void allowsWhitelistedMobType() {
        HeadVaultConfig c = config();
        c.access.villager.mode = "MOB_WHITELIST";
        c.access.villager.entityWhitelist = List.of("minecraft:zombie");
        assertTrue(NamedVillagerListener.matchesName("Head Trader", "minecraft:zombie", c));
        assertFalse(NamedVillagerListener.matchesName("Head Trader", "minecraft:creeper", c));
    }

    @Test
    void rejectsWhenConfigNameBlank() {
        HeadVaultConfig c = config();
        c.access.villager.name = "";
        assertFalse(NamedVillagerListener.matchesName("Head Trader", "minecraft:villager", c));
    }
}
