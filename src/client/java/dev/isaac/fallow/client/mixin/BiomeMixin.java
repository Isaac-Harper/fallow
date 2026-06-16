package dev.isaac.fallow.client.mixin;

import dev.isaac.fallow.client.FallowClientSeasons;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Seasonal tint over the final biome grass/foliage colors. Injecting at the {@code Biome}
 * getters (rather than the colormap statics) catches biomes with fixed color overrides too,
 * and both vanilla and Sodium resolve block tints through these methods, so the seasonal
 * look is renderer-agnostic. The tint is a no-op (strength 0) without a Fallow server,
 * out of season, or when disabled - vanilla colors exactly.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Inject(method = "getGrassColor", at = @At("RETURN"), cancellable = true)
    private void fallow$tintGrass(double x, double z, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FallowClientSeasons.tintGrass(cir.getReturnValueI()));
    }

    @Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
    private void fallow$tintFoliage(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FallowClientSeasons.tintFoliage(cir.getReturnValueI()));
    }

    @Inject(method = "getDryFoliageColor", at = @At("RETURN"), cancellable = true)
    private void fallow$tintDryFoliage(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(FallowClientSeasons.tintDryFoliage(cir.getReturnValueI()));
    }
}
