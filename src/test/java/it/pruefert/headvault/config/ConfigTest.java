package it.pruefert.headvault.config;

import it.pruefert.headvault.catalog.HeadCategory;
import it.pruefert.headvault.economy.EconomyMode;
import it.pruefert.headvault.economy.PriceResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private final Logger log = LoggerFactory.getLogger("test");

    @TempDir
    Path dir;

    @Test
    void writesDefaultsWhenMissing() {
        ConfigManager cm = new ConfigManager(dir, log);
        HeadVaultConfig config = cm.loadOrCreate();
        assertTrue(Files.exists(cm.configFile()), "default config file is created");
        assertEquals("v1", config.catalog.source);
        assertEquals(EconomyMode.FREE, config.economyMode());
        assertEquals("Head Trader", config.access.villager.name);
    }

    @Test
    void parsesCustomConfigAndBuildsResolver() throws IOException {
        String json = """
                {
                  "catalog": {"source":"v1","refreshIntervalHours":12},
                  "economy": {
                    "mode":"ITEM",
                    "item":{"id":"minecraft:diamond","amountPerHead":2},
                    "categoryOverrides": {
                      "monsters": {"mode":"ITEM","item":{"id":"minecraft:netherite_ingot","amountPerHead":3}}
                    }
                  }
                }
                """;
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.json"), json, StandardCharsets.UTF_8);

        HeadVaultConfig config = new ConfigManager(dir, log).loadOrCreate();
        assertEquals("v1", config.catalog.source);
        assertEquals(EconomyMode.ITEM, config.economyMode());

        PriceResolver resolver = config.priceResolver();
        assertEquals(2, resolver.resolve(HeadCategory.BLOCKS, 1).amount());
        assertEquals(3, resolver.resolve(HeadCategory.MONSTERS, 1).amount());
        assertEquals("minecraft:netherite_ingot", resolver.resolve(HeadCategory.MONSTERS, 1).itemId());
    }

    @Test
    void recoversFromCorruptConfig() throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.json"), "{ not valid json ]", StandardCharsets.UTF_8);
        ConfigManager cm = new ConfigManager(dir, log);
        HeadVaultConfig config = cm.loadOrCreate();
        assertEquals("v1", config.catalog.source, "falls back to defaults");
        assertTrue(Files.exists(dir.resolve("config.json.corrupt")), "corrupt file is backed up");
    }

    @Test
    void villagerTraderDefaultsToOnlyVillager() {
        HeadVaultConfig config = new ConfigManager(dir, log).loadOrCreate();
        assertEquals("ONLY_VILLAGER", config.access.villager.mode);
        assertTrue(config.access.villager.entityWhitelist.isEmpty());
        assertTrue(config.access.villager.entityBlacklist.isEmpty());
    }

    @Test
    void parsesTraderModeAndEntityLists() throws IOException {
        String json = """
                {
                  "access": {
                    "villager": {
                      "enabled": true,
                      "name": "Head Trader",
                      "mode": "MOB_WHITELIST",
                      "entityWhitelist": ["minecraft:zombie", "minecraft:skeleton"],
                      "entityBlacklist": ["minecraft:creeper"]
                    }
                  }
                }
                """;
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("config.json"), json, StandardCharsets.UTF_8);

        HeadVaultConfig config = new ConfigManager(dir, log).loadOrCreate();
        assertEquals("MOB_WHITELIST", config.access.villager.mode);
        assertEquals(2, config.access.villager.entityWhitelist.size());
        assertTrue(config.access.villager.entityWhitelist.contains("minecraft:zombie"));
        assertEquals(1, config.access.villager.entityBlacklist.size());
        assertTrue(config.access.villager.entityBlacklist.contains("minecraft:creeper"));
    }
}
