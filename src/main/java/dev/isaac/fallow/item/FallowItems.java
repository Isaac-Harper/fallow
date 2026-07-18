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

    // Seeds.
    public static final Item TURNIP_SEEDS = register("turnip_seeds",
        props -> new BlockItem(FallowBlocks.TURNIP_CROP, props), new Item.Properties());
    public static final Item CABBAGE_SEEDS = register("cabbage_seeds",
        props -> new BlockItem(FallowBlocks.CABBAGE_CROP, props), new Item.Properties());
    /** Plain item - consumed on trellis right-click to start a pea crop. */
    public static final Item PEA_SEEDS = register("pea_seeds", Item::new, new Item.Properties());

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
            output.insertAfter(Items.CARROT, TURNIP, CABBAGE, ONION);
            output.insertAfter(Items.SWEET_BERRIES, STRAWBERRIES, CHERRIES);
            output.accept(PEAS);
        });

        // Natural Blocks - seeds and trellis near wheat seeds.
        CreativeModeTabEvents.modifyOutputEvent(NATURAL_BLOCKS).register(output -> {
            output.insertAfter(Items.WHEAT_SEEDS, TURNIP_SEEDS, CABBAGE_SEEDS, PEA_SEEDS);
            output.accept(TRELLIS);
        });
    }
}
