package dev.isaac.fallow.client.mixin;

import dev.isaac.fallow.client.FallowClientSeasons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the ambient falling-leaf particles seasonal so the visible leaf fall matches the ground
 * leaf litter: vanilla spawns a particle with probability {@code leafParticleChance} per animate
 * tick; we scale that by the current season's leaf-fall weight (autumn-peaked) - a flurry in
 * autumn, light in spring/summer, sparse in winter. Client-side cosmetic only; weight 1.0 (no
 * Fallow server / seasons off) leaves vanilla's rate untouched.
 */
@Mixin(LeavesBlock.class)
public abstract class LeafFallMixin {
    @Shadow @Final protected float leafParticleChance;

    @Shadow
    protected abstract void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random);

    @Inject(method = "makeFallingLeavesParticles", at = @At("HEAD"), cancellable = true)
    private void fallow$seasonalLeafFall(Level level, BlockPos pos, RandomSource random, BlockState state,
            BlockPos particlePos, CallbackInfo ci) {
        float weight = FallowClientSeasons.leafFallWeight();
        if (weight == 1.0f) {
            return; // neutral: let vanilla run unchanged
        }
        ci.cancel();
        // Mirror vanilla: spawn at the leaf's own pos (not the block below), and only when the
        // block below lacks a full top face. The 4th/5th params are the below block's state and
        // position (what vanilla passes), used for that collision check.
        if (random.nextFloat() < leafParticleChance * weight
            && !Block.isFaceFull(state.getCollisionShape(level, particlePos), Direction.UP)) {
            spawnFallingLeavesParticle(level, pos, random);
        }
    }
}
