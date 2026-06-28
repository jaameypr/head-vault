package it.pruefert.headvault.access.villager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraderModeTest {

    @Test
    void parseIsCaseInsensitiveAndFallsBack() {
        assertEquals(TraderMode.ALL, TraderMode.parse("all"));
        assertEquals(TraderMode.MOB_WHITELIST, TraderMode.parse("  Mob_Whitelist "));
        assertEquals(TraderMode.ONLY_VILLAGER, TraderMode.parse(null));
        assertEquals(TraderMode.ONLY_VILLAGER, TraderMode.parse("garbage"));
    }

    @Test
    void onlyVillagerAllowsVillagerOnly() {
        assertTrue(TraderMode.ONLY_VILLAGER.allows("minecraft:villager", List.of(), List.of()));
        assertFalse(TraderMode.ONLY_VILLAGER.allows("minecraft:zombie", List.of(), List.of()));
    }

    @Test
    void allAllowsAnyType() {
        assertTrue(TraderMode.ALL.allows("minecraft:zombie", List.of(), List.of()));
        assertTrue(TraderMode.ALL.allows("somemod:robot", List.of(), List.of()));
    }

    @Test
    void whitelistAllowsListedOnly() {
        List<String> wl = List.of("minecraft:zombie", "skeleton");
        assertTrue(TraderMode.MOB_WHITELIST.allows("minecraft:zombie", wl, List.of()));
        assertTrue(TraderMode.MOB_WHITELIST.allows("minecraft:skeleton", wl, List.of()), "bare id matches");
        assertFalse(TraderMode.MOB_WHITELIST.allows("minecraft:creeper", wl, List.of()));
        assertFalse(TraderMode.MOB_WHITELIST.allows("minecraft:zombie", List.of(), List.of()),
                "empty whitelist allows nothing");
    }

    @Test
    void blacklistRejectsListedOnly() {
        List<String> bl = List.of("minecraft:creeper");
        assertFalse(TraderMode.MOB_BLACKLIST.allows("minecraft:creeper", List.of(), bl));
        assertTrue(TraderMode.MOB_BLACKLIST.allows("minecraft:villager", List.of(), bl));
        assertTrue(TraderMode.MOB_BLACKLIST.allows("minecraft:zombie", List.of(), List.of()),
                "empty blacklist allows all");
    }

    @Test
    void normalizationHandlesCaseAndNamespace() {
        assertTrue(TraderMode.MOB_WHITELIST.allows("Zombie", List.of("MINECRAFT:ZOMBIE"), List.of()));
    }
}
