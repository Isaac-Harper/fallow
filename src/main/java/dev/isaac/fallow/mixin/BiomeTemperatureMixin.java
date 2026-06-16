package dev.isaac.fallow.mixin;

import dev.isaac.fallow.growth.BiomeTuning;
import dev.isaac.fallow.season.SeasonalTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The seasons' one gameplay lever (and the mod's only non-cosmetic mixin): shift every biome's
 * effective temperature by the current season's offset. Vanilla derives rain-vs-snow,
 * snow-layer accumulation, and water freeze/thaw from this single value, so one injection makes
 * all of them seasonal - a temperate biome rains in summer and snows in winter, warm biomes stay
 * rain, and {@code has_precipitation=false} deserts/savanna are untouched (their precipitation is
 * gated by a flag, not temperature). The seasonless base is what the per-position
 * {@code temperatureCache} stores; we add the offset on each return, so the cache stays correct.
 * Offset 0 (out of season / disabled / no Fallow server) is bit-identical to vanilla.
 *
 * <p>This is the deliberate exception to the no-mixin rule: the gettemperature redirect is the
 * only way (short of per-tick re-datapacking biomes) to make precipitation <em>type</em> seasonal
 * for the same biome - there is no event or setter for a per-call temperature. See docs.
 *
 * <p>The swing is scaled by this biome's <em>seasonality</em> ({@code biomeSeasonality} k, resolved
 * per biome and read from {@link BiomeTuning#seasonality(Biome)} - the eager registry-built map the
 * mixin can consult with only the {@code Biome} instance). Tropical biomes (k~0.2, incl. mangrove
 * k=0.3) barely cool and never snow even at altitude; temperate (k=1.0) snow in winter; boreal
 * (k&gt;1) snow hard. So all of it - growth, density, snow type/depth, and this swing - is driven by
 * the one per-biome config knob, no base-temperature proxy.
 */
@Mixin(Biome.class)
public abstract class BiomeTemperatureMixin {
    @Inject(method = "getTemperature(Lnet/minecraft/core/BlockPos;I)F", at = @At("RETURN"), cancellable = true)
    private void fallow$seasonalTemperature(BlockPos pos, int seaLevel, CallbackInfoReturnable<Float> cir) {
        float offset = SeasonalTemperature.offset();
        if (offset == 0.0f) {
            return;
        }
        float damped = (float) (offset * BiomeTuning.seasonality((Biome) (Object) this));
        if (damped != 0.0f) {
            cir.setReturnValue(cir.getReturnValueF() + damped);
        }
    }
}
