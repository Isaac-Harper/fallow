package dev.isaac.fallow.item;

import dev.isaac.fallow.Fallow;
import dev.isaac.fallow.block.FallowBlocks;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The mod's items. Includes the season_clock (Phase 4) and the full crop-layer item roster
 * (Phase C1): food items, seed/planter items, and block items for crop blocks.
 */
public final class FallowItems {
    /** Vanilla's "Tools &amp; Utilities" creative tab - same one the vanilla clock lives in. */
    private static final ResourceKey<CreativeModeTab> TOOLS_AND_UTILITIES =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));

    /** Vanilla's "Food &amp; Drinks" tab - where sweet berries, carrot, etc. live. */
    private static final ResourceKey<CreativeModeTab> FOOD_AND_DRINKS =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("food_and_drinks"));

    /** Vanilla's "Natural Blocks" tab - where seeds and plants live. */
    private static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS =
        ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("natural_blocks"));

    // Season clock (Phase 4).
    public static final Item SEASON_CLOCK = register("season_clock", Item::new, new Item.Properties());

    // Food items.
    public static final Item TURNIP = register("turnip", Item::new,
        new Item.Properties().food(food(3, 0.6f)));
    public static final Item CABBAGE = register("cabbage", Item::new,
        new Item.Properties().food(food(3, 0.6f)));
    /**
     * Onion is carrot-style: the food item IS the planter (BlockItem pointing to onion_crop).
     * Right-clicking farmland with an onion plants the crop without a separate seeds item.
     */
    public static final Item ONION = register("onion",
        props -> new BlockItem(FallowBlocks.ONION_CROP, props),
        new Item.Properties().food(food(3, 0.6f)));
    public static final Item CHERRIES = register("cherries", Item::new,
        new Item.Properties().food(food(2, 0.4f)));
    /**
     * Strawberries are sweet-berry-style: the food item itself is the planter (BlockItem
     * pointing to fallow:strawberry_bush). Right-clicking farmland/grass/dirt with strawberries
     * plants the bush, exactly as sweet berries work.
     */
    public static final Item STRAWBERRIES = register("strawberries",
        props -> new BlockItem(FallowBlocks.STRAWBERRY_BUSH, props),
        new Item.Properties().food(food(2, 0.4f)));
    public static final Item PEAS = register("peas", Item::new,
        new Item.Properties().food(food(3, 0.8f)));

    // Phase C2/C3 food items.
    /** Plain food; planted via {@link #LEEK_SEEDS}. */
    public static final Item LEEK = register("leek", Item::new,
        new Item.Properties().food(food(3, 0.6f)));
    /**
     * Garlic is carrot-style: the food item IS the planter (BlockItem pointing to garlic_crop).
     * Right-clicking farmland with garlic plants the crop without a separate seeds item.
     */
    public static final Item GARLIC = register("garlic",
        props -> new BlockItem(FallowBlocks.GARLIC_CROP, props),
        new Item.Properties().food(food(3, 0.6f)));
    /** Plain food; planted via {@link #RADISH_SEEDS}. */
    public static final Item RADISH = register("radish", Item::new,
        new Item.Properties().food(food(2, 0.4f)));
    /** Plain food; planted via {@link #PARSNIP_SEEDS}. */
    public static final Item PARSNIP = register("parsnip", Item::new,
        new Item.Properties().food(food(3, 0.6f)));
    public static final Item PEPPER = register("pepper", Item::new,
        new Item.Properties().food(food(2, 0.4f)));
    public static final Item TOMATO = register("tomato", Item::new,
        new Item.Properties().food(food(2, 0.5f)));
    public static final Item CUCUMBER = register("cucumber", Item::new,
        new Item.Properties().food(food(2, 0.4f)));
    public static final Item CORN = register("corn", Item::new,
        new Item.Properties().food(food(3, 0.6f)));
    /** Grapes plant a grape climber on a trellis (consumed on trellis right-click). */
    public static final Item GRAPES = register("grapes", Item::new,
        new Item.Properties().food(food(2, 0.3f)));
    /** Raspberries are sweet-berry-style: the food item itself plants the raspberry bush. */
    public static final Item RASPBERRIES = register("raspberries",
        props -> new BlockItem(FallowBlocks.RASPBERRY_BUSH, props),
        new Item.Properties().food(food(2, 0.3f)));
    /** Blackberries are sweet-berry-style: the food item itself plants the blackberry bush. */
    public static final Item BLACKBERRIES = register("blackberries",
        props -> new BlockItem(FallowBlocks.BLACKBERRY_BUSH, props),
        new Item.Properties().food(food(2, 0.3f)));
    public static final Item PLUM = register("plum", Item::new,
        new Item.Properties().food(food(2, 0.4f)));
    public static final Item CHANTERELLE = register("chanterelle", Item::new,
        new Item.Properties().food(food(1, 0.4f)));
    public static final Item MINT = register("mint", Item::new,
        new Item.Properties().food(food(1, 0.2f)));
    public static final Item SAGE = register("sage", Item::new,
        new Item.Properties().food(food(1, 0.2f)));
    public static final Item THYME = register("thyme", Item::new,
        new Item.Properties().food(food(1, 0.2f)));
    public static final Item RAMSONS = register("ramsons", Item::new,
        new Item.Properties().food(food(1, 0.2f)));
    public static final Item SORREL = register("sorrel", Item::new,
        new Item.Properties().food(food(1, 0.2f)));

    // Non-food harvest items.
    public static final Item BARLEY = register("barley", Item::new, new Item.Properties());
    public static final Item RYE = register("rye", Item::new, new Item.Properties());
    public static final Item OATS = register("oats", Item::new, new Item.Properties());
    public static final Item RICE = register("rice", Item::new, new Item.Properties());
    /** Hop cones plant a hops climber on a trellis (consumed on trellis right-click). */
    public static final Item HOP_CONES = register("hop_cones", Item::new, new Item.Properties());

    // Seeds.
    public static final Item TURNIP_SEEDS = register("turnip_seeds",
        props -> new BlockItem(FallowBlocks.TURNIP_CROP, props), new Item.Properties());
    public static final Item CABBAGE_SEEDS = register("cabbage_seeds",
        props -> new BlockItem(FallowBlocks.CABBAGE_CROP, props), new Item.Properties());
    /** Plain item - consumed on trellis right-click to start a pea crop. */
    public static final Item PEA_SEEDS = register("pea_seeds", Item::new, new Item.Properties());
    public static final Item LEEK_SEEDS = register("leek_seeds",
        props -> new BlockItem(FallowBlocks.LEEK_CROP, props), new Item.Properties());
    public static final Item BARLEY_SEEDS = register("barley_seeds",
        props -> new BlockItem(FallowBlocks.BARLEY_CROP, props), new Item.Properties());
    public static final Item RYE_SEEDS = register("rye_seeds",
        props -> new BlockItem(FallowBlocks.RYE_CROP, props), new Item.Properties());
    public static final Item OAT_SEEDS = register("oat_seeds",
        props -> new BlockItem(FallowBlocks.OAT_CROP, props), new Item.Properties());
    public static final Item RADISH_SEEDS = register("radish_seeds",
        props -> new BlockItem(FallowBlocks.RADISH_CROP, props), new Item.Properties());
    public static final Item PARSNIP_SEEDS = register("parsnip_seeds",
        props -> new BlockItem(FallowBlocks.PARSNIP_CROP, props), new Item.Properties());
    public static final Item PEPPER_SEEDS = register("pepper_seeds",
        props -> new BlockItem(FallowBlocks.PEPPER_CROP, props), new Item.Properties());
    public static final Item FLAX_SEEDS = register("flax_seeds",
        props -> new BlockItem(FallowBlocks.FLAX_CROP, props), new Item.Properties());
    public static final Item TOMATO_SEEDS = register("tomato_seeds",
        props -> new BlockItem(FallowBlocks.TOMATO_CROP, props), new Item.Properties());
    public static final Item RICE_SEEDS = register("rice_seeds",
        props -> new BlockItem(FallowBlocks.RICE_CROP, props), new Item.Properties());
    public static final Item CORN_SEEDS = register("corn_seeds",
        props -> new BlockItem(FallowBlocks.CORN_CROP, props), new Item.Properties());
    public static final Item SQUASH_SEEDS = register("squash_seeds",
        props -> new BlockItem(FallowBlocks.SQUASH_STEM, props), new Item.Properties());
    /** Plain item - consumed on trellis right-click to start a cucumber crop. */
    public static final Item CUCUMBER_SEEDS = register("cucumber_seeds", Item::new,
        new Item.Properties());

    // Squash gourd block item (not edible).
    public static final Item SQUASH = register("squash",
        props -> new BlockItem(FallowBlocks.SQUASH, props), new Item.Properties());

    // Trellis block item.
    public static final Item TRELLIS = register("trellis",
        props -> new BlockItem(FallowBlocks.TRELLIS, props), new Item.Properties());

    private FallowItems() {
    }

    /** Resolve, build, and register an item, wiring its registry id into its properties. */
    private static Item register(String name,
            java.util.function.Function<Item.Properties, Item> factory,
            Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Fallow.MOD_ID, name));
        Item item = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static FoodProperties food(int nutrition, float saturation) {
        return new FoodProperties.Builder()
            .nutrition(nutrition)
            .saturationModifier(saturation)
            .build();
    }

    /** Touch the static fields (forcing registration) and place the items in their creative tabs. */
    public static void register() {
        // Tools &amp; Utilities - season clock.
        CreativeModeTabEvents.modifyOutputEvent(TOOLS_AND_UTILITIES)
            .register((FabricCreativeModeTabOutput output) -> output.accept(SEASON_CLOCK));

        // Food &amp; Drinks - food items after their nearest vanilla neighbours.
        CreativeModeTabEvents.modifyOutputEvent(FOOD_AND_DRINKS).register(output -> {
            output.insertAfter(Items.CARROT, TURNIP, CABBAGE, ONION,
                LEEK, GARLIC, RADISH, PARSNIP, PEPPER, TOMATO, CUCUMBER, CORN);
            output.insertAfter(Items.SWEET_BERRIES, STRAWBERRIES, CHERRIES,
                RASPBERRIES, BLACKBERRIES, GRAPES, PLUM);
            output.accept(PEAS);
            output.accept(CHANTERELLE);
            output.accept(MINT);
            output.accept(SAGE);
            output.accept(THYME);
            output.accept(RAMSONS);
            output.accept(SORREL);
        });

        // Natural Blocks - seeds, harvest goods, squash, and trellis near wheat seeds.
        CreativeModeTabEvents.modifyOutputEvent(NATURAL_BLOCKS).register(output -> {
            output.insertAfter(Items.WHEAT_SEEDS, TURNIP_SEEDS, CABBAGE_SEEDS, PEA_SEEDS,
                LEEK_SEEDS, BARLEY_SEEDS, RYE_SEEDS, OAT_SEEDS, RADISH_SEEDS, PARSNIP_SEEDS,
                PEPPER_SEEDS, FLAX_SEEDS, TOMATO_SEEDS, CUCUMBER_SEEDS, RICE_SEEDS, CORN_SEEDS,
                SQUASH_SEEDS);
            output.accept(BARLEY);
            output.accept(RYE);
            output.accept(OATS);
            output.accept(RICE);
            output.accept(HOP_CONES);
            output.accept(SQUASH);
            output.accept(TRELLIS);
        });
    }
}
