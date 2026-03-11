package net.harderwardens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Mod configuration, stored at config/harder_wardens.json.
 *
 * Fields:
 *  difficulty           - EASY | NORMAL | HARD | NIGHTMARE | INSANE | CUSTOM
 *  customHealth         - Max HP for the Warden (CUSTOM only)
 *  customDamageMultiplier - Attack damage multiplier, e.g. 2.0 = double damage (CUSTOM only)
 *  customLootPreset     - Which loot preset to use for CUSTOM: EASY/NORMAL/HARD/NIGHTMARE/INSANE
 */
public class HarderWardensConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Config fields ─────────────────────────────────────────────────────────

    @SerializedName("difficulty")
    public String difficulty = "NORMAL";

    @SerializedName("customHealth")
    public double customHealth = 500.0;

    @SerializedName("customDamageMultiplier")
    public double customDamageMultiplier = 1.5;

    @SerializedName("customLootPreset")
    public String customLootPreset = "NORMAL";

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Difficulty getDifficulty() {
        try {
            return Difficulty.valueOf(difficulty.toUpperCase());
        } catch (IllegalArgumentException e) {
            HarderWardensMod.LOGGER.warn("[HarderWardens] Unknown difficulty '{}', falling back to NORMAL.", difficulty);
            return Difficulty.NORMAL;
        }
    }

    /** Returns the active DifficultySettings based on the current config. */
    public DifficultySettings getSettings() {
        return switch (getDifficulty()) {
            case EASY      -> DifficultySettings.EASY;
            case NORMAL    -> DifficultySettings.NORMAL;
            case HARD      -> DifficultySettings.HARD;
            case NIGHTMARE -> DifficultySettings.NIGHTMARE;
            case INSANE    -> DifficultySettings.INSANE;
            case CUSTOM    -> new DifficultySettings(
                    customHealth,
                    customDamageMultiplier,
                    DifficultySettings.fromName(customLootPreset).lootPreset()
            );
        };
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    /** Loads config from disk, or generates a default config if none exists. */
    public static HarderWardensConfig load() {
        Path configFile = getConfigPath();

        if (Files.exists(configFile)) {
            try (Reader reader = new InputStreamReader(
                    new FileInputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
                HarderWardensConfig config = GSON.fromJson(reader, HarderWardensConfig.class);
                if (config != null) {
                    HarderWardensMod.LOGGER.info("[HarderWardens] Config loaded: difficulty={}", config.difficulty);
                    return config;
                }
            } catch (IOException e) {
                HarderWardensMod.LOGGER.error("[HarderWardens] Failed to load config, using defaults.", e);
            }
        }

        HarderWardensConfig defaults = new HarderWardensConfig();
        defaults.save();
        HarderWardensMod.LOGGER.info("[HarderWardens] Default config created at {}", configFile);
        return defaults;
    }

    /** Saves the current config to disk. */
    public void save() {
        Path configFile = getConfigPath();
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(configFile.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            HarderWardensMod.LOGGER.error("[HarderWardens] Failed to save config.", e);
        }
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("harder_wardens.json");
    }
}
