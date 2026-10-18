package net.harderwardens.mixin;

import net.harderwardens.MonsterAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MonsterMixin implements MonsterAccessor {

    @Shadow
    protected int xpReward;

    @Unique private boolean harderWardens$loaded;
    @Unique private int harderWardens$savedXpReward = -1;

    @Override
    public void harderWardens$setXpReward(int xpReward) {
        this.xpReward = xpReward;
        harderWardens$savedXpReward = xpReward;
    }

    @Override
    public boolean harderWardens$wasLoaded() {
        return harderWardens$loaded;
    }

    @Override
    public int harderWardens$getSavedXpReward() {
        return harderWardens$savedXpReward;
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void harderWardens$readState(ValueInput input, CallbackInfo ci) {
        if ((Object) this instanceof Warden) {
            harderWardens$loaded = true;
            harderWardens$savedXpReward = Math.clamp(input.getIntOr("harder_wardens:xp_reward", -1), -1, 100);
            if (harderWardens$savedXpReward >= 0) {
                xpReward = harderWardens$savedXpReward;
            }
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void harderWardens$writeState(ValueOutput output, CallbackInfo ci) {
        if ((Object) this instanceof Warden && harderWardens$savedXpReward >= 0) {
            output.putInt("harder_wardens:xp_reward", harderWardens$savedXpReward);
        }
    }
}
