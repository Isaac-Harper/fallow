package dev.isaac.fallow.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;

/**
 * The custom item-model property that drives the Season Clock's face. Registered under
 * {@code fallow:season} (see {@code RangeSelectItemModelPropertiesMixin}) and referenced by
 * {@code assets/fallow/items/season_clock.json}'s {@code range_dispatch}: it returns a continuous
 * fraction through the year in {@code [0, 1)} (season + day-in-season + intra-day time), so the
 * dial's many frames sweep smoothly rather than snapping between four - exactly how vanilla's
 * {@code minecraft:time} drives the clock, but off Fallow's season instead of the daytime.
 * Client-only, read every frame: a volatile read plus a day-time read, no allocation.
 */
public record SeasonClockModelProperty() implements RangeSelectItemModelProperty {
    public static final MapCodec<SeasonClockModelProperty> MAP_CODEC =
        MapCodec.unit(new SeasonClockModelProperty());

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        return FallowClientSeasons.yearFraction(level);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}
