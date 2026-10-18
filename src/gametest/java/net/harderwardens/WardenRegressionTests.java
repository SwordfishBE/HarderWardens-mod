package net.harderwardens;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.UUID;

public class WardenRegressionTests {
    @GameTest
    public void legacyWardenKeepsAttributes(GameTestHelper helper) {
        HarderWardensConfig previous = HarderWardensMod.CONFIG;
        try {
            HarderWardensConfig insane = new HarderWardensConfig();
            insane.difficulty = "INSANE";
            HarderWardensMod.CONFIG = insane;
            Warden original = helper.spawnWithNoFreeWill(EntityTypes.WARDEN, 1, 2, 1);
            original.setHealth(200);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            original.saveWithoutId(output);
            var tag = output.buildResult();
            tag.remove("harder_wardens:xp_reward");
            original.discard();
            HarderWardensMod.CONFIG = new HarderWardensConfig();
            Warden legacy = EntityTypes.WARDEN.create(helper.getLevel(), EntitySpawnReason.LOAD);
            legacy.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
            legacy.setUUID(UUID.randomUUID());
            helper.assertTrue(helper.getLevel().addFreshEntity(legacy), "Legacy Warden added");
            helper.assertTrue(legacy.getMaxHealth() == 1000 && legacy.getHealth() == 200,
                    "Legacy Warden keeps its max and current HP");
            helper.assertTrue(legacy.getAttributeValue(Attributes.ATTACK_DAMAGE) == 90,
                    "Legacy Warden keeps damage modifier");
            helper.assertTrue(((MonsterAccessor) legacy).harderWardens$getSavedXpReward() == 25,
                    "Missing legacy XP is initialized from current config");
            helper.succeed();
        } finally {
            HarderWardensMod.CONFIG = previous;
        }
    }

    @GameTest
    public void spawnAndReloadPreserveStats(GameTestHelper helper) {
        HarderWardensConfig previous = HarderWardensMod.CONFIG;
        Warden loaded;
        Warden fresh;
        try {
            HarderWardensConfig insane = new HarderWardensConfig();
            insane.difficulty = "INSANE";
            HarderWardensMod.CONFIG = insane;
            Warden original = helper.spawnWithNoFreeWill(EntityTypes.WARDEN, 1, 2, 1);
            helper.assertTrue(original.getMaxHealth() == 1000, "New Warden max HP");
            helper.assertTrue(original.getHealth() == 1000, "New Warden full HP");
            original.setHealth(321);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            original.saveWithoutId(output);
            original.discard();

            HarderWardensConfig easy = new HarderWardensConfig();
            easy.difficulty = "EASY";
            HarderWardensMod.CONFIG = easy;
            loaded = EntityTypes.WARDEN.create(helper.getLevel(), EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING,
                    helper.getLevel().registryAccess(), output.buildResult()));
            loaded.setUUID(UUID.randomUUID());
            loaded.setNoAi(true);
            helper.assertTrue(((MonsterAccessor) loaded).harderWardens$wasLoaded(), "Load marker is set");
            helper.assertTrue(helper.getLevel().addFreshEntity(loaded), "Reloaded Warden added");
            helper.assertTrue(loaded.getMaxHealth() == 1000, "Reload retains max HP despite changed config");
            helper.assertTrue(loaded.getHealth() == 321, "Reload retains damaged HP");
            helper.assertTrue(loaded.getAttributeValue(Attributes.ATTACK_DAMAGE) == 90, "Reload retains damage");
            helper.assertTrue(((MonsterAccessor) loaded).harderWardens$getSavedXpReward() == 70, "Reload retains XP");

            HarderWardensMod.CONFIG = insane;
            fresh = helper.spawnWithNoFreeWill(EntityTypes.WARDEN, 2, 2, 2);
            // Reproduce the late spawn-health overwrite that the delayed refresh fixes.
            fresh.setHealth(500);
        } finally {
            HarderWardensMod.CONFIG = previous;
        }
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(fresh.getHealth() == 1000, "Delayed refresh restores fresh spawn HP");
            helper.assertTrue(loaded.getHealth() == 321, "Delayed refresh does not heal loaded Wardens");
            helper.succeed();
        });
    }
}
