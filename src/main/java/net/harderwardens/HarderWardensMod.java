package net.harderwardens;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final Set<UUID> PENDING_WARDENS = ConcurrentHashMap.newKeySet();

    public static HarderWardensConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = HarderWardensConfig.load();
        LOGGER.info("[HarderWardens] Initialised! Difficulty: {}", CONFIG.difficulty);

        registerEntityEvents();
        registerTickEvents();
        registerLootEvents();
        registerCommands();
    }

    private void registerEntityEvents() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Warden warden) {
                applyWardenSettings(warden);
                PENDING_WARDENS.add(warden.getUUID());
            }
        });
    }

    private void registerTickEvents() {
        ServerTickEvents.END_SERVER_TICK.register(this::refreshPendingWardens);
    }

    /**
     * Re-applies settings one tick later because the spawn pipeline can still overwrite
     * current health after ENTITY_LOAD for freshly spawned Wardens.
     */
    private void refreshPendingWardens(MinecraftServer server) {
        if (PENDING_WARDENS.isEmpty()) {
            return;
        }

        Set<UUID> refreshed = ConcurrentHashMap.newKeySet();
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof Warden warden && PENDING_WARDENS.contains(warden.getUUID())) {
                    applyWardenSettings(warden);
                    refreshed.add(warden.getUUID());
                }
            }
        }

        PENDING_WARDENS.removeAll(refreshed);
    }

    /**
     * Applies HP and attack damage modifiers based on the active config.
     * Uses named IDs so modifiers are never stacked twice.
     */
    private void applyWardenSettings(Warden warden) {
        DifficultySettings settings = CONFIG.getSettings();

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

            float newMax = (float) healthAttr.getValue();
            float adjustedHealth = oldMax > 0.0F ? (oldHealth / oldMax) * newMax : newMax;
            warden.setHealth(Mth.clamp(adjustedHealth, 0.0F, newMax));
        }

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

    private void registerLootEvents() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (key.equals(WARDEN_LOOT_KEY)) {
                addWardenLoot(tableBuilder);
            }
        });
    }

    private void addWardenLoot(LootTable.Builder tableBuilder) {
        DifficultySettings.LootPreset preset = CONFIG.getSettings().lootPreset();

        if (preset == DifficultySettings.LootPreset.NONE) return;

        int[] echo = switch (preset) {
            case NONE      -> new int[]{0, 0};
            case EASY      -> new int[]{1, 3};
            case NORMAL    -> new int[]{2, 5};
            case HARD      -> new int[]{3, 7};
            case NIGHTMARE -> new int[]{5, 10};
            case INSANE    -> new int[]{7, 15};
        };
        tableBuilder.withPool(itemPool(Items.ECHO_SHARD, echo[0], echo[1]));

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

        if (preset == DifficultySettings.LootPreset.HARD
                || preset == DifficultySettings.LootPreset.NIGHTMARE
                || preset == DifficultySettings.LootPreset.INSANE) {
            int diamondMax = switch (preset) {
                case INSANE -> 4;
                default     -> 3;
            };
            tableBuilder.withPool(itemPool(Items.DIAMOND, 1, diamondMax));
        }

        if (preset == DifficultySettings.LootPreset.NIGHTMARE
                || preset == DifficultySettings.LootPreset.INSANE) {
            int scrapMax = (preset == DifficultySettings.LootPreset.INSANE) ? 3 : 2;
            tableBuilder.withPool(itemPool(Items.NETHERITE_SCRAP, 1, scrapMax));
        }

        if (preset == DifficultySettings.LootPreset.INSANE) {
            tableBuilder.withPool(itemPool(Items.NETHERITE_INGOT, 1, 2));
        }
    }

    private LootPool.Builder itemPool(net.minecraft.world.item.Item item, int min, int max) {
        return LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(
                                UniformGenerator.between(min, max)
                        ))
                );
    }

    private int reapplyLoadedWardens(MinecraftServer server) {
        int updatedWardens = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof Warden warden) {
                    applyWardenSettings(warden);
                    updatedWardens++;
                }
            }
        }
        return updatedWardens;
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("harderwardens")
                    .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(literal("reload")
                        .executes(ctx -> {
                            CONFIG = HarderWardensConfig.load();
                            int updatedWardens = reapplyLoadedWardens(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§a[HarderWardens] §fConfig reloaded! Difficulty: §e" + CONFIG.difficulty),
                                false
                            );
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§7Updated loaded Wardens: §f" + updatedWardens),
                                false
                            );
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§7Note: for loot table changes, also run §f/reload§7."),
                                false
                            );
                            return 1;
                        })
                    )
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