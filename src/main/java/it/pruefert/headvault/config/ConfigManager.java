package it.pruefert.headvault.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads, creates, saves, and hot-reloads {@code config/headvault/config.json}. The base directory
 * is injected so this is unit-testable without a Minecraft runtime. A corrupt config is backed up
 * and replaced with defaults rather than crashing the server.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path configFile;
    private final org.slf4j.Logger log;

    public ConfigManager(Path configDir, org.slf4j.Logger log) {
        this.configFile = configDir.resolve("config.json");
        this.log = log;
    }

    public Path configFile() {
        return configFile;
    }

    /** Load the config, writing defaults if the file is missing and recovering from corruption. */
    public HeadVaultConfig loadOrCreate() {
        if (!Files.isRegularFile(configFile)) {
            HeadVaultConfig defaults = new HeadVaultConfig();
            try {
                save(defaults);
                log.info("[HeadVault] Wrote default config to {}", configFile);
            } catch (IOException e) {
                log.warn("[HeadVault] Could not write default config: {}", e.toString());
            }
            return defaults;
        }

        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            HeadVaultConfig config = GSON.fromJson(json, HeadVaultConfig.class);
            if (config == null) {
                throw new JsonSyntaxException("Config parsed to null");
            }
            return config;
        } catch (IOException | JsonSyntaxException e) {
            log.warn("[HeadVault] Config is unreadable ({}); backing it up and using defaults", e.toString());
            backupCorrupt();
            HeadVaultConfig defaults = new HeadVaultConfig();
            try {
                save(defaults);
            } catch (IOException ignored) {
                // keep running with in-memory defaults
            }
            return defaults;
        }
    }

    public void save(HeadVaultConfig config) throws IOException {
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, GSON.toJson(config), StandardCharsets.UTF_8);
    }

    private void backupCorrupt() {
        try {
            Path backup = configFile.resolveSibling("config.json.corrupt");
            Files.deleteIfExists(backup);
            Files.move(configFile, backup);
        } catch (IOException e) {
            log.warn("[HeadVault] Could not back up corrupt config: {}", e.toString());
        }
    }
}
