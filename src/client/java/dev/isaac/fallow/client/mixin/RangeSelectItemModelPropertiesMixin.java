package dev.isaac.fallow.client.mixin;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.client.SeasonClockModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers Fallow's {@code fallow:season} item-model property alongside vanilla's ({@code time},
 * {@code compass}, ...) so the Season Clock's model can dispatch on it. The property-type registry is
 * a private {@code LateBoundIdMapper} populated only in {@link RangeSelectItemModelProperties#bootstrap()},
 * and there is no Fabric event or API to add to it - so a one-line {@code TAIL} inject is the only
 * way in. Client cosmetic, no behavior change to vanilla properties: a deliberate mixin, held to
 * the same "no other hook exists" bar as every Fallow mixin.
 */
@Mixin(RangeSelectItemModelProperties.class)
public class RangeSelectItemModelPropertiesMixin {
    @Shadow
    @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends RangeSelectItemModelProperty>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void fallow$registerSeasonProperty(CallbackInfo ci) {
        ID_MAPPER.put(Identifier.fromNamespaceAndPath(Fallow.MOD_ID, "season"),
            SeasonClockModelProperty.MAP_CODEC);
    }
}
