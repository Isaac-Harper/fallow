package dev.isaac.fallow.client.mixin;

import dev.isaac.fallow.client.FallowClientSeasons;
import dev.isaac.fallow.client.SeasonalTint;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Seasonal tint for blocks vanilla colors with a fixed {@code constant} source instead of the
 * biome foliage color: birch leaves, spruce leaves and lily pads. Because they never read the
 * biome color, {@code BiomeMixin} can't reach them; here we wrap each one's registered tint source
 * once, at registry build time, so its color flows through the matching
 * {@link FallowClientSeasons} tint - birch goes golden in autumn, spruce frost-mutes in winter,
 * lily pads turn through the year, and all are exactly vanilla when out of season or disabled.
 * Wrapping the canonical {@link BlockColors} registry keeps this renderer-agnostic (Sodium resolves
 * tints the same way), and the per-block tint cache means the wrapper is hit only on chunk rebuild.
 */
@Mixin(BlockColors.class)
public abstract class FixedFoliageTintMixin {
    @Inject(method = "createDefault", at = @At("RETURN"))
    private static void fallow$tintFixedFoliage(CallbackInfoReturnable<BlockColors> cir) {
        BlockColors colors = cir.getReturnValue();
        fallow$wrap(colors, Blocks.BIRCH_LEAVES, FallowClientSeasons::tintBirchFoliage);
        fallow$wrap(colors, Blocks.SPRUCE_LEAVES, FallowClientSeasons::tintSpruceFoliage);
        fallow$wrap(colors, Blocks.LILY_PAD, FallowClientSeasons::tintLilyPad);
    }

    private static void fallow$wrap(BlockColors colors, Block block, IntUnaryOperator tint) {
        BlockTintSource original = colors.getTintSource(block.defaultBlockState(), 0);
        if (original != null) {
            colors.register(List.of(new SeasonalTint(original, tint)), block);
        }
    }
}
