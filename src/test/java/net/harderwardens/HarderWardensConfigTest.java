package net.harderwardens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HarderWardensConfigTest {
    @TempDir Path directory;

    @Test
    void malformedConfigIsNotOverwritten() throws Exception {
        Path file = directory.resolve("config.json");
        String broken = "{\"difficulty\": \"INSANE\", broken";
        Files.writeString(file, broken);
        assertEquals(Difficulty.NORMAL, HarderWardensConfig.load(file).getDifficulty());
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void emptyConfigIsNotOverwritten() throws Exception {
        Path file = directory.resolve("config.json");
        Files.writeString(file, "");
        HarderWardensConfig.load(file);
        assertEquals("", Files.readString(file));
    }

    @Test
    void customValuesSurviveSaveAndReload() throws Exception {
        Path file = directory.resolve("config.json");
        HarderWardensConfig config = new HarderWardensConfig();
        config.difficulty = "CUSTOM";
        config.customHealth = 777.125;
        config.customDamageMultiplier = 1.25;
        config.customLootPreset = "NONE";
        config.customXpReward = 93;
        config.save(file);
        HarderWardensConfig restored = HarderWardensConfig.load(file);
        assertEquals(new DifficultySettings(777.125, 1.25, DifficultySettings.LootPreset.NONE, 93),
                restored.getSettings());
        config.customDamageMultiplier = 2.125;
        config.save(file);
        assertEquals(2.125, HarderWardensConfig.load(file).customDamageMultiplier);
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void missingConfigIsCreatedAndInvalidValuesAreClamped() {
        Path file = directory.resolve("config.json");
        assertEquals(Difficulty.NORMAL, HarderWardensConfig.load(file).getDifficulty());
        assertTrue(Files.exists(file));
        HarderWardensConfig config = new HarderWardensConfig();
        config.difficulty = "CUSTOM";
        config.customHealth = Double.NaN;
        config.customDamageMultiplier = 200;
        config.customXpReward = -1;
        config.save(file);
        DifficultySettings settings = HarderWardensConfig.load(file).getSettings();
        assertEquals(500, settings.health());
        assertEquals(100, settings.damageMultiplier());
        assertEquals(0, settings.xpReward());
    }
}
