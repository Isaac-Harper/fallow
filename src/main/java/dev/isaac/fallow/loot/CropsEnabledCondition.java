package dev.isaac.fallow.loot;

import com.mojang.serialization.MapCodec;
import dev.isaac.fallow.Fallow;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Loot condition that passes only when both the Fallow master switch and the crop layer are
 * enabled. Registered as {@code fallow:crops_enabled}. Used to gate seed drops from grass
 * so they only appear when the player has opted into the crop feature.
 *
 * <p>The condition carries no parameters, so its MapCodec is a unit codec (no fields). The
 * random_chance pool condition is a separate condition on the same pool so the two can be
 * authored independently.
 */
public final class CropsEnabledCondition implements LootItemCondition {
    /** Singleton instance - the condition has no parameters. */
    public static final CropsEnabledCondition INSTANCE = new CropsEnabledCondition();

    /** MapCodec registered under {@code fallow:crops_enabled}. */
    public static final MapCodec<CropsEnabledCondition> CODEC =
        MapCodec.unit(INSTANCE);

    private CropsEnabledCondition() {
    }

    @Override
    public MapCodec<CropsEnabledCondition> codec() {
        return CODEC;
    }

    @Override
    public boolean test(LootContext context) {
        return Fallow.CONFIG.enabled && Fallow.CONFIG.crops.enabled;
    }

    /** Register the codec under {@code fallow:crops_enabled} in the loot condition registry. */
    public static void register() {
        Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, "crops_enabled"),
            CODEC);
    }
}
