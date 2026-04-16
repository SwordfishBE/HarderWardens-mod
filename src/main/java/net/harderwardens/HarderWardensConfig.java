package net.harderwardens;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Locale;

/**
 * Mod configuration, stored at config/harder_wardens.json.
 *
 * Fields:
 *  difficulty           - EASY | NORMAL | HARD | NIGHTMARE | INSANE | CUSTOM
 *  customHealth         - Max HP for the Warden (CUSTOM only)
 *  customDamageMultiplier - Attack damage multiplier, e.g. 2.0 = double damage (CUSTOM only)
 *  customLootPreset     - Which loot preset to use for CUSTOM: EASY/NORMAL/HARD/NIGHTMARE/INSANE
 *  customXpReward       - XP reward for the Warden (CUSTOM only, max 100)
 */
public class HarderWardensConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double MIN_CUSTOM_HEALTH = 1.0;
    private static final double MAX_CUSTOM_HEALTH = 1024.0;
    private static final double MIN_CUSTOM_DAMAGE_MULTIPLIER = 0.1;
    private static final double MAX_CUSTOM_DAMAGE_MULTIPLIER = 100.0;

    // ── Config fields ─────────────────────────────────────────────────────────

    @SerializedName("difficulty")
    public String difficulty = "NORMAL";

    @SerializedName("customHealth")
    public double customHealth = 500.0;

    @SerializedName("customDamageMultiplier")
    public double customDamageMultiplier = 1.5;

    @SerializedName("customLootPreset")
    public String customLootPreset = "NORMAL";

    @SerializedName("customXpReward")
    public int customXpReward = 25;

    // ── Helpers ───────────────────────────────────────────────────────────────

    public Difficulty getDifficulty() {
        if (difficulty == null || difficulty.isBlank()) {
            HarderWardensMod.LOGGER.warn("{} Missing difficulty in config, falling back to NORMAL.",
                    HarderWardensMod.LOG_PREFIX);
            return Difficulty.NORMAL;
        }

        try {
            return Difficulty.valueOf(difficulty.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            HarderWardensMod.LOGGER.warn("{} Unknown difficulty '{}', falling back to NORMAL.",
                    HarderWardensMod.LOG_PREFIX, difficulty);
            return Difficulty.NORMAL;
        }
    }

    public DifficultySettings.LootPreset getCustomLootPreset() {
        return DifficultySettings.fromName(customLootPreset).lootPreset();
    }

    public int getClampedCustomXpReward() {
        return Math.clamp(customXpReward, 0, 100);
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
                    clampCustomHealth(customHealth),
                    clampCustomDamageMultiplier(customDamageMultiplier),
                    DifficultySettings.fromName(customLootPreset).lootPreset(),
                    getClampedCustomXpReward()
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
                JsonReader jsonReader = new JsonReader(reader);
                jsonReader.setLenient(true);
                HarderWardensConfig config = GSON.fromJson(jsonReader, HarderWardensConfig.class);
                if (config != null) {
                    config.sanitize();
                    HarderWardensMod.LOGGER.debug("{} Config loaded: difficulty={}",
                            HarderWardensMod.LOG_PREFIX, config.difficulty);
                    return config;
                }
            } catch (IOException | RuntimeException e) {
                HarderWardensMod.LOGGER.error("{} Failed to load config, using defaults.",
                        HarderWardensMod.LOG_PREFIX, e);
            }
        }

        HarderWardensConfig defaults = new HarderWardensConfig();
        defaults.sanitize();
        defaults.save();
        HarderWardensMod.LOGGER.debug("{} Default config created at {}",
                HarderWardensMod.LOG_PREFIX, configFile);
        return defaults;
    }

    /** Saves the current config to disk. */
    public void save() {
        sanitize();
        Path configFile = getConfigPath();
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, toCommentedJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HarderWardensMod.LOGGER.error("{} Failed to save config.", HarderWardensMod.LOG_PREFIX, e);
        }
    }

    private String toCommentedJson() {
        return """
                {
                  // Preset difficulty for newly spawned Wardens.
                  // Valid values: EASY, NORMAL, HARD, NIGHTMARE, INSANE, or CUSTOM.
                  "difficulty": "%s",

                  // Maximum Warden health.
                  // Only used when difficulty is CUSTOM.
                  "customHealth": %s,

                  // Multiplies the Warden's base attack damage.
                  // Only used when difficulty is CUSTOM.
                  "customDamageMultiplier": %s,

                  // Loot preset used when difficulty is CUSTOM.
                  // Valid values: NONE, EASY, NORMAL, HARD, NIGHTMARE, or INSANE.
                  "customLootPreset": "%s",

                  // XP reward dropped by the Warden.
                  // Only used when difficulty is CUSTOM.
                  // Values above 100 are clamped to 100.
                  "customXpReward": %s
                }
                """.formatted(
                difficulty,
                formatNumber(customHealth),
                formatNumber(customDamageMultiplier),
                customLootPreset,
                getClampedCustomXpReward()
        );
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void sanitize() {
        Difficulty difficultyValue = getDifficulty();
        difficulty = difficultyValue.name();

        customHealth = clampCustomHealth(customHealth);
        customDamageMultiplier = clampCustomDamageMultiplier(customDamageMultiplier);
        customLootPreset = DifficultySettings.fromName(customLootPreset).lootPreset().name();
        customXpReward = getClampedCustomXpReward();
    }

    private static double clampCustomHealth(double value) {
        if (!Double.isFinite(value)) {
            return 500.0;
        }
        return Math.clamp(value, MIN_CUSTOM_HEALTH, MAX_CUSTOM_HEALTH);
    }

    private static double clampCustomDamageMultiplier(double value) {
        if (!Double.isFinite(value)) {
            return 1.5;
        }
        return Math.clamp(value, MIN_CUSTOM_DAMAGE_MULTIPLIER, MAX_CUSTOM_DAMAGE_MULTIPLIER);
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("harder_wardens.json");
    }
}
