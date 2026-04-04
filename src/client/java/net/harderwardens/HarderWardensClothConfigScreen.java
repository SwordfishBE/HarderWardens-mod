package net.harderwardens;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HarderWardensClothConfigScreen {
    private HarderWardensClothConfigScreen() {
    }

    static Screen create(Screen parent) {
        HarderWardensConfig config = HarderWardensMod.loadConfigForEditing();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Harder Wardens Config"))
                .setSavingRunnable(() -> HarderWardensMod.applyEditedConfig(config));

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        ConfigEntryBuilder entries = builder.entryBuilder();

        general.addEntry(entries.startEnumSelector(
                        Component.literal("Difficulty"),
                        Difficulty.class,
                        config.getDifficulty()
                )
                .setDefaultValue(Difficulty.NORMAL)
                .setTooltip(Component.literal("Preset difficulty for newly spawned Wardens."))
                .setEnumNameProvider(value -> Component.literal(value.name()))
                .setSaveConsumer(value -> config.difficulty = value.name())
                .build());

        general.addEntry(entries.startDoubleField(Component.literal("Custom Health"), config.customHealth)
                .setDefaultValue(500.0)
                .setMin(1.0)
                .setMax(1024.0)
                .setTooltip(Component.literal("Maximum Warden health when difficulty is CUSTOM."))
                .setSaveConsumer(value -> config.customHealth = value)
                .build());

        general.addEntry(entries.startDoubleField(Component.literal("Custom Damage Multiplier"), config.customDamageMultiplier)
                .setDefaultValue(1.5)
                .setMin(0.1)
                .setMax(100.0)
                .setTooltip(Component.literal("Attack damage multiplier when difficulty is CUSTOM."))
                .setSaveConsumer(value -> config.customDamageMultiplier = value)
                .build());

        general.addEntry(entries.startEnumSelector(
                        Component.literal("Custom Loot Preset"),
                        DifficultySettings.LootPreset.class,
                        config.getCustomLootPreset()
                )
                .setDefaultValue(DifficultySettings.LootPreset.NORMAL)
                .setTooltip(Component.literal("Loot preset used when difficulty is CUSTOM."))
                .setEnumNameProvider(value -> Component.literal(value.name()))
                .setSaveConsumer(value -> config.customLootPreset = value.name())
                .build());

        return builder.build();
    }
}
