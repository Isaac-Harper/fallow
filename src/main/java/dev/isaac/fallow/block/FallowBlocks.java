package dev.isaac.fallow.block;

import dev.isaac.fallow.Fallow;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Function;

/**
 * Fallow crop blocks. Must be registered before {@link dev.isaac.fallow.item.FallowItems}
 * because seed BlockItems reference the block instances.
 */
public final class FallowBlocks {
    // Farmland crops - AGE 0-3, farmland base.
    public static final Block TURNIP_CROP = register("turnip_crop",
        TurnipCropBlock::new, cropProperties());
    public static final Block CABBAGE_CROP = register("cabbage_crop",
        CabbageCropBlock::new, cropProperties());
    public static final Block ONION_CROP = register("onion_crop",
        OnionCropBlock::new, cropProperties());

    // Sweet-berry-style bush, survives on grass/dirt/farmland.
    public static final Block STRAWBERRY_BUSH = register("strawberry_bush",
        StrawberryBushBlock::new, bushProperties());

    // Decorative forage plant placed by the forage task.
    public static final Block WILD_ONION = register("wild_onion",
        WildOnionBlock::new, wildPlantProperties());

    // Trellis + its crop.
    public static final Block TRELLIS = register("trellis",
        TrellisBlock::new, trellisProperties());
    public static final Block PEA_CROP = register("pea_crop",
        PeaCropBlock::new, peaProperties());

    // Winter-kill husk: instabreak, no drops.
    public static final Block WITHERED_CROP = register("withered_crop",
        WitheredCropBlock::new, witheredProperties());

    private FallowBlocks() {
    }

    /** Standard farmland-crop properties (no collision, random ticks, grass sounds). */
    private static BlockBehaviour.Properties cropProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.CROP)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion();
    }

    /** Bush properties - same as vanilla sweet_berry_bush (no collision, random ticks, grass). */
    private static BlockBehaviour.Properties bushProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.SWEET_BERRY_BUSH)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion();
    }

    /** Wild forage plant - simple decorative, no collision, instabreak. */
    private static BlockBehaviour.Properties wildPlantProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion();
    }

    /** Trellis: visible lattice structure, small strength, grass sounds. */
    private static BlockBehaviour.Properties trellisProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .noCollision()
            .strength(0.2f)
            .sound(SoundType.GRASS)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion();
    }

    /** Pea crop on the trellis - like a vine/climber, no collision. */
    private static BlockBehaviour.Properties peaProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.CROP)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion();
    }

    /** Withered crop husk: instabreak, no drops. */
    private static BlockBehaviour.Properties witheredProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN)
            .noCollision()
            .instabreak()
            .noLootTable()
            .sound(SoundType.CROP)
            .pushReaction(PushReaction.DESTROY)
            .noOcclusion();
    }

    /**
     * Resolve the registry key, stamp it onto the properties (MC 26.1.2 requires the id be set
     * before the Block constructor runs), then register.
     */
    private static Block register(String name,
            Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, name));
        Block block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    /** Touch all static fields, forcing registration. */
    public static void register() {
        // Static initializer handles registration; this method exists to guarantee class loading
        // in a predictable order from Fallow.onInitialize.
    }
}
