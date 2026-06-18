package dev.isaac.fallow.client.mixin;

import dev.isaac.fallow.client.FallowClientSeasons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Seasonal cherry leaf-fall. Cherry leaves are an {@link UntintedParticleLeavesBlock}, so their
 * falling particle is always the fixed pink blossom petal regardless of season. While a recolored
 * cherry leaf model is showing (see {@code SeasonalLeafModels}) we instead drop a tinted-leaf
 * particle in that season's color so the drifting leaves match the recolored canopy - crimson in
 * autumn, dormant brown in winter. Spring/summer keep the vanilla pink petal. Client-side cosmetic.
 */
@Mixin(UntintedParticleLeavesBlock.class)
public abstract class CherryLeafParticleMixin {
    /** Match the cherry autumn/winter leaf textures; full alpha for an opaque particle. */
    @Unique
    private static final int FALLOW_CHERRY_AUTUMN_RGB = 0xFFB4182E;
    @Unique
    private static final int FALLOW_CHERRY_WINTER_RGB = 0xFF8C7A5E;

    @Inject(method = "spawnFallingLeavesParticle", at = @At("HEAD"), cancellable = true)
    private void fallow$seasonalCherryFall(Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if ((Object) this != Blocks.CHERRY_LEAVES) {
            return;
        }
        int color = switch (FallowClientSeasons.swapSeason()) {
            case AUTUMN -> FALLOW_CHERRY_AUTUMN_RGB;
            case WINTER -> FALLOW_CHERRY_WINTER_RGB;
            case null, default -> -1; // spring/summer/no-swap: keep the vanilla pink petal
        };
        if (color == -1) {
            return;
        }
        ci.cancel();
        ParticleUtils.spawnParticleBelow(level, pos, random,
            ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, color));
    }
}
