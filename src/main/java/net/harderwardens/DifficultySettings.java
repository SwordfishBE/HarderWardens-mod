package net.harderwardens;

/**
 * Holds all stat settings for each difficulty preset.
 * Vanilla Warden baseline: 500 HP, 30 base attack damage.
 */
public record DifficultySettings(
        double health,
        double damageMultiplier,
        LootPreset lootPreset
) {

    // ── Presets ───────────────────────────────────────────────────────────────

    public static final DifficultySettings EASY      = new DifficultySettings(500.0,  1.0, LootPreset.EASY);
    public static final DifficultySettings NORMAL    = new DifficultySettings(500.0,  1.5, LootPreset.NORMAL);
    public static final DifficultySettings HARD      = new DifficultySettings(750.0,  2.0, LootPreset.HARD);
    public static final DifficultySettings NIGHTMARE = new DifficultySettings(850.0, 2.5, LootPreset.NIGHTMARE);
    public static final DifficultySettings INSANE    = new DifficultySettings(1000.0, 3.0, LootPreset.INSANE);

    /** Returns the preset matching a given name string (used for customLootPreset). */
    public static DifficultySettings fromName(String name) {
        return switch (name.toUpperCase()) {
            case "NONE"      -> new DifficultySettings(500.0, 1.0, LootPreset.NONE);
            case "EASY"      -> EASY;
            case "HARD"      -> HARD;
            case "NIGHTMARE" -> NIGHTMARE;
            case "INSANE"    -> INSANE;
            default          -> NORMAL;
        };
    }

    // ── Loot Presets ──────────────────────────────────────────────────────────

    /**
     * Controls which items drop when the Warden is killed.
     *
     * EASY:      1–3 Echo Shards
     * NORMAL:    2–5 Echo Shards + 1 Sculk Catalyst
     * HARD:      3–7 Echo Shards + 1–2 Sculk Catalyst + 1–3 Diamond
     * NIGHTMARE: 5–10 Echo Shards + 1–3 Sculk Catalyst + 1–3 Diamond + 1–2 Netherite Scrap
     * INSANE:    7–15 Echo Shards + 2–5 Sculk Catalyst + 2–4 Diamond + 1–3 Netherite Scrap
     *            + 1–2 Netherite Ingot
     */
    public enum LootPreset {
        NONE, EASY, NORMAL, HARD, NIGHTMARE, INSANE
    }
}