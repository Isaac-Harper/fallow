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
    // Phase C2 farmland crops.
    public static final Block LEEK_CROP = register("leek_crop",
        LeekCropBlock::new, cropProperties());
    public static final Block BARLEY_CROP = register("barley_crop",
        BarleyCropBlock::new, cropProperties());
    public static final Block RYE_CROP = register("rye_crop",
        RyeCropBlock::new, cropProperties());
    public static final Block OAT_CROP = register("oat_crop",
        OatCropBlock::new, cropProperties());
    public static final Block GARLIC_CROP = register("garlic_crop",
        GarlicCropBlock::new, cropProperties());
    public static final Block RADISH_CROP = register("radish_crop",
        RadishCropBlock::new, cropProperties());
    public static final Block PARSNIP_CROP = register("parsnip_crop",
        ParsnipCropBlock::new, cropProperties());
    public static final Block PEPPER_CROP = register("pepper_crop",
        PepperCropBlock::new, cropProperties());
    public static final Block FLAX_CROP = register("flax_crop",
        FlaxCropBlock::new, cropProperties());
    public static final Block TOMATO_CROP = register("tomato_crop",
        TomatoCropBlock::new, cropProperties());
    public static final Block RICE_CROP = register("rice_crop",
        RiceCropBlock::new, cropProperties());

    // Corn: double-height farmland crop (pitcher-style), AGE 0-3 + HALF.
    public static final Block CORN_CROP = register("corn_crop",
        CornCropBlock::new, cropProperties());

    // Sweet-berry-style bushes, survive on grass/dirt/farmland.
    public static final Block STRAWBERRY_BUSH = register("strawberry_bush",
        StrawberryBushBlock::new, bushProperties());
    public static final Block RASPBERRY_BUSH = register("raspberry_bush",
        RaspberryBushBlock::new, bushProperties());
    public static final Block BLACKBERRY_BUSH = register("blackberry_bush",
        BlackberryBushBlock::new, bushProperties());

    // Decorative forage plants placed by the forage task.
    public static final Block WILD_ONION = register("wild_onion",
        WildOnionBlock::new, wildPlantProperties());
    public static final Block WILD_RICE = register("wild_rice",
        WildRiceBlock::new, wildPlantProperties());
    public static final Block WILD_GRAPE_VINE = register("wild_grape_vine",
        WildGrapeVineBlock::new, wildPlantProperties());
    public static final Block WILD_HOPS = register("wild_hops",
        WildHopsBlock::new, wildPlantProperties());
    public static final Block CHANTERELLE = register("chanterelle",
        ChanterelleBlock::new, wildPlantProperties());
    public static final Block MINT = register("mint",
        MintBlock::new, wildPlantProperties());
    public static final Block SAGE = register("sage",
        SageBlock::new, wildPlantProperties());
    public static final Block THYME = register("thyme",
        ThymeBlock::new, wildPlantProperties());
    public static final Block RAMSONS = register("ramsons",
        RamsonsBlock::new, wildPlantProperties());
    public static final Block SORREL = register("sorrel",
        SorrelBlock::new, wildPlantProperties());

    // Trellis + its climbers.
    public static final Block TRELLIS = register("trellis",
        TrellisBlock::new, trellisProperties());
    public static final Block PEA_CROP = register("pea_crop",
        PeaCropBlock::new, peaProperties());
    public static final Block CUCUMBER_CROP = register("cucumber_crop",
        CucumberCropBlock::new, peaProperties());
    public static final Block GRAPE_CROP = register("grape_crop",
        GrapeCropBlock::new, peaProperties());
    public static final Block HOPS_CROP = register("hops_crop",
        HopsCropBlock::new, peaProperties());

    // Squash (Idiom E stem crop): stem grows a full-cube gourd, pumpkin-style.
    public static final Block SQUASH = register("squash",
        Block::new, squashProperties());
    public static final Block SQUASH_STEM = register("squash_stem",
        SquashStemBlock::new, stemProperties());
    public static final Block ATTACHED_SQUASH_STEM = register("attached_squash_stem",
        AttachedSquashStemBlock::new, attachedStemProperties());

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

    /** Squash gourd: full cube, pumpkin/melon-like (strength 1.0, wood sound). */
    private static BlockBehaviour.Properties squashProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE)
            .strength(1.0f)
            .sound(SoundType.WOOD)
            .pushReaction(PushReaction.DESTROY);
    }

    /** Squash stem: pumpkin-stem style (no collision, random ticks, hard-crop sound). */
    private static BlockBehaviour.Properties stemProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .randomTicks()
            .instabreak()
            .sound(SoundType.HARD_CROP)
            .pushReaction(PushReaction.DESTROY);
    }

    /** Attached squash stem: same as vanilla attached stem (no ticks, wood sound). */
    private static BlockBehaviour.Properties attachedStemProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollision()
            .instabreak()
            .sound(SoundType.WOOD)
            .pushReaction(PushReaction.DESTROY);
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
