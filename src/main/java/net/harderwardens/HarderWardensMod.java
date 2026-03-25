package net.harderwardens;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;

// Mojang-mapped Minecraft imports
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.literal;

public class HarderWardensMod implements ModInitializer {

    public static final String MOD_ID = "harder_wardens";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** ID for the health modifier — prevents double-stacking on chunk reload. */
    public static final Identifier HEALTH_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "health_boost");

    /** ID for the damage modifier. */
    public static final Identifier DAMAGE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "damage_boost");

    /** Registry key for the Warden loot table. */
    private static final ResourceKey<LootTable> WARDEN_LOOT_KEY =
            ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("entities/warden"));

    public static HarderWardensConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = HarderWardensConfig.load();
        LOGGER.info("[HarderWardens] Initialised! Difficulty: {}", CONFIG.difficulty);

        registerEntityEvents();
        registerLootEvents();
        registerCommands();
    }

    // ── Entity Events ─────────────────────────────────────────────────────────

    private void registerEntityEvents() {
        // Fires whenever a Warden is loaded (fresh spawn or loaded from chunk)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Warden warden) {
                applyWardenSettings(warden);
            }
        });
    }

    /**
     * Applies HP and attack damage modifiers based on the active config.
     * Uses named ResourceLocation IDs so modifiers are never stacked twice.
     */
    private void applyWardenSettings(Warden warden) {
        DifficultySettings settings = CONFIG.getSettings();

        // ── Max Health ──────────────────────────────────────────────────────
        // Vanilla Warden has 500 HP. We add the delta as ADD_VALUE.
        AttributeInstance healthAttr = warden.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            float oldMax = (float) healthAttr.getValue();
            float oldHealth = warden.getHealth();

            healthAttr.removeModifier(HEALTH_MODIFIER_ID);

            double healthBonus = settings.health() - 500.0;
            healthAttr.addPermanentModifier(new AttributeModifier(
                    HEALTH_MODIFIER_ID,
                    healthBonus,
                    AttributeModifier.Operation.ADD_VALUE
            ));

            // Keep the same health ratio after changing max HP.
            // A fresh 500/500 Warden becomes 1024/1024 on INSANE instead of staying at 500/1024.
            float newMax = (float) healthAttr.getValue();
            float adjustedHealth = oldMax > 0.0F ? (oldHealth / oldMax) * newMax : newMax;
            warden.setHealth(Mth.clamp(adjustedHealth, 0.0F, newMax));
        }

        // ── Attack Damage ────────────────────────────────────────────────────
        // Vanilla Warden base attack = 30.
        // ADD_MULTIPLIED_BASE adds (multiplier - 1.0) * base.
        // Example: 1.5x → bonus = 0.5 → total = 30 + 0.5 * 30 = 45
        AttributeInstance damageAttr = warden.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_ID);

            double multiplierBonus = settings.damageMultiplier() - 1.0;
            damageAttr.addPermanentModifier(new AttributeModifier(
                    DAMAGE_MODIFIER_ID,
                    multiplierBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            ));
        }
    }

    // ── Loot Events ───────────────────────────────────────────────────────────

    private void registerLootEvents() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (key.equals(WARDEN_LOOT_KEY)) {
                addWardenLoot(tableBuilder);
            }
        });
    }

    /**
     * Appends extra loot pools to the Warden loot table.
     *
     * EASY:      1–3 Echo Shards
     * NORMAL:    2–5 Echo Shards, 1 Sculk Catalyst
     * HARD:      3–7 Echo Shards, 1–2 Sculk Catalyst, 1–3 Diamond
     * NIGHTMARE: 5–10 Echo Shards, 1–3 Sculk Catalyst, 1–3 Diamond, 1–2 Netherite Scrap
     * INSANE:    7–15 Echo Shards, 2–5 Sculk Catalyst, 2–4 Diamond, 1–3 Netherite Scrap,
     *            1–2 Netherite Ingot
     */
    private void addWardenLoot(LootTable.Builder tableBuilder) {
        DifficultySettings.LootPreset preset = CONFIG.getSettings().lootPreset();

        // No extra loot
        if (preset == DifficultySettings.LootPreset.NONE) return;

        // Echo Shards — always present
        int[] echo = switch (preset) {
            case NONE      -> new int[]{0, 0}; // unreachable, caught above
            case EASY      -> new int[]{1, 3};
            case NORMAL    -> new int[]{2, 5};
            case HARD      -> new int[]{3, 7};
            case NIGHTMARE -> new int[]{5, 10};
            case INSANE    -> new int[]{7, 15};
        };
        tableBuilder.withPool(itemPool(Items.ECHO_SHARD, echo[0], echo[1]));

        // Sculk Catalyst — NORMAL and above
        if (preset != DifficultySettings.LootPreset.EASY) {
            int sculkMax = switch (preset) {
                case NORMAL    -> 1;
                case HARD      -> 2;
                case NIGHTMARE -> 3;
                case INSANE    -> 5;
                default        -> 1;
            };
            tableBuilder.withPool(itemPool(Items.SCULK_CATALYST, 1, sculkMax));
        }

        // Diamond — HARD and above
        if (preset == DifficultySettings.LootPreset.HARD
                || preset == DifficultySettings.LootPreset.NIGHTMARE
                || preset == DifficultySettings.LootPreset.INSANE) {
            int diamondMax = switch (preset) {
                case INSANE -> 4;
                default     -> 3;
            };
            tableBuilder.withPool(itemPool(Items.DIAMOND, 1, diamondMax));
        }

        // Netherite Scrap — NIGHTMARE and above
        if (preset == DifficultySettings.LootPreset.NIGHTMARE
                || preset == DifficultySettings.LootPreset.INSANE) {
            int scrapMax = (preset == DifficultySettings.LootPreset.INSANE) ? 3 : 2;
            tableBuilder.withPool(itemPool(Items.NETHERITE_SCRAP, 1, scrapMax));
        }

        // Netherite Ingot — INSANE only
        if (preset == DifficultySettings.LootPreset.INSANE) {
            tableBuilder.withPool(itemPool(Items.NETHERITE_INGOT, 1, 2));
        }
    }

    /** Builds a simple single-roll LootPool for one item with a count range. */
    private LootPool.Builder itemPool(net.minecraft.world.item.Item item, int min, int max) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(
                                UniformGenerator.between(min, max)
                        ))
                );
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("harderwardens")
                    .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                    // /harderwardens reload
                    .then(literal("reload")
                        .executes(ctx -> {
                            CONFIG = HarderWardensConfig.load();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§a[HarderWardens] §fConfig reloaded! Difficulty: §e" + CONFIG.difficulty),
                                false
                            );
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§7Note: for loot table changes, also run §f/reload§7."),
                                false
                            );
                            return 1;
                        })
                    )

                    // /harderwardens info
                    .then(literal("info")
                        .executes(ctx -> {
                            DifficultySettings s = CONFIG.getSettings();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(
                                    "§6[HarderWardens]§r\n" +
                                    "  §7Difficulty: §f" + CONFIG.difficulty + "\n" +
                                    "  §7Warden HP:  §f" + (int) s.health() + "\n" +
                                    "  §7Damage:     §f" + s.damageMultiplier() + "x\n" +
                                    "  §7Loot:       §f" + s.lootPreset()
                                ),
                                false
                            );
                            return 1;
                        })
                    )
            )
        );
    }
}
