package net.harderwardens;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.harderwardens.util.ModrinthUpdateChecker;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.UniformGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.literal;

public class HarderWardensMod implements ModInitializer {

    public static final String MOD_ID = "harder_wardens";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final ModMetadata MOD_METADATA = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .orElseThrow(() -> new IllegalStateException("Missing mod metadata for " + MOD_ID))
            .getMetadata();
    public static final String MOD_NAME = MOD_METADATA.getName();
    public static final String MOD_VERSION = MOD_METADATA.getVersion().getFriendlyString();
    public static final String LOG_PREFIX = "[" + MOD_NAME + "]";
    private static final int MAX_PENDING_REFRESH_ATTEMPTS = 5;

    /** ID for the health modifier — prevents double-stacking on chunk reload. */
    public static final Identifier HEALTH_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "health_boost");

    /** ID for the damage modifier. */
    public static final Identifier DAMAGE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(MOD_ID, "damage_boost");

    /** Registry key for the Warden loot table. */
    private static final ResourceKey<LootTable> WARDEN_LOOT_KEY =
            ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("entities/warden"));

    // Entity callbacks may run on Async workers; delayed HP refreshes run on the server tick.
    private static final Map<UUID, PendingRefresh> PENDING_WARDENS = new ConcurrentHashMap<>();

    private record PendingRefresh(ResourceKey<Level> dimension, int entityId, int attempts) {}

    public static volatile HarderWardensConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = HarderWardensConfig.load();
        LOGGER.info("{} Mod initialized. Version: {}", LOG_PREFIX, MOD_VERSION);

        registerEntityEvents();
        registerTickEvents();
        registerLootEvents();
        registerCommands();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> ModrinthUpdateChecker.checkOnceAsync());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING_WARDENS.clear());
    }

    static HarderWardensConfig loadConfigForEditing() {
        return HarderWardensConfig.load();
    }

    static void applyEditedConfig(HarderWardensConfig editedConfig) {
        editedConfig.save();
        CONFIG = HarderWardensConfig.load();
        LOGGER.debug("{} Config updated through client config screen.", LOG_PREFIX);
    }

    private void registerEntityEvents() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Warden warden) {
                MonsterAccessor state = (MonsterAccessor) warden;
                AttributeInstance health = warden.getAttribute(Attributes.MAX_HEALTH);
                AttributeInstance damage = warden.getAttribute(Attributes.ATTACK_DAMAGE);
                if (health != null && health.getModifier(HEALTH_MODIFIER_ID) != null
                        && damage != null && damage.getModifier(DAMAGE_MODIFIER_ID) != null) {
                    // Older releases persisted attributes, but not the configured XP reward.
                    if (state.harderWardens$getSavedXpReward() < 0) {
                        state.harderWardens$setXpReward(CONFIG.getSettings().xpReward());
                    }
                    return;
                }
                boolean freshSpawn = !state.harderWardens$wasLoaded();
                applyWardenSettings(warden, freshSpawn);
                if (freshSpawn) {
                    PENDING_WARDENS.put(warden.getUUID(), new PendingRefresh(world.dimension(), warden.getId(), 0));
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof Warden) {
                PENDING_WARDENS.computeIfPresent(entity.getUUID(), (uuid, pending) ->
                        pending.entityId() == entity.getId() && pending.dimension().equals(world.dimension())
                                ? null : pending);
            }
        });
    }

    private void registerTickEvents() {
        ServerTickEvents.END_SERVER_TICK.register(this::refreshPendingWardens);
    }

    /**
     * Finishes stat application one tick later because fresh Warden spawns can still overwrite
     * current health after ENTITY_LOAD. At this point max health is already updated, so setHealth
     * clamps against the correct value instead of an older cached max.
     */
    private void refreshPendingWardens(MinecraftServer server) {
        if (PENDING_WARDENS.isEmpty()) {
            return;
        }

        for (var entry : PENDING_WARDENS.entrySet()) {
            PendingRefresh pending = entry.getValue();
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) {
                PENDING_WARDENS.remove(entry.getKey(), pending);
                continue;
            }
            if (level.getEntity(entry.getKey()) instanceof Warden warden
                    && warden.getId() == pending.entityId()) {
                if (warden.isAlive()) {
                    warden.setHealth(warden.getMaxHealth());
                }
                PENDING_WARDENS.remove(entry.getKey(), pending);
            } else if (pending.attempts() + 1 >= MAX_PENDING_REFRESH_ATTEMPTS) {
                PENDING_WARDENS.remove(entry.getKey(), pending);
            } else {
                // Do not resurrect a removed request or overwrite a newer entity's request.
                PENDING_WARDENS.replace(entry.getKey(), pending,
                        new PendingRefresh(pending.dimension(), pending.entityId(), pending.attempts() + 1));
            }
        }
    }

    /**
     * Applies HP and attack damage modifiers based on the active config.
     * Uses named IDs so modifiers are never stacked twice.
     */
    private void applyWardenSettings(Warden warden, boolean freshSpawn) {
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
            warden.setHealth(freshSpawn ? newMax : Mth.clamp(adjustedHealth, 0.0F, newMax));
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

        ((MonsterAccessor) warden).harderWardens$setXpReward(settings.xpReward());
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
        Holder<ContextIntProvider> rolls = Holder.direct(new ConstantValue(1));
        Holder<ContextIntProvider> count = Holder.direct(new UniformGenerator(
                Holder.direct(new ConstantValue(min)),
                Holder.direct(new ConstantValue(max))
        ));

        return LootPool.lootPool()
                .setRolls(rolls)
                .add(LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(
                                count
                        ))
                );
    }


    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("harderwardens")
                    .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(literal("reload")
                        .executes(ctx -> {
                            CONFIG = HarderWardensConfig.load();
                            LOGGER.info("{} Config reloaded via command.", LOG_PREFIX);
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§a[HarderWardens] §fConfig reloaded! Difficulty: §e" + CONFIG.difficulty),
                                false
                            );
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("§7Existing Wardens keep their current stats. New spawns use the updated config."),
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
                                    "  §7Loot:       §f" + s.lootPreset() + "\n" +
                                    "  §7XP Reward:  §f" + s.xpReward()
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
