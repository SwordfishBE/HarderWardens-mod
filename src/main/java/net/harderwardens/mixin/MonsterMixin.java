package net.harderwardens.mixin;

import net.harderwardens.MonsterAccessor;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Mob.class)
public abstract class MonsterMixin implements MonsterAccessor {

    @Shadow
    protected int xpReward;

    @Override
    public void harderWardens$setXpReward(int xpReward) {
        this.xpReward = xpReward;
    }
}
