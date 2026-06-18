package dev.isaac.fallow.client;

import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Delegates to vanilla's constant source, then applies the live season tint. */
public record SeasonalTint(BlockTintSource delegate, IntUnaryOperator tint)
    implements BlockTintSource {
    @Override
    public int color(BlockState state) {
        return tint.applyAsInt(delegate.color(state));
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return tint.applyAsInt(delegate.colorInWorld(state, level, pos));
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return delegate.relevantProperties();
    }
}
