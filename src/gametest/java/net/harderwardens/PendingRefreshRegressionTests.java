package net.harderwardens;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PendingRefreshRegressionTests {
    @GameTest
    public void workerUnloadsCancelOnlyMatchingRefreshes(GameTestHelper helper) throws Exception {
        HarderWardensConfig previous = HarderWardensMod.CONFIG;
        List<Warden> wardens = new ArrayList<>();
        List<Callable<Void>> unloads = new ArrayList<>();
        try {
            HarderWardensConfig insane = new HarderWardensConfig();
            insane.difficulty = "INSANE";
            HarderWardensMod.CONFIG = insane;
            for (int i = 0; i < 64; i++) {
                Warden warden = helper.spawnWithNoFreeWill(EntityTypes.WARDEN,
                        1 + (i % 8) * 2, 4, 1 + (i / 8) * 2);
                warden.setNoGravity(true);
                wardens.add(warden);
                if (i % 2 == 0) {
                    warden.setHealth(321);
                    // Exercise the callback boundary without mutating world/entity state on workers.
                    unloads.add(() -> {
                        ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(warden, helper.getLevel());
                        return null;
                    });
                } else {
                    warden.setHealth(500);
                    Warden stale = EntityTypes.WARDEN.create(helper.getLevel(), EntitySpawnReason.LOAD);
                    stale.setUUID(warden.getUUID());
                    unloads.add(() -> {
                        ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(stale, helper.getLevel());
                        return null;
                    });
                }
            }
            try (var workers = Executors.newFixedThreadPool(4)) {
                for (var result : workers.invokeAll(unloads, 5, TimeUnit.SECONDS)) {
                    helper.assertFalse(result.isCancelled(), "Worker callbacks complete without waiting for server tasks");
                    result.get();
                }
            }
        } finally {
            HarderWardensMod.CONFIG = previous;
        }
        helper.runAfterDelay(2, () -> {
            try {
                for (int i = 0; i < wardens.size(); i++) {
                    float expected = i % 2 == 0 ? 321 : 1000;
                    helper.assertTrue(wardens.get(i).getHealth() == expected,
                            "Matching unload cancels refresh; stale same-UUID unload preserves newer refresh");
                }
                helper.succeed();
            } finally {
                wardens.forEach(Warden::discard);
            }
        });
    }
}
